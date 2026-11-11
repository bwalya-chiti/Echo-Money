package com.example.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidPasswordException(message: String = "Incorrect backup password") : Exception(message)
class CorruptedBackupException(message: String = "Corrupted or tampered backup file") : Exception(message)

class BackupManager(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(BackupManifest::class.java)
    private val integrityAdapter = moshi.adapter(BackupIntegrity::class.java)
    private val summaryAdapter = moshi.adapter(BackupSummaryInfo::class.java)

    private val backupDir: File by lazy {
        File(context.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Creates an encrypted .echomoney backup.
     * Can write to internal backup storage or directly to a provided OutputStream (e.g. SAF Uri).
     */
    suspend fun createBackup(
        password: String,
        isAuto: Boolean = false,
        targetOutputStream: OutputStream? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (password.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Password must be at least 4 characters"))
            }

            val db = AppDatabase.getDatabase(context)
            AppDatabase.checkpoint(db)

            // Gather metadata & summary
            val transactions = db.transactionDao().getAllTransactions()
            val accounts = db.accountDao().getAllAccounts()
            val categories = db.categoryDao().getAllCategories()
            val logs = db.smsLogDao().getLogByHash("dummy") // Check table accessible

            val airtel = accounts.find { it.provider == "AIRTEL" }?.lastReportedBalance ?: 0.0
            val mtn = accounts.find { it.provider == "MTN" }?.lastReportedBalance ?: 0.0
            val cash = accounts.find { it.provider == "CASH" }?.lastReportedBalance ?: 0.0

            val dateStart = transactions.minOfOrNull { it.transactionDate } ?: System.currentTimeMillis()
            val dateEnd = transactions.maxOfOrNull { it.transactionDate } ?: System.currentTimeMillis()

            val summaryInfo = BackupSummaryInfo(
                totalTransactions = transactions.size,
                totalAccounts = accounts.size,
                totalCategories = categories.size,
                totalSmsLogs = 0,
                dateRangeStart = dateStart,
                dateRangeEnd = dateEnd,
                airtelBalance = airtel,
                mtnBalance = mtn,
                cashBalance = cash,
                appVersion = "1.0",
                deviceModel = android.os.Build.MODEL ?: "Android"
            )

            // Checkpoint database so all WAL frames are committed to echo_money.db
            AppDatabase.checkpoint(db)

            // Read database file
            val dbFile = context.getDatabasePath("echo_money.db")
            if (!dbFile.exists()) {
                return@withContext Result.failure(BackupException("Database file not found"))
            }

            val rawDbBytes = dbFile.readBytes()
            val rawDbSha256 = CryptoManager.calculateSha256(rawDbBytes)

            // Crypto parameters
            val salt = CryptoManager.generateSalt()
            val iv = CryptoManager.generateIv()
            val encryptedPayload = CryptoManager.encrypt(rawDbBytes, password, salt, iv)
            val payloadSha256 = CryptoManager.calculateSha256(encryptedPayload)

            val recordCounts = mapOf(
                "transactions" to transactions.size,
                "accounts" to accounts.size,
                "categories" to categories.size
            )

            val manifest = BackupManifest(
                app = "ECHO MONEY",
                formatVersion = 1,
                dbSchemaVersion = 3,
                timestamp = System.currentTimeMillis(),
                saltBase64 = CryptoManager.toBase64(salt),
                ivBase64 = CryptoManager.toBase64(iv),
                recordCounts = recordCounts,
                summary = summaryInfo
            )

            val manifestJson = manifestAdapter.toJson(manifest)
            val manifestSha256 = CryptoManager.calculateSha256(manifestJson.toByteArray(Charsets.UTF_8))

            val integrity = BackupIntegrity(
                rawDbSha256 = rawDbSha256,
                payloadSha256 = payloadSha256,
                manifestSha256 = manifestSha256
            )
            val integrityJson = integrityAdapter.toJson(integrity)
            val summaryJson = summaryAdapter.toJson(summaryInfo)

            // Package into ZIP
            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (isAuto) "echo_money_auto_" else "echo_money_backup_"
            val backupFile = File(backupDir, "$prefix$timestampStr.echomoney")

            val outStream = targetOutputStream ?: FileOutputStream(backupFile)
            ZipOutputStream(outStream).use { zos ->
                // 1. manifest.json
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. metadata.json
                zos.putNextEntry(ZipEntry("metadata.json"))
                zos.write(summaryJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3. integrity.json
                zos.putNextEntry(ZipEntry("integrity.json"))
                zos.write(integrityJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 4. payload.enc
                zos.putNextEntry(ZipEntry("payload.enc"))
                zos.write(encryptedPayload)
                zos.closeEntry()
            }

            if (isAuto) {
                rotateAutoBackups(maxKeep = 5)
            }

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(BackupException("Failed to create backup: ${e.message}", e))
        }
    }

    /**
     * Inspects a backup file and attempts password verification without altering active data.
     */
    suspend fun inspectBackup(
        inputStream: InputStream,
        password: String
    ): Result<BackupInspection> = withContext(Dispatchers.IO) {
        try {
            val entries = readZipEntries(inputStream)

            val manifestBytes = entries["manifest.json"] ?: return@withContext Result.failure(CorruptedBackupException("Missing manifest.json"))
            val integrityBytes = entries["integrity.json"] ?: return@withContext Result.failure(CorruptedBackupException("Missing integrity.json"))
            val payloadBytes = entries["payload.enc"] ?: return@withContext Result.failure(CorruptedBackupException("Missing payload.enc"))

            val manifest = manifestAdapter.fromJson(String(manifestBytes, Charsets.UTF_8))
                ?: return@withContext Result.failure(CorruptedBackupException("Invalid manifest format"))
            val integrity = integrityAdapter.fromJson(String(integrityBytes, Charsets.UTF_8))
                ?: return@withContext Result.failure(CorruptedBackupException("Invalid integrity format"))

            // Verify payload checksum
            val currentPayloadSha = CryptoManager.calculateSha256(payloadBytes)
            if (currentPayloadSha != integrity.payloadSha256) {
                return@withContext Result.failure(CorruptedBackupException("Payload integrity hash mismatch"))
            }

            // Attempt decryption to test password
            val salt = CryptoManager.fromBase64(manifest.saltBase64)
            val iv = CryptoManager.fromBase64(manifest.ivBase64)

            val decryptedDbBytes: ByteArray
            try {
                decryptedDbBytes = CryptoManager.decrypt(payloadBytes, password, salt, iv, manifest.kdfIterations)
            } catch (e: Exception) {
                return@withContext Result.failure(InvalidPasswordException("Authentication failed: Wrong password or tampered payload"))
            }

            val decryptedSha = CryptoManager.calculateSha256(decryptedDbBytes)
            if (decryptedSha != integrity.rawDbSha256) {
                return@withContext Result.failure(CorruptedBackupException("Decrypted database checksum mismatch"))
            }

            Result.success(
                BackupInspection(
                    manifest = manifest,
                    summary = manifest.summary,
                    fileSizeBytes = payloadBytes.size.toLong() + manifestBytes.size + integrityBytes.size,
                    fileName = "Backup (${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(manifest.timestamp))})",
                    isPasswordValid = true,
                    integrityValid = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores backup using either FULL_REPLACE or SELECTIVE_MERGE.
     */
    suspend fun restoreBackup(
        inputStream: InputStream,
        password: String,
        mode: RestoreMode
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        val tempRestoreFile = File(context.cacheDir, "echo_money_temp_restore.db")
        val safetySnapshotFile = File(context.filesDir, "echo_money_safety_snapshot.db")

        try {
            val entries = readZipEntries(inputStream)
            val manifestBytes = entries["manifest.json"] ?: return@withContext Result.failure(CorruptedBackupException("Missing manifest.json"))
            val integrityBytes = entries["integrity.json"] ?: return@withContext Result.failure(CorruptedBackupException("Missing integrity.json"))
            val payloadBytes = entries["payload.enc"] ?: return@withContext Result.failure(CorruptedBackupException("Missing payload.enc"))

            val manifest = manifestAdapter.fromJson(String(manifestBytes, Charsets.UTF_8))
                ?: return@withContext Result.failure(CorruptedBackupException("Invalid manifest"))
            val integrity = integrityAdapter.fromJson(String(integrityBytes, Charsets.UTF_8))
                ?: return@withContext Result.failure(CorruptedBackupException("Invalid integrity"))

            // Verify payload SHA256
            if (CryptoManager.calculateSha256(payloadBytes) != integrity.payloadSha256) {
                return@withContext Result.failure(CorruptedBackupException("Backup payload corrupted"))
            }

            // Decrypt
            val salt = CryptoManager.fromBase64(manifest.saltBase64)
            val iv = CryptoManager.fromBase64(manifest.ivBase64)

            val decryptedDbBytes = try {
                CryptoManager.decrypt(payloadBytes, password, salt, iv, manifest.kdfIterations)
            } catch (e: Exception) {
                return@withContext Result.failure(InvalidPasswordException("Wrong password or corrupted payload"))
            }

            if (CryptoManager.calculateSha256(decryptedDbBytes) != integrity.rawDbSha256) {
                return@withContext Result.failure(CorruptedBackupException("Decrypted database hash check failed"))
            }

            // Write to temporary file for SQLite validation
            if (tempRestoreFile.exists()) tempRestoreFile.delete()
            tempRestoreFile.writeBytes(decryptedDbBytes)

            // Validate SQLite integrity
            validateSqliteFile(tempRestoreFile)

            if (mode == RestoreMode.FULL_REPLACE) {
                // 1. Safety snapshot of current database
                val currentDbFile = context.getDatabasePath("echo_money.db")
                AppDatabase.closeDatabase()

                if (currentDbFile.exists()) {
                    currentDbFile.copyTo(safetySnapshotFile, overwrite = true)
                }

                try {
                    // Replace main DB file & delete WAL/SHM
                    val walFile = File(currentDbFile.parentFile, "echo_money.db-wal")
                    val shmFile = File(currentDbFile.parentFile, "echo_money.db-shm")
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()

                    tempRestoreFile.copyTo(currentDbFile, overwrite = true)

                    // Reopen database and verify
                    val reopenedDb = AppDatabase.getDatabase(context)
                    val restoredTransactions = reopenedDb.transactionDao().getAllTransactions()
                    val restoredAccounts = reopenedDb.accountDao().getAllAccounts()

                    // Clean up safety snapshot on success
                    if (safetySnapshotFile.exists()) safetySnapshotFile.delete()

                    Result.success(
                        RestoreResult(
                            success = true,
                            mode = RestoreMode.FULL_REPLACE,
                            transactionsImported = restoredTransactions.size,
                            accountsUpdated = restoredAccounts.size
                        )
                    )
                } catch (e: Exception) {
                    // Automatic Rollback to safety snapshot
                    if (safetySnapshotFile.exists()) {
                        AppDatabase.closeDatabase()
                        val currentDb = context.getDatabasePath("echo_money.db")
                        safetySnapshotFile.copyTo(currentDb, overwrite = true)
                        AppDatabase.getDatabase(context)
                    }
                    Result.failure(BackupException("Restore failed, rolled back to safety snapshot: ${e.message}", e))
                }
            } else {
                // SELECTIVE_MERGE
                val currentDb = AppDatabase.getDatabase(context)
                val mergeResult = performSelectiveMerge(tempRestoreFile, currentDb)
                Result.success(mergeResult)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempRestoreFile.exists()) tempRestoreFile.delete()
        }
    }

    private fun validateSqliteFile(file: File) {
        val sqliteDb = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val cursor = sqliteDb.rawQuery("PRAGMA integrity_check", null)
            cursor.use {
                if (it.moveToFirst()) {
                    val result = it.getString(0)
                    if (!result.equals("ok", ignoreCase = true)) {
                        throw CorruptedBackupException("SQLite integrity check failed: $result")
                    }
                } else {
                    throw CorruptedBackupException("Empty SQLite integrity check result")
                }
            }

            // Verify expected tables exist
            val tablesCursor = sqliteDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            val tableNames = mutableSetOf<String>()
            tablesCursor.use {
                while (it.moveToNext()) {
                    tableNames.add(it.getString(0))
                }
            }
            if (!tableNames.contains("transactions")) {
                throw CorruptedBackupException("Backup database does not contain required tables")
            }
        } finally {
            sqliteDb.close()
        }
    }

    private suspend fun performSelectiveMerge(tempDbFile: File, currentDb: AppDatabase): RestoreResult {
        val sourceDb = SQLiteDatabase.openDatabase(tempDbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        var importedTxCount = 0
        var skippedTxCount = 0
        var importedCategories = 0

        try {
            // Read categories from source if table exists
            val hasCategories = sourceDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='categories'", null).use {
                it.moveToFirst()
            }

            if (hasCategories) {
                val catCursor = sourceDb.rawQuery("SELECT name, categoryType, isDefault, icon FROM categories", null)
                val sourceCategories = mutableListOf<Category>()
                catCursor.use {
                    while (it.moveToNext()) {
                        sourceCategories.add(
                            Category(
                                name = it.getString(0),
                                categoryType = it.getString(1),
                                isDefault = it.getInt(2) != 0,
                                icon = it.getString(3) ?: "category"
                            )
                        )
                    }
                }

                val existingCategories = currentDb.categoryDao().getAllCategories().map { it.name }.toSet()
                for (cat in sourceCategories) {
                    if (!existingCategories.contains(cat.name)) {
                        currentDb.categoryDao().insertCategory(cat)
                        importedCategories++
                    }
                }
            }

            // Read transactions from source
            val txCursor = sourceDb.rawQuery("SELECT * FROM transactions ORDER BY transactionDate ASC", null)
            val sourceTransactions = mutableListOf<Transaction>()
            txCursor.use { cursor ->
                val colAccId = cursor.getColumnIndex("accountId")
                val colProv = cursor.getColumnIndex("provider")
                val colRef = cursor.getColumnIndex("transactionReference")
                val colType = cursor.getColumnIndex("transactionType")
                val colDir = cursor.getColumnIndex("direction")
                val colStatus = cursor.getColumnIndex("status")
                val colCatId = cursor.getColumnIndex("categoryId")
                val colCatName = cursor.getColumnIndex("categoryName")
                val colAmtMinor = cursor.getColumnIndex("amountMinor")
                val colAmt = cursor.getColumnIndex("amount")
                val colCur = cursor.getColumnIndex("currency")
                val colFeeMinor = cursor.getColumnIndex("feeMinor")
                val colFee = cursor.getColumnIndex("fee")
                val colSender = cursor.getColumnIndex("sender")
                val colRecipient = cursor.getColumnIndex("recipient")
                val colMerchant = cursor.getColumnIndex("merchantOrAgent")
                val colDesc = cursor.getColumnIndex("description")
                val colOrigRef = cursor.getColumnIndex("originalTransactionReference")
                val colDate = cursor.getColumnIndex("transactionDate")
                val colDetected = cursor.getColumnIndex("detectedAt")
                val colBalMinor = cursor.getColumnIndex("balanceAfterMinor")
                val colBal = cursor.getColumnIndex("balanceAfter")
                val colRaw = cursor.getColumnIndex("rawSms")
                val colConf = cursor.getColumnIndex("classificationConfidence")
                val colInternal = cursor.getColumnIndex("isInternalTransfer")
                val colGroup = cursor.getColumnIndex("transferGroupId")
                val colReview = cursor.getColumnIndex("needsReview")

                while (cursor.moveToNext()) {
                    val amountMinor = when {
                        colAmtMinor != -1 && !cursor.isNull(colAmtMinor) -> cursor.getLong(colAmtMinor)
                        colAmt != -1 && !cursor.isNull(colAmt) -> com.example.data.model.Money.fromZmw(cursor.getDouble(colAmt))
                        else -> 0L
                    }
                    val feeMinor = when {
                        colFeeMinor != -1 && !cursor.isNull(colFeeMinor) -> cursor.getLong(colFeeMinor)
                        colFee != -1 && !cursor.isNull(colFee) -> com.example.data.model.Money.fromZmw(cursor.getDouble(colFee))
                        else -> 0L
                    }
                    val balMinor = when {
                        colBalMinor != -1 && !cursor.isNull(colBalMinor) -> cursor.getLong(colBalMinor)
                        colBal != -1 && !cursor.isNull(colBal) -> com.example.data.model.Money.fromZmw(cursor.getDouble(colBal))
                        else -> null
                    }

                    sourceTransactions.add(
                        Transaction(
                            accountId = if (colAccId != -1) cursor.getLong(colAccId) else 0L,
                            provider = if (colProv != -1) cursor.getString(colProv) ?: "UNKNOWN" else "UNKNOWN",
                            transactionReference = if (colRef != -1 && !cursor.isNull(colRef)) cursor.getString(colRef) else null,
                            transactionType = if (colType != -1) cursor.getString(colType) ?: "OUTGOING_TRANSFER" else "OUTGOING_TRANSFER",
                            direction = if (colDir != -1) cursor.getString(colDir) ?: "DEBIT" else "DEBIT",
                            status = if (colStatus != -1) cursor.getString(colStatus) ?: "SUCCESSFUL" else "SUCCESSFUL",
                            categoryId = if (colCatId != -1) cursor.getLong(colCatId) else 0L,
                            categoryName = if (colCatName != -1) cursor.getString(colCatName) ?: "General" else "General",
                            amountMinor = amountMinor,
                            currency = if (colCur != -1 && !cursor.isNull(colCur)) cursor.getString(colCur) else "ZMW",
                            feeMinor = feeMinor,
                            sender = if (colSender != -1 && !cursor.isNull(colSender)) cursor.getString(colSender) else null,
                            recipient = if (colRecipient != -1 && !cursor.isNull(colRecipient)) cursor.getString(colRecipient) else null,
                            merchantOrAgent = if (colMerchant != -1 && !cursor.isNull(colMerchant)) cursor.getString(colMerchant) else null,
                            description = if (colDesc != -1 && !cursor.isNull(colDesc)) cursor.getString(colDesc) else null,
                            originalTransactionReference = if (colOrigRef != -1 && !cursor.isNull(colOrigRef)) cursor.getString(colOrigRef) else null,
                            transactionDate = if (colDate != -1) cursor.getLong(colDate) else System.currentTimeMillis(),
                            detectedAt = if (colDetected != -1) cursor.getLong(colDetected) else System.currentTimeMillis(),
                            balanceAfterMinor = balMinor,
                            rawSms = if (colRaw != -1 && !cursor.isNull(colRaw)) cursor.getString(colRaw) else null,
                            classificationConfidence = if (colConf != -1) cursor.getFloat(colConf) else 1.0f,
                            isInternalTransfer = if (colInternal != -1) cursor.getInt(colInternal) != 0 else false,
                            transferGroupId = if (colGroup != -1 && !cursor.isNull(colGroup)) cursor.getString(colGroup) else null,
                            needsReview = if (colReview != -1) cursor.getInt(colReview) != 0 else false
                        )
                    )
                }
            }

            for (tx in sourceTransactions) {
                // Deduplication check: Reference match
                if (!tx.transactionReference.isNullOrBlank()) {
                    val existing = currentDb.transactionDao().getTransactionByReference(tx.provider, tx.transactionReference)
                    if (existing != null) {
                        skippedTxCount++
                        continue
                    }
                }

                // Deduplication check: Near duplicate within 30s with same provider & amountMinor
                val potentialDup = currentDb.transactionDao().findPotentialDuplicate(
                    provider = tx.provider,
                    amountMinor = tx.amountMinor,
                    startTime = tx.transactionDate - 30_000,
                    endTime = tx.transactionDate + 30_000
                )
                if (potentialDup != null && potentialDup.transactionType == tx.transactionType) {
                    skippedTxCount++
                    continue
                }

                // Safe to insert & post to double-entry journal
                val insertedId = currentDb.transactionDao().insertTransaction(tx)
                com.example.data.accounting.AccountingEngine.postTransaction(currentDb, tx.copy(id = insertedId))
                importedTxCount++
            }

            return RestoreResult(
                success = true,
                mode = RestoreMode.SELECTIVE_MERGE,
                transactionsImported = importedTxCount,
                transactionsSkipped = skippedTxCount,
                categoriesImported = importedCategories
            )
        } finally {
            sourceDb.close()
        }
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (zis.read(buffer).also { read = it } != -1) {
                        baos.write(buffer, 0, read)
                    }
                    entries[entry.name] = baos.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    fun listLocalBackups(): List<LocalBackupFile> {
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { file -> file.name.endsWith(".echomoney") }
            ?.map {
                LocalBackupFile(
                    fileName = it.name,
                    filePath = it.absolutePath,
                    sizeBytes = it.length(),
                    timestamp = it.lastModified(),
                    isAuto = it.name.startsWith("echo_money_auto_")
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun deleteLocalBackup(filePath: String): Boolean {
        val file = File(filePath)
        return if (file.exists() && file.parentFile == backupDir) {
            file.delete()
        } else false
    }

    private fun rotateAutoBackups(maxKeep: Int = 5) {
        val autoBackups = backupDir.listFiles { f -> f.name.startsWith("echo_money_auto_") && f.name.endsWith(".echomoney") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (autoBackups.size > maxKeep) {
            autoBackups.drop(maxKeep).forEach { oldFile ->
                oldFile.delete()
            }
        }
    }
}

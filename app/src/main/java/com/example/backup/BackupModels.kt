package com.example.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupManifest(
    val app: String = "ECHO MONEY",
    val formatVersion: Int = 1,
    val dbSchemaVersion: Int = 3,
    val timestamp: Long = System.currentTimeMillis(),
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val kdfIterations: Int = 100000,
    val saltBase64: String,
    val ivBase64: String,
    val recordCounts: Map<String, Int>,
    val summary: BackupSummaryInfo
)

@JsonClass(generateAdapter = true)
data class BackupSummaryInfo(
    val totalTransactions: Int,
    val totalAccounts: Int,
    val totalCategories: Int,
    val totalSmsLogs: Int,
    val dateRangeStart: Long,
    val dateRangeEnd: Long,
    val airtelBalance: Double,
    val mtnBalance: Double,
    val cashBalance: Double,
    val appVersion: String = "1.0",
    val deviceModel: String = "Android"
)

@JsonClass(generateAdapter = true)
data class BackupIntegrity(
    val rawDbSha256: String,
    val payloadSha256: String,
    val manifestSha256: String
)

enum class RestoreMode {
    FULL_REPLACE,
    SELECTIVE_MERGE
}

data class RestoreResult(
    val success: Boolean,
    val mode: RestoreMode,
    val transactionsImported: Int = 0,
    val transactionsSkipped: Int = 0,
    val accountsUpdated: Int = 0,
    val categoriesImported: Int = 0,
    val errorMessage: String? = null
)

data class BackupInspection(
    val manifest: BackupManifest,
    val summary: BackupSummaryInfo,
    val fileSizeBytes: Long,
    val fileName: String,
    val isPasswordValid: Boolean,
    val integrityValid: Boolean
)

data class LocalBackupFile(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val timestamp: Long,
    val isAuto: Boolean
)

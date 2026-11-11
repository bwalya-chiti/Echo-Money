package com.example.backup

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    const val DEFAULT_ITERATIONS = 100000
    private const val GCM_TAG_LENGTH_BITS = 128
    const val SALT_LENGTH_BYTES = 32
    const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun deriveKey(password: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKeySpec {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        keySpec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts byte array using AES-256-GCM with derived key from password and salt.
     */
    fun encrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(data)
    }

    /**
     * Decrypts byte array using AES-256-GCM.
     * Throws an exception (e.g. AEADBadTagException) if the password is wrong or ciphertext has been tampered with.
     */
    @Throws(Exception::class)
    fun decrypt(encryptedData: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(encryptedData)
    }

    /**
     * Calculates SHA-256 hex digest of a byte array.
     */
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun fromBase64(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP)
}

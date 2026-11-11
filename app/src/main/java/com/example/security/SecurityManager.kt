package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class SecurityManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("echo_security_prefs", Context.MODE_PRIVATE)

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    init {
        // If PIN is enabled, initial state is locked
        if (isPinEnabled()) {
            _isLocked.value = true
        }
    }

    fun isPinEnabled(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false)
    }

    fun setPin(pin: String) {
        val salt = "echo_money_salt_2026"
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putBoolean(KEY_PIN_ENABLED, true)
            .putString(KEY_PIN_HASH, hash)
            .apply()
        _isLocked.value = false
    }

    fun disablePin() {
        prefs.edit()
            .putBoolean(KEY_PIN_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .apply()
        _isLocked.value = false
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isPinEnabled()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    fun unlockWithBiometrics(): Boolean {
        _isLocked.value = false
        return true
    }

    fun unlock(pin: String): Boolean {
        if (!isPinEnabled()) {
            _isLocked.value = false
            return true
        }
        val salt = "echo_money_salt_2026"
        val hash = hashPin(pin, salt)
        val savedHash = prefs.getString(KEY_PIN_HASH, "")
        if (hash == savedHash) {
            _isLocked.value = false
            return true
        }
        return false
    }

    fun lock() {
        if (isPinEnabled()) {
            _isLocked.value = true
        }
    }

    private fun hashPin(pin: String, salt: String): String {
        val input = "$pin:$salt"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isDashboardGated(): Boolean {
        return prefs.getBoolean(KEY_DASHBOARD_GATED, false)
    }

    fun setDashboardGated(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DASHBOARD_GATED, enabled).apply()
    }

    companion object {
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DASHBOARD_GATED = "dashboard_gated"
    }
}

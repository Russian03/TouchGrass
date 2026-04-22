package com.tuapp.touchgrass.storage

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "touchgrass_prefs"
    }

    // ── Reels siempre ocultos ──────────────────────────────────────
    var reelsAlwaysHidden: Boolean
        get() = prefs.getBoolean("reels_always_hidden", false)
        set(value) = prefs.edit().putBoolean("reels_always_hidden", value).apply()

    // ── Reels always hidden pendiente para mañana ─────────────────
    var pendingReelsAlwaysHidden: Boolean
        get() = prefs.getBoolean("pending_reels_always_hidden", false)
        set(value) = prefs.edit().putBoolean("pending_reels_always_hidden", value).apply()

    var pendingReelsAlwaysHiddenSet: Boolean
        get() = prefs.getBoolean("pending_reels_always_hidden_set", false)
        set(value) = prefs.edit().putBoolean("pending_reels_always_hidden_set", value).apply()

    val isReelsLimitReached: Boolean
        get() = todayRemainingSeconds <= 0

    // ── Onboarding ─────────────────────────────────────────────────
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    // ── Límite diario configurado (en minutos) ─────────────────────
    var dailyInstagramLimitMinutes: Int
        get() = prefs.getInt("daily_instagram_limit_minutes", 60)
        set(value) = prefs.edit().putInt("daily_instagram_limit_minutes", value).apply()

    // ── Segundos consumidos hoy ────────────────────────────────────
    var todayUsedSeconds: Long
        get() = prefs.getLong("today_used_seconds", 0L)
        set(value) = prefs.edit().putLong("today_used_seconds", value).apply()

    // ── Fecha del último uso (formato "yyyy-MM-dd") ────────────────
    var lastUsageDate: String
        get() = prefs.getString("last_usage_date", "") ?: ""
        set(value) = prefs.edit().putString("last_usage_date", value).apply()

    // ── Timestamp de cuando empezó la sesión de Reels ─────────────
    var reelsSessionStartMs: Long
        get() = prefs.getLong("reels_session_start_ms", 0L)
        set(value) = prefs.edit().putLong("reels_session_start_ms", value).apply()

    // ── Segundos restantes hoy ─────────────────────────────────────
    val todayRemainingSeconds: Long
        get() {
            val totalSeconds = dailyInstagramLimitMinutes * 60L
            return maxOf(0L, totalSeconds - todayUsedSeconds)
        }

    // ── Resetear uso si es un día nuevo ───────────────────────────
    fun resetIfNewDay() {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val today = todayString()

        if (hour >= 8 && lastResetDate != today) {
            if (pendingLimitMinutes != -1) {
                dailyInstagramLimitMinutes = pendingLimitMinutes
                pendingLimitMinutes = -1
            }
            if (pendingReelsAlwaysHiddenSet) {
                reelsAlwaysHidden = pendingReelsAlwaysHidden
                pendingReelsAlwaysHiddenSet = false
            }
            todayUsedSeconds = 0L
            reelsSessionStartMs = 0L
            lastResetDate = today
            lastUsageDate = today
        }
    }

    // ── Límite pendiente para mañana ───────────────────────────────
    var pendingLimitMinutes: Int
        get() = prefs.getInt("pending_limit_minutes", -1)
        set(value) = prefs.edit().putInt("pending_limit_minutes", value).apply()

    // ── Hora del último reset (para saber si ya se hizo el de hoy) ─
    var lastResetDate: String
        get() = prefs.getString("last_reset_date", "") ?: ""
        set(value) = prefs.edit().putString("last_reset_date", value).apply()

    private fun todayString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
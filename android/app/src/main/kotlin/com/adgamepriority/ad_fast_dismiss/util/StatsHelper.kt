package com.adgamepriority.ad_fast_dismiss.util

import android.content.Context
import android.content.SharedPreferences

/**
 * StatsHelper.kt
 * ==============
 * Tracks and persists ad dismissal statistics.
 * All data stored in SharedPreferences — lightweight, no DB needed.
 *
 * Data tracked:
 * - Total ads dismissed (lifetime)
 * - Total time saved in seconds (lifetime)
 * - Ads dismissed today
 * - Time saved today (seconds)
 * - Last reset date (for daily tracking)
 */
class StatsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("afd_stats", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOTAL_ADS       = "total_ads"
        private const val KEY_TOTAL_SECS      = "total_secs"
        private const val KEY_TODAY_ADS       = "today_ads"
        private const val KEY_TODAY_SECS      = "today_secs"
        private const val KEY_LAST_DATE       = "last_date"
        private const val KEY_PHASE_BREAKDOWN = "phase_breakdown" // e.g. "1:5,2:3,3:12"
    }

    /**
     * Records a successful ad dismissal.
     * @param timeSavedSeconds How many seconds were saved vs waiting for full ad
     * @param phase Which cascade phase succeeded (1-7)
     */
    fun recordDismissal(timeSavedSeconds: Int, phase: Int) {
        // FIX BUG-2: Snapshot all current values BEFORE writing.
        // Old code called getTotalAds()/getTodayAds() inside the editor block which
        // triggered checkDayReset() mid-write — a read-then-write race that could
        // corrupt today's counters across a midnight boundary.
        checkDayReset()
        val totalAds   = prefs.getInt(KEY_TOTAL_ADS,  0)
        val totalSecs  = prefs.getInt(KEY_TOTAL_SECS, 0)
        val todayAds   = prefs.getInt(KEY_TODAY_ADS,  0)
        val todaySecs  = prefs.getInt(KEY_TODAY_SECS, 0)
        prefs.edit()
            .putInt(KEY_TOTAL_ADS,  totalAds  + 1)
            .putInt(KEY_TOTAL_SECS, totalSecs + timeSavedSeconds)
            .putInt(KEY_TODAY_ADS,  todayAds  + 1)
            .putInt(KEY_TODAY_SECS, todaySecs + timeSavedSeconds)
            .apply()
        incrementPhaseBreakdown(phase)
    }

    fun getTotalAds(): Int = prefs.getInt(KEY_TOTAL_ADS, 0)
    fun getTotalSecsSaved(): Int = prefs.getInt(KEY_TOTAL_SECS, 0)
    fun getTodayAds(): Int {
        checkDayReset()
        return prefs.getInt(KEY_TODAY_ADS, 0)
    }
    fun getTodaySecsSaved(): Int {
        checkDayReset()
        return prefs.getInt(KEY_TODAY_SECS, 0)
    }

    /**
     * Returns phase breakdown map for stats display.
     * e.g. {1 to 5, 2 to 3, 4 to 12} means phase 4 was most effective.
     */
    fun getPhaseBreakdown(): Map<Int, Int> {
        val raw = prefs.getString(KEY_PHASE_BREAKDOWN, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                try { parts[0].toInt() to parts[1].toInt() } catch (e: Exception) { null }
            } else null
        }.toMap()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun checkDayReset() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        if (lastDate != today) {
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_TODAY_ADS, 0)
                .putInt(KEY_TODAY_SECS, 0)
                .apply()
        }
    }

    private fun incrementPhaseBreakdown(phase: Int) {
        val breakdown = getPhaseBreakdown().toMutableMap()
        breakdown[phase] = (breakdown[phase] ?: 0) + 1
        val serialized = breakdown.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_PHASE_BREAKDOWN, serialized).apply()
    }
}

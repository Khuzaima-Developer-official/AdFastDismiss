package com.adgamepriority.ad_fast_dismiss.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * CoordLearner.kt
 * ===============
 * Adaptive coordinate memory system.
 *
 * When a coord tap succeeds at dismissing an ad, we save that exact position.
 * Next time the same game+SDK combo appears, we use the LEARNED coord directly
 * instead of going through the database — faster dismiss every session.
 *
 * Key format: "pkg_sdk" e.g. "com.example.game_unity"
 * Value: "xRatio,yRatio,successCount"
 *
 * After 3 successes → HIGH CONFIDENCE: skip database, use learned coord directly.
 */
class CoordLearner(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("coord_learner", Context.MODE_PRIVATE)

    companion object {
        private const val HIGH_CONFIDENCE_THRESHOLD = 3
    }

    data class LearnedCoord(
        val xRatio: Float,
        val yRatio: Float,
        val successCount: Int
    ) {
        val isHighConfidence: Boolean get() = successCount >= HIGH_CONFIDENCE_THRESHOLD
    }

    /**
     * Records a successful coord tap for a game+SDK combination.
     * @param gamePkg Package name of the game
     * @param adClass Full class name of the ad activity
     * @param xRatio X position as a ratio (0.0–1.0)
     * @param yRatio Y position as a ratio (0.0–1.0)
     */
    fun recordSuccess(gamePkg: String, adClass: String, xRatio: Float, yRatio: Float) {
        val key = buildKey(gamePkg, adClass)
        val existing = get(gamePkg, adClass)
        val newCount = (existing?.successCount ?: 0) + 1
        prefs.edit()
            .putString(key, "$xRatio,$yRatio,$newCount")
            .apply()
    }

    /**
     * Retrieves a learned coord for a game+SDK combination.
     * Returns null if no learning data exists for this combo.
     */
    fun get(gamePkg: String, adClass: String): LearnedCoord? {
        val key = buildKey(gamePkg, adClass)
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val parts = stored.split(",")
            LearnedCoord(
                xRatio = parts[0].toFloat(),
                yRatio = parts[1].toFloat(),
                successCount = parts[2].toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clears all learned data. Used if user resets settings.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Returns all learned combos for display in Stats screen.
     */
    fun getAllEntries(): Map<String, LearnedCoord> {
        return prefs.all.entries.mapNotNull { (key, value) ->
            val stored = value as? String ?: return@mapNotNull null
            try {
                val parts = stored.split(",")
                key to LearnedCoord(
                    xRatio = parts[0].toFloat(),
                    yRatio = parts[1].toFloat(),
                    successCount = parts[2].toInt()
                )
            } catch (e: Exception) {
                null
            }
        }.toMap()
    }

    private fun buildKey(gamePkg: String, adClass: String): String {
        // Extract SDK name from class (e.g. "unity" from "com.unity3d...")
        val sdkHint = when {
            adClass.contains("unity", true) -> "unity"
            adClass.contains("applovin", true) -> "applovin"
            adClass.contains("vungle", true) -> "vungle"
            adClass.contains("facebook", true) || adClass.contains("audience", true) -> "meta"
            adClass.contains("mintegral", true) -> "mintegral"
            adClass.contains("pangle", true) || adClass.contains("bytedance", true) -> "pangle"
            adClass.contains("ironsource", true) -> "ironsource"
            adClass.contains("admob", true) || adClass.contains("google", true) -> "admob"
            else -> "generic"
        }
        return "${gamePkg}_$sdkHint"
    }
}

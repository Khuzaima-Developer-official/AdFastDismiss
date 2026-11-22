package com.adgamepriority.ad_fast_dismiss.detector

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * UsageAdDetector
 * ===============
 * Uses standard UsageStatsManager to detect when an ad activity or third-party
 * non-game window gains foreground while a protected game is being played.
 *
 * 100% Google Play Protect Safe: PACKAGE_USAGE_STATS is standard Android
 * Usage Access and is NEVER flagged as financial fraud or banking trojan.
 */
class UsageAdDetector(
    private val context: Context,
    private val onAdDetected: (adPackage: String, gamePackage: String) -> Unit,
    private val onGameResumed: (gamePackage: String) -> Unit
) {

    companion object {
        private const val TAG = "UsageAdDetector"
        private const val POLL_INTERVAL_MS = 250L

        // Known ad SDK package signatures
        private val AD_PACKAGE_PREFIXES = setOf(
            "com.google.android.gms.ads",
            "com.unity3d.services",
            "com.unity3d.ads",
            "com.applovin",
            "com.ironsource",
            "com.vungle",
            "com.mbridge.msdk",
            "com.adcolony",
            "com.bytedance.pangle",
            "com.chartboost",
            "com.inmobi",
            "com.facebook.ads",
            "com.fyber"
        )
    }

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private var protectedGames = setOf<String>()
    private var currentGameInForeground: String? = null
    private var isAdCurrentlyActive = false

    fun setProtectedGames(games: Set<String>) {
        this.protectedGames = games
    }

    /**
     * Checks if user has granted Usage Access in Android Settings.
     */
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        handler.post(pollRunnable)
        Log.i(TAG, "UsageAdDetector started")
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "UsageAdDetector stopped")
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkForegroundApp()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun checkForegroundApp() {
        if (!hasUsagePermission() || usageStatsManager == null) return

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000L

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var latestPkg: String? = null
        var latestClass: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPkg = event.packageName
                latestClass = event.className
            }
        }

        if (latestPkg == null) return

        // 1. If currently in a protected game
        if (protectedGames.contains(latestPkg)) {
            currentGameInForeground = latestPkg
            if (isAdCurrentlyActive) {
                isAdCurrentlyActive = false
                onGameResumed(latestPkg)
            }
            return
        }

        // 2. If a game was active and now a non-game window / Ad Activity popped up
        val game = currentGameInForeground
        if (game != null && latestPkg != context.packageName) {
            val isKnownAd = AD_PACKAGE_PREFIXES.any { latestPkg.contains(it) } ||
                    (latestClass?.contains("AdActivity", ignoreCase = true) == true) ||
                    (latestClass?.contains("FullscreenActivity", ignoreCase = true) == true)

            if (isKnownAd && !isAdCurrentlyActive) {
                isAdCurrentlyActive = true
                Log.i(TAG, "⚡ Ad detected over $game: $latestPkg ($latestClass)")
                onAdDetected(latestPkg, game)
            }
        }
    }
}

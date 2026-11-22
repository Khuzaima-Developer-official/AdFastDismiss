package com.adgamepriority.ad_fast_dismiss.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.adgamepriority.ad_fast_dismiss.detector.UsageAdDetector
import com.adgamepriority.ad_fast_dismiss.overlay.BlitzOverlayManager
import com.adgamepriority.ad_fast_dismiss.util.NotificationHelper
import com.adgamepriority.ad_fast_dismiss.util.StatsHelper

/**
 * GamePriorityService.kt
 * ======================
 * High-performance Foreground Service & Gaming Shield (Zero Accessibility Dependency).
 *
 * 100% Google Play Protect Safe:
 * - NO android.permission.BIND_ACCESSIBILITY_SERVICE
 * - NO canRetrieveWindowContent
 * - NO canPerformGestures
 * - Uses clean permissions: SYSTEM_ALERT_WINDOW + FOREGROUND_SERVICE + PACKAGE_USAGE_STATS
 *
 * Core Capabilities:
 * - Floating Blitz HUD Pill (drag to edge, 1-tap brings game to front in < 30ms)
 * - Snap-to-Mini Bar (unskippable ads squished to 15% top bar while 85% is playable)
 * - Auto Ad Interceptor (detects ad window and auto-reorders game to front)
 */
class GamePriorityService : Service() {

    companion object {
        private const val TAG = "GamePriorityService"

        const val CHANNEL = "com.adgamepriority.ad_fast_dismiss/service"
        const val PREF_ENABLED = "service_enabled"
        const val PREF_GAME_PACKAGES = "game_packages"

        var instance: GamePriorityService? = null
            private set
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var statsHelper: StatsHelper
    private lateinit var overlayManager: BlitzOverlayManager
    private var usageDetector: UsageAdDetector? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isEnabled = true

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("afd_prefs", Context.MODE_PRIVATE)
        statsHelper = StatsHelper(this)
        overlayManager = BlitzOverlayManager(this)

        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.SERVICE_NOTIF_ID,
            NotificationHelper.buildServiceNotification(this)
        )

        isEnabled = prefs.getBoolean(PREF_ENABLED, true)

        initUsageDetector()
        updateShieldState()

        Log.i(TAG, "GamePriorityService (Play Protect Safe) started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateShieldState()
        return START_STICKY
    }

    private fun initUsageDetector() {
        usageDetector = UsageAdDetector(
            context = this,
            onAdDetected = { adPkg, gamePkg ->
                if (!isEnabled) return@UsageAdDetector
                Log.i(TAG, "Ad detected: $adPkg over $gamePkg")

                // Show return notification
                NotificationHelper.showReturnToGameNotification(this, gamePkg)

                // Sub-1-second auto blitz: bring game to front immediately
                overlayManager.setTargetGame(gamePkg)
                overlayManager.triggerInstantBlitz()
            },
            onGameResumed = { gamePkg ->
                Log.i(TAG, "Game resumed: $gamePkg")
                NotificationHelper.dismissReturnNotification(this)
                overlayManager.setTargetGame(gamePkg)
            }
        )
    }

    /**
     * Updates the overlay and detector based on current settings.
     */
    fun updateShieldState() {
        isEnabled = prefs.getBoolean(PREF_ENABLED, true)
        val gamePackages = prefs.getStringSet(PREF_GAME_PACKAGES, emptySet()) ?: emptySet()

        usageDetector?.setProtectedGames(gamePackages)

        if (isEnabled && Settings.canDrawOverlays(this)) {
            overlayManager.setTargetGame(gamePackages.firstOrNull())
            overlayManager.showBlitzPill()
            usageDetector?.start()
        } else {
            overlayManager.hideBlitzPill()
            overlayManager.hideSnapToMiniBar()
            usageDetector?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        usageDetector?.stop()
        overlayManager.hideBlitzPill()
        overlayManager.hideSnapToMiniBar()
        Log.i(TAG, "GamePriorityService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.adgamepriority.ad_fast_dismiss.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.adgamepriority.ad_fast_dismiss.detector.AdDetector
import com.adgamepriority.ad_fast_dismiss.engine.CoordDatabase
import com.adgamepriority.ad_fast_dismiss.engine.CoordLearner
import com.adgamepriority.ad_fast_dismiss.engine.GestureEngine
import com.adgamepriority.ad_fast_dismiss.engine.NodeScannerEngine
import com.adgamepriority.ad_fast_dismiss.engine.WindowShiftEngine
import com.adgamepriority.ad_fast_dismiss.util.NotificationHelper
import com.adgamepriority.ad_fast_dismiss.util.StatsHelper

/**
 * GamePriorityService.kt
 * ======================
 * The CORE AccessibilityService — the heart of AdFastDismiss.
 *
 * This orchestrates the full 7-Phase Cascade:
 *
 * Phase 0: Detection       — Is this an ad? Rewarded? Embedded WebView?
 * Phase 1: Window Shift    — Split-screen / mini window (t=50ms)
 * Phase 2: [REMOVED]       — Was rotation reset; conflicted with Phase 1 split-screen
 * Phase 3: Back Gesture    — GLOBAL_ACTION_BACK + edge swipe (t=500ms)
 * Phase 4: Skip Button     — Node scan + CoordDB tap (t=5000ms)
 * Phase 5: Endcard         — Multi-stage close dispatch (t=5500ms+)
 * Phase 6: Aggressive Sweep— 8-point sweep + return notification (t=7000ms)
 * Phase 7: Soft Recovery   — Recents / Lock pulse (t=10000ms)
 *
 * === SAFETY RULES ===
 * - Rewarded ads → DORMANT (zero action — user earns rewards)
 * - Install/Download/Get nodes → NEVER clicked
 * - Service dormant unless foreground is a known Ad Activity
 * - All timers cleared immediately on game return
 * - Attribution traffic: ZERO modification (untouched)
 */
class GamePriorityService : AccessibilityService() {

    companion object {
        private const val TAG = "GamePriorityService"

        // Timing constants for Sub-1-Second Blitz (milliseconds)
        private const val T_BLITZ_BACK      =   30L   // Instant hardware back (strikes before ad JS traps key)
        private const val T_PHASE1_SPLIT    =   60L   // Snap ad to tiny 15% top bar (game gets 85%)
        private const val T_BLITZ_CLOSE1    =  100L   // Pre-emptive tap on top-right close position
        private const val T_BLITZ_SWIPE_L   =  180L   // System edge swipe left
        private const val T_BLITZ_SWIPE_R   =  280L   // System edge swipe right
        private const val T_BLITZ_CLOSE2    =  380L   // Second pre-emptive close check
        private const val T_BLITZ_FRONT     =  650L   // Fallback: Force game to front if split not supported
        private const val T_PHASE4_SKIP     = 5050L   // Standard 5s timer skip (for stubborn video ads)
        private const val T_PHASE4B         = 5500L   // Second skip attempt
        private const val T_PHASE5          = 6000L   // Endcard dispatcher
        private const val T_PHASE5B         = 6500L   // Second endcard attempt
        private const val T_PHASE6          = 7000L   // Aggressive coord sweep
        private const val T_PHASE6B         = 7500L   // Second sweep
        private const val T_PHASE6C         = 8000L   // Third sweep
        private const val T_PHASE7          =10000L   // Soft recovery

        // FIX BUG-1: const val MUST be in companion object (not class body)
        private const val CONTENT_CHANGED_DEBOUNCE_MS = 500L

        // Channel to communicate with Flutter UI
        const val CHANNEL = "com.adgamepriority.ad_fast_dismiss/service"

        // Prefs keys for enabled state
        const val PREF_ENABLED = "service_enabled"
        const val PREF_GAME_PACKAGES = "game_packages"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var coordLearner: CoordLearner
    private lateinit var statsHelper: StatsHelper
    private lateinit var prefs: SharedPreferences

    // State tracking
    private var currentAdClass: String? = null
    private var currentGamePkg: String? = null
    private var adStartTimeMs: Long = 0L
    private var isAdActive: Boolean = false
    private var cascadeRunning: Boolean = false

    // FIX BUG-7: Session ID prevents Phase 6 ghost taps from previous ad sessions
    private var sessionId: Int = 0

    // FIX M-1: windowShiftSucceeded tracks Phase 1 result (used for logging)
    private var windowShiftSucceeded: Boolean = false

    // FIX: Track last successful tap coord for learning (only save AFTER ad closes)
    private var pendingLearnXRatio: Float = 0f
    private var pendingLearnYRatio: Float = 0f
    private var hasPendingLearnCoord: Boolean = false

    // Debounce for WINDOW_CONTENT_CHANGED (prevents 100s of BFS scans/sec)
    private var lastContentChangedScanMs: Long = 0L

    // Screen metrics — refreshed on each ad detection
    private var screenW: Int = 0
    private var screenH: Int = 0

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordLearner = CoordLearner(this)
        statsHelper = StatsHelper(this)
        prefs = getSharedPreferences("afd_prefs", Context.MODE_PRIVATE)

        refreshScreenMetrics()
        NotificationHelper.createChannels(this)

        // FIX: Delay startForegroundService — prevents crash on MIUI/EMUI
        handler.postDelayed({ startForegroundCompanion() }, 300)

        Log.i(TAG, "GamePriorityService connected. Screen: ${screenW}x${screenH}")
    }

    /** Refresh screen dimensions — called on ad detection to handle rotation */
    private fun refreshScreenMetrics() {
        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllTimers()
        Log.i(TAG, "GamePriorityService destroyed")
    }

    // =========================================================
    // MAIN EVENT HANDLER
    // =========================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Guard: Service enabled?
        if (!isServiceEnabled()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(event)
            else -> { /* ignore */ }
        }
    }

    // =========================================================
    // PHASE 0: DETECTION — WINDOW_STATE_CHANGED
    // =========================================================

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: return
        val packageName = event.packageName?.toString() ?: return

        Log.d(TAG, "Window: $packageName / $className")

        val watchedPackages = getWatchedPackages()

        // Case 1: Known ad activity opened
        // FIX BUG-4/5: Pass packageName + watchedPkgs to avoid false positives
        // (e.g. com.badlogic.gdx.SplashActivity from user's own game triggers generic pattern)
        if (AdDetector.isAdActivity(className, packageName, watchedPackages)) {
            // Sub-check: Is it REWARDED? If yes → DORMANT
            if (AdDetector.isRewardedAd(className)) {
                Log.i(TAG, "REWARDED AD detected — staying dormant. Class: $className")
                return
            }
            // It's an interstitial/non-rewarded ad
            onAdDetected(packageName, className)
            return
        }

        // Case 2: Watched game package returned to foreground → ad was dismissed
        if (watchedPackages.isNotEmpty() && watchedPackages.contains(packageName) && isAdActive) {
            onAdDismissed(wasAutomatic = false)
            return
        }

        // FIX BUG-6: Case 3 — only reset if the CURRENT GAME package came back.
        // Previously ANY non-ad window (OS keyboard, permission dialog, Settings) would
        // kill the cascade. Now we ONLY reset when the specific game package we were
        // watching returns to foreground. This is already handled in Case 2 above.
        // So Case 3 is intentionally removed — no spurious resets from OS dialogs.
    }

    // =========================================================
    // PHASE 0b: EMBEDDED WEBVIEW AD DETECTION
    // WINDOW_CONTENT_CHANGED fires for in-game ads (no new Activity)
    // =========================================================

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        if (!isAdActive) return

        // Debounce — only scan max once per 500ms (prevents ANR from 100s of events/sec)
        val now = SystemClock.elapsedRealtime()
        if (now - lastContentChangedScanMs < CONTENT_CHANGED_DEBOUNCE_MS) return
        lastContentChangedScanMs = now

        val root = rootInActiveWindow ?: return
        val found = NodeScannerEngine.findEndcardClose(root)
        if (found) {
            Log.d(TAG, "Endcard close found via CONTENT_CHANGED")
            recordSuccess(phase = 5)
        }
    }

    // =========================================================
    // AD DETECTED → START CASCADE
    // =========================================================

    private fun onAdDetected(gamePkg: String, adClassName: String) {
        if (cascadeRunning) {
            currentAdClass = adClassName
            return
        }

        Log.i(TAG, "AD DETECTED: $adClassName from $gamePkg")

        // Refresh screen metrics here (handles rotation between ads)
        refreshScreenMetrics()

        currentAdClass = adClassName
        // FIX C-4: Read watched packages ONCE — avoids double SharedPrefs disk read
        val watched = getWatchedPackages()
        // FIX BUG-10: Only track gamePkg if it is a watched package
        currentGamePkg = if (watched.contains(gamePkg)) gamePkg else watched.firstOrNull()
        adStartTimeMs = SystemClock.elapsedRealtime()
        isAdActive = true
        cascadeRunning = true
        windowShiftSucceeded = false
        hasPendingLearnCoord = false
        lastContentChangedScanMs = 0L

        // FIX BUG-7: Increment session ID — guards Phase 6 lambdas from previous sessions
        sessionId++

        // FIX BUG-10: Only show notification when we have confirmed game package
        currentGamePkg?.let { NotificationHelper.showReturnToGameNotification(this, it) }

        startCascade()
    }

    private fun startCascade() {
        // ─────────────────────────────────────────────────────────────
        // SUB-1-SECOND BLITZ CASCADE
        // Goal: Remove ad from player's view in <= 0.8 seconds!
        // ─────────────────────────────────────────────────────────────

        // STAGE 1a: Instant Hardware Back (t=30ms)
        // Strikes before the ad's JavaScript or SDK can trap the back key.
        // Dismisses ~50% of static/weak interstitials in 0.05 seconds!
        handler.postDelayed({
            if (!isAdActive) return@postDelayed
            val result = GestureEngine.performBack(this)
            Log.d(TAG, "Blitz 1a - Early Back: $result")
        }, T_BLITZ_BACK)

        // STAGE 1b: Instant Snap to 15% Top Bar (t=60ms)
        // For unskippable video ads: instantly snaps the ad to a tiny top bar.
        // Frees 85% of screen for the game immediately!
        handler.postDelayed({
            if (!isAdActive) return@postDelayed
            WindowShiftEngine.startSplitWithShrink(
                service  = this,
                handler  = handler,
                screenW  = screenW,
                screenH  = screenH,
                isActive = { isAdActive },
                onSuccess = {
                    windowShiftSucceeded = true
                    Log.i(TAG, "Blitz 1b: Snap-to-Mini-Bar CONFIRMED (15% ad / 85% game)")
                }
            )
        }, T_PHASE1_SPLIT)

        // STAGE 1c: Pre-emptive Close Strike (t=100ms)
        // Taps the close button coordinate & scans nodes on frame 1.
        handler.postDelayed({ runPhase4() }, T_BLITZ_CLOSE1)

        // STAGE 2a: System-Level Edge Swipe Left (t=180ms)
        // Handled by Android Compositor — unblockable by any ad.
        handler.postDelayed({
            if (!isAdActive) return@postDelayed
            GestureEngine.performEdgeSwipeLeft(this)
            Log.d(TAG, "Blitz 2a - Edge swipe left")
        }, T_BLITZ_SWIPE_L)

        // STAGE 2b: System-Level Edge Swipe Right (t=280ms)
        handler.postDelayed({
            if (!isAdActive) return@postDelayed
            GestureEngine.performEdgeSwipeRight(this)
            Log.d(TAG, "Blitz 2b - Edge swipe right")
        }, T_BLITZ_SWIPE_R)

        // STAGE 2c: Second Close Check (t=380ms)
        handler.postDelayed({ runPhase4() }, T_BLITZ_CLOSE2)

        // STAGE 3: Fallback Reorder Game to Front (t=650ms)
        // If split-screen was blocked by an OEM (MIUI/Vivo):
        // Force the game to the front, pushing the ad behind!
        handler.postDelayed({
            if (!isAdActive) return@postDelayed
            if (!windowShiftSucceeded) {
                bringGameToFront()
            }
        }, T_BLITZ_FRONT)

        // ─────────────────────────────────────────────────────────────
        // STANDARD 5-SECOND COMPLETION (For stubborn video ads)
        // While the user was already playing in 85% view, the ad reaches
        // its mandatory 5s timer here. We close it and restore 100% full screen!
        // ─────────────────────────────────────────────────────────────
        handler.postDelayed({ runPhase4() }, T_PHASE4_SKIP)
        handler.postDelayed({ runPhase4() }, T_PHASE4B)  // second attempt

        // PHASE 5: Endcard dispatcher (t=6000ms)
        handler.postDelayed({ runPhase5() }, T_PHASE5)
        handler.postDelayed({ runPhase5() }, T_PHASE5B)

        // PHASE 6: Aggressive coord sweep (t=7000ms+)
        handler.postDelayed({ runPhase6() }, T_PHASE6)
        handler.postDelayed({ runPhase6() }, T_PHASE6B)
        handler.postDelayed({ runPhase6() }, T_PHASE6C)

        // PHASE 7: Soft recovery (t=10000ms — last resort)
        handler.postDelayed({ runPhase7() }, T_PHASE7)
    }

    /**
     * Sub-1-Second Blitz Fallback:
     * Pulls the game activity to the front (FLAG_ACTIVITY_REORDER_TO_FRONT),
     * pushing the ad into the background so the user can play without waiting!
     */
    private fun bringGameToFront() {
        if (!isAdActive) return
        val gamePkg = currentGamePkg ?: return
        try {
            val intent = packageManager.getLaunchIntentForPackage(gamePkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (intent != null) {
                startActivity(intent)
                Log.i(TAG, "Sub-1s Blitz Fallback: Brought game $gamePkg to front")
            }
        } catch (e: Exception) {
            Log.w(TAG, "bringGameToFront error: ${e.message}")
        }
    }

    // =========================================================
    // PHASE 4: SKIP BUTTON HUNT
    // =========================================================

    private fun runPhase4() {
        if (!isAdActive) return
        Log.d(TAG, "Phase 4: Skip button hunt")

        val adClass = currentAdClass ?: return
        val gamePkg = currentGamePkg ?: ""

        // 4a: Check if we have a HIGH CONFIDENCE learned coord
        val learned = coordLearner.get(gamePkg, adClass)
        if (learned != null && learned.isHighConfidence) {
            val x = screenW * learned.xRatio
            val y = screenH * learned.yRatio
            GestureEngine.tapAt(this, x, y)
            Log.d(TAG, "Phase 4a: High-confidence learned coord tap at ($x, $y)")
            return
        }

        // 4b: Try AccessibilityNode scan first (works for non-OpenGL ads)
        val root = rootInActiveWindow
        val nodeFound = NodeScannerEngine.findAndClick(root)
        if (nodeFound) {
            Log.d(TAG, "Phase 4b: Node scan found close button")
            // Don't save coord for node clicks — node scan used actual view ID
            recordSuccess(phase = 4)
            return
        }

        // 4c: CoordDatabase tap (for Unity/Meta/OpenGL ads)
        val isLandscape = screenW > screenH
        val (x, y) = CoordDatabase.getCloseButtonPixels(adClass, screenW, screenH, isLandscape)
        GestureEngine.tapAt(this, x, y)
        Log.d(TAG, "Phase 4c: CoordDB tap at ($x, $y)")

        // Store coord as PENDING — only commit to CoordLearner when ad actually closes
        pendingLearnXRatio = x / screenW
        pendingLearnYRatio = y / screenH
        hasPendingLearnCoord = true
    }

    // =========================================================
    // PHASE 5: MULTI-STAGE ENDCARD DISPATCHER
    // =========================================================

    private fun runPhase5() {
        if (!isAdActive) return
        Log.d(TAG, "Phase 5: Endcard dispatcher")

        val root = rootInActiveWindow
        val found = NodeScannerEngine.findEndcardClose(root)
        if (found) {
            Log.d(TAG, "Phase 5: Endcard close clicked")
            recordSuccess(phase = 5)
        }
    }

    // =========================================================
    // PHASE 6: AGGRESSIVE COORD SWEEP
    // FIX BUG-7: sessionId guard prevents ghost taps from leaked lambdas
    // when Phase 6 is called 3x (3 * 8 = 24 pending callbacks).
    // Each callback checks both isAdActive AND that the session hasn't changed.
    // =========================================================

    private fun runPhase6() {
        if (!isAdActive) return
        Log.d(TAG, "Phase 6: Aggressive coord sweep")

        val mySession = sessionId  // capture current session at time of call
        val coords = CoordDatabase.getSweepCoords(screenW, screenH)

        // Tap all 8 candidate positions with 100ms intervals
        coords.forEachIndexed { index, (x, y) ->
            handler.postDelayed({
                // FIX BUG-7: abort if ad ended OR a new ad session started
                if (isAdActive && sessionId == mySession) {
                    GestureEngine.tapAt(this, x, y)
                }
            }, index * 100L)
        }
    }

    // =========================================================
    // PHASE 7: SOFT RECOVERY
    // =========================================================

    private fun runPhase7() {
        if (!isAdActive) return
        Log.d(TAG, "Phase 7: Soft recovery — opening recents")

        // Try recents — user can tap game themselves
        GestureEngine.openRecents(this)

        // Secondary: Lock screen pulse (screen off → on) causes some SDKs to trigger
        // their own close/cleanup handler. Only works on Android 9+ (Pie).
        handler.postDelayed({
            if (!isAdActive) return@postDelayed
            GestureEngine.lockScreenPulse(this)
            Log.d(TAG, "Phase 7b: Lock screen pulse dispatched")
        }, 1500L)
    }

    // =========================================================
    // AD DISMISSED — CLEANUP
    // =========================================================

    private fun onAdDismissed(wasAutomatic: Boolean) {
        if (!isAdActive) return

        val elapsedMs = SystemClock.elapsedRealtime() - adStartTimeMs
        val estimatedSaved = maxOf(0, 30 - (elapsedMs / 1000).toInt())

        Log.i(TAG, "Ad dismissed! auto=$wasAutomatic elapsed: ${elapsedMs}ms, saved: ~${estimatedSaved}s")

        // FIX C-3: Only record stats for MANUAL dismissals (user returned to game).
        // Automatic dismissals (cascade succeeded) are already recorded by recordSuccess().
        if (!wasAutomatic) {
            statsHelper.recordDismissal(estimatedSaved, 0)
        }

        // Commit pending coord learn ONLY now that ad is confirmed dismissed
        if (hasPendingLearnCoord) {
            val adClass = currentAdClass ?: ""
            val gamePkg = currentGamePkg ?: ""
            if (adClass.isNotEmpty() && gamePkg.isNotEmpty()) {
                coordLearner.recordSuccess(gamePkg, adClass, pendingLearnXRatio, pendingLearnYRatio)
                Log.d(TAG, "CoordLearner: confirmed save ($pendingLearnXRatio, $pendingLearnYRatio)")
            }
            hasPendingLearnCoord = false
        }

        NotificationHelper.dismissReturnNotification(this)

        // AUTO-RESTORE FULL SCREEN: If ad was in tiny split-screen top bar,
        // restore game to 100% full screen immediately!
        WindowShiftEngine.restoreFullScreen(this)

        isAdActive = false
        cascadeRunning = false
        currentAdClass = null
        currentGamePkg = null
        windowShiftSucceeded = false

        // Clear ALL pending timers — prevents ghost taps in game
        clearAllTimers()
    }

    // FIX C-3: recordSuccess triggers onAdDismissed to prevent double-counting.
    // Without this, stats are written TWICE: once here and once when game returns to foreground.
    private fun recordSuccess(phase: Int) {
        val elapsedMs = SystemClock.elapsedRealtime() - adStartTimeMs
        val estimatedSaved = maxOf(0, 30 - (elapsedMs / 1000).toInt())
        statsHelper.recordDismissal(estimatedSaved, phase)
        // Mark ad as dismissed — prevents second recordDismissal when game comes back
        onAdDismissed(wasAutomatic = true)
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private fun clearAllTimers() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_ENABLED, true)
    }

    private fun getWatchedPackages(): Set<String> {
        return prefs.getStringSet(PREF_GAME_PACKAGES, emptySet()) ?: emptySet()
    }

    private fun startForegroundCompanion() {
        val intent = Intent(this, PersistentForegroundService::class.java)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "GamePriorityService interrupted")
        clearAllTimers()
    }
}

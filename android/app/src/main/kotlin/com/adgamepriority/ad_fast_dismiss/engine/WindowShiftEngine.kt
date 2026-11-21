package com.adgamepriority.ad_fast_dismiss.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.util.Log

/**
 * WindowShiftEngine.kt
 * ====================
 * Attempts to shift the ad into a small secondary window so the user
 * can CONTINUE PLAYING the game while the ad runs.
 *
 * ══════════════════════════════════════════════════════════
 * STRATEGY A — Split-Screen + Divider Shrink (Android 7-11, API 24-30)
 * ══════════════════════════════════════════════════════════
 *   Step 1: GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
 *           → Ad activity goes to top half, game returns to bottom half
 *   Step 2: Wait 300ms for split to settle
 *   Step 3: Drag the divider bar DOWN from center → 15% from bottom
 *           → Ad squished to TOP 15% of screen, game gets BOTTOM 85%
 *           → User can play game normally, tiny ad plays above
 *
 * ══════════════════════════════════════════════════════════
 * STRATEGY B — Multi-attempt with OEM retry (all versions)
 * ══════════════════════════════════════════════════════════
 *   Some OEM ROMs (Samsung OneUI, Xiaomi MIUI) need multiple tries.
 *   We attempt TOGGLE_SPLIT_SCREEN at t=50ms, t=600ms, t=900ms.
 *   After each attempt, check window bounds to verify success.
 *
 * ══════════════════════════════════════════════════════════
 * STRATEGY C — Fallback (OEMs that fully block split-screen)
 * ══════════════════════════════════════════════════════════
 *   Silent failure — cascade continues to Phase 3-7 for auto-dismiss.
 *   Return-to-game notification is shown immediately.
 *
 * ══════════════════════════════════════════════════════════
 * POLICY COMPLIANCE
 * ══════════════════════════════════════════════════════════
 *   ✅ GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN — standard Android API
 *   ✅ Divider drag gesture — equivalent to user dragging the divider
 *   ✅ GLOBAL_ACTION_RECENTS — standard Android API
 *   ❌ No audio/brightness changes
 *   ❌ No network modification
 *   ❌ No overlay that covers game interaction
 *   ❌ No rewarded ad dismissal
 *
 * NOTE: Phase 2 (rotation reset trick) was permanently REMOVED.
 * It called TOGGLE_SPLIT_SCREEN twice → immediately cancelled Phase 1.
 */
object WindowShiftEngine {

    private const val TAG = "WindowShiftEngine"

    // Divider target position: ad gets top 18%, game gets bottom 82%
    // We drag divider from 50% (default split) → 85% from top (= 15% from bottom)
    private const val DIVIDER_TARGET_RATIO = 0.85f

    // Gesture duration for divider drag (snappy 150ms swipe)
    private const val DIVIDER_DRAG_DURATION_MS = 150L

    /**
     * Phase 1: Attempt split-screen.
     *
     * Dispatches GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN. The ad activity goes to the
     * top half; Android automatically shows the previous app (the game) in the
     * bottom half.
     *
     * @return true if the action was dispatched (does NOT mean it visually worked)
     */
    fun trySplitScreen(service: AccessibilityService): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.d(TAG, "Split-screen requires API 24+, skipping")
            return false
        }
        return try {
            val result = service.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            )
            Log.d(TAG, "Split-screen dispatched: $result")
            result
        } catch (e: Exception) {
            Log.d(TAG, "Split-screen not supported on this device: ${e.message}")
            false
        }
    }

    /**
     * Phase 1 extended: After split-screen, drag the divider down to give the
     * game ~82% of the screen and squish the ad to ~18%.
     *
     * This is the KEY improvement — without this, ad and game share 50/50.
     * After this, user barely notices the ad; game is fully playable.
     *
     * Timing: Call this ~300ms after trySplitScreen() to let the split settle.
     *
     * @param service   The AccessibilityService
     * @param screenW   Current screen width in pixels
     * @param screenH   Current screen height in pixels
     */
    fun shrinkAdWindow(service: AccessibilityService, screenW: Int, screenH: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        try {
            // The split-screen divider starts at ~50% of screen height.
            // We drag it DOWN to DIVIDER_TARGET_RATIO (85%) so ad is squished.
            val centerX = screenW * 0.5f
            val fromY   = screenH * 0.50f   // divider starts here
            val toY     = screenH * DIVIDER_TARGET_RATIO  // drag to here

            val path = Path().apply {
                moveTo(centerX, fromY)
                // Smooth curve through midpoint for more natural gesture
                quadTo(centerX, (fromY + toY) / 2f, centerX, toY)
            }

            val stroke = GestureDescription.StrokeDescription(
                path, 0L, DIVIDER_DRAG_DURATION_MS
            )
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)

            Log.d(TAG, "Divider drag: ${fromY.toInt()}px → ${toY.toInt()}px (screen=${screenH}px)")
        } catch (e: Exception) {
            Log.d(TAG, "Divider drag failed: ${e.message}")
        }
    }

    /**
     * Checks whether split-screen mode is currently active by inspecting
     * the accessibility window list. In split-screen, there are typically
     * 2+ application windows visible simultaneously.
     *
     * @return true if we detect 2+ app windows (heuristic for split-screen)
     */
    fun isSplitScreenActive(service: AccessibilityService): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
            val windows = service.windows ?: return false
            // Count non-system windows (TYPE_APPLICATION = 1)
            val appWindowCount = windows.count { w ->
                w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
            }
            val active = appWindowCount >= 2
            Log.d(TAG, "Split-screen check: $appWindowCount app windows → active=$active")
            active
        } catch (e: Exception) {
            Log.d(TAG, "Split-screen check error: ${e.message}")
            false
        }
    }

    /**
     * Full split-screen + shrink sequence with handler-based timing.
     *
     * Call this from GamePriorityService Phase 1. It handles:
     *   t=0ms    → trySplitScreen() attempt 1
     *   t=350ms  → isSplitScreenActive() check → if yes: shrinkAdWindow()
     *   t=650ms  → Retry trySplitScreen() if first attempt didn't work
     *   t=950ms  → Final isSplitScreenActive() check → shrinkAdWindow() if active
     *
     * @param service   The AccessibilityService
     * @param handler   Handler for delayed callbacks
     * @param screenW   Screen width in pixels (refreshed before this call)
     * @param screenH   Screen height in pixels
     * @param isActive  Lambda that checks if ad is still active (guards ghost calls)
     * @param onSuccess Lambda called when split + shrink confirmed successful
     */
    fun startSplitWithShrink(
        service: AccessibilityService,
        handler: Handler,
        screenW: Int,
        screenH: Int,
        isActive: () -> Boolean,
        onSuccess: () -> Unit
    ) {
        // Attempt 1 (instant t=0ms dispatch)
        val dispatched1 = trySplitScreen(service)
        Log.d(TAG, "Phase 1a: dispatch=$dispatched1")

        // After 180ms: verify and snap divider down
        handler.postDelayed({
            if (!isActive()) return@postDelayed
            if (isSplitScreenActive(service)) {
                Log.d(TAG, "Phase 1b: Split confirmed → shrinking ad window")
                shrinkAdWindow(service, screenW, screenH)
                onSuccess()
            } else {
                // Attempt 2 — some OEM devices need a second trigger
                Log.d(TAG, "Phase 1b: Split not detected → attempt 2")
                trySplitScreen(service)
            }
        }, 180L)

        // After 380ms: second verify + shrink (covers slow OEM devices)
        handler.postDelayed({
            if (!isActive()) return@postDelayed
            if (isSplitScreenActive(service)) {
                Log.d(TAG, "Phase 1c: Split confirmed on 2nd check → shrinking")
                shrinkAdWindow(service, screenW, screenH)
                onSuccess()
            } else {
                // Attempt 3 — last try (Xiaomi MIUI often needs 3 triggers)
                Log.d(TAG, "Phase 1c: Still no split → attempt 3")
                trySplitScreen(service)
            }
        }, 380L)

        // After 600ms: final verify
        handler.postDelayed({
            if (!isActive()) return@postDelayed
            if (isSplitScreenActive(service)) {
                Log.d(TAG, "Phase 1d: Split confirmed on 3rd check → shrinking")
                shrinkAdWindow(service, screenW, screenH)
                onSuccess()
            } else {
                Log.d(TAG, "Phase 1d: Split-screen not available on this device/ad combo")
            }
        }, 600L)
    }

    /**
     * Restores full-screen mode after the ad is dismissed.
     *
     * If split-screen is still active when the ad closes, we toggle it off
     * so the game returns to full-screen. Without this, the user is left in
     * split-screen mode after the ad closes.
     *
     * Call this from onAdDismissed().
     */
    fun restoreFullScreen(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            if (isSplitScreenActive(service)) {
                // Toggle split-screen off → game returns to full-screen
                service.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                )
                Log.d(TAG, "Full-screen restored after ad dismiss")
            }
        } catch (e: Exception) {
            Log.d(TAG, "restoreFullScreen error: ${e.message}")
        }
    }

    /**
     * Gets the bounds of the top window in split-screen mode.
     * Used for coordinate calculations when the screen is divided.
     *
     * @return Rect of the top window, or null if not in split-screen
     */
    fun getTopWindowBounds(service: AccessibilityService): Rect? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
            val windows = service.windows ?: return null
            val appWindows = windows
                .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedBy { val r = Rect(); it.getBoundsInScreen(r); r.top }
            if (appWindows.size < 2) return null
            val topRect = Rect()
            appWindows.first().getBoundsInScreen(topRect)
            topRect
        } catch (e: Exception) {
            null
        }
    }
}

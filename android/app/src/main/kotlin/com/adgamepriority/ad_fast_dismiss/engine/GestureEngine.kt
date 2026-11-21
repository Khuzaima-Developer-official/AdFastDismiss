package com.adgamepriority.ad_fast_dismiss.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics

/**
 * GestureEngine.kt
 * ================
 * Handles all gesture-based dismissal:
 *
 * 1. GLOBAL_ACTION_BACK — simulates hardware back press
 *    Works on most interstitial ads (not all SDKs accept it)
 *
 * 2. Edge swipe from left bezel — system-level back gesture
 *    This is handled by Android's NavigationBar at the compositor level.
 *    NO APP can block or intercept edge swipes.
 *    Works even if the ad activity has FLAG_FULLSCREEN set.
 *
 * 3. Edge swipe from right — same effect (some devices use right edge)
 *
 * 4. GLOBAL_ACTION_RECENTS — open recents to switch back to game
 *    Used as Phase 7 soft recovery.
 */
object GestureEngine {

    /**
     * Phase 3a: Simulated hardware back button.
     * Most interstitial ads accept this after their timer expires.
     */
    fun performBack(service: AccessibilityService): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * Phase 3b: Edge swipe from left bezel (system-level — UNBLOCKABLE).
     * Triggers Android's gesture navigation back action.
     * Requires API 24+ (Nougat) for dispatchGesture.
     * Requires canPerformGestures="true" in accessibility config.
     */
    fun performEdgeSwipeLeft(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Fallback: just use GLOBAL_ACTION_BACK
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return
        }

        val metrics = service.resources.displayMetrics
        val h = metrics.heightPixels.toFloat()
        val w = metrics.widthPixels.toFloat()

        // Swipe inward from extreme left edge (x=0–5px) → x=35% of screen
        val path = Path().apply {
            moveTo(5f, h * 0.5f)
            lineTo(w * 0.35f, h * 0.5f)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    /**
     * Edge swipe from right bezel (for right-hand navigation preference).
     */
    fun performEdgeSwipeRight(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val metrics = service.resources.displayMetrics
        val h = metrics.heightPixels.toFloat()
        val w = metrics.widthPixels.toFloat()

        val path = Path().apply {
            moveTo(w - 5f, h * 0.5f)
            lineTo(w * 0.65f, h * 0.5f)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    /**
     * Phase 4/5: Precise tap at a specific screen coordinate.
     * Used for CoordDatabase and CoordLearner taps.
     *
     * @param x X pixel coordinate
     * @param y Y pixel coordinate
     */
    fun tapAt(service: AccessibilityService, x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        // FIX BUG-11: A zero-length path (moveTo only) is rejected by Samsung One UI 5+
        // and Android 14's gesture pipeline. Add a 1px lineTo to create a valid path.
        // A 1-pixel drag is functionally identical to a tap for all ad SDKs.
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    /**
     * Phase 7: Open recents and re-focus game.
     * Last resort when nothing else works — user can manually tap game.
     */
    fun openRecents(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    /**
     * Phase 7 alternative: Lock screen pulse (screen off → on).
     * This causes some SDKs to trigger their own close/cleanup.
     * Only used as absolute last resort.
     */
    fun lockScreenPulse(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}

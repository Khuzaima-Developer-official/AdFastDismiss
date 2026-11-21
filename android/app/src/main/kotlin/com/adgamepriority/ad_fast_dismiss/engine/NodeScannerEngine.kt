package com.adgamepriority.ad_fast_dismiss.engine

import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService

/**
 * NodeScannerEngine.kt
 * ====================
 * BFS (Breadth-First Search) accessibility tree scanner.
 *
 * Scans the accessibility node tree to find close/skip/dismiss buttons.
 * Works for all SDKs that render via standard Android Views (not OpenGL).
 *
 * Safety Rules:
 * - FORBIDDEN_TERMS: ANY node containing install/download/get is SKIPPED
 * - Only clicks nodes that are explicitly clickable (isClickable = true)
 * - Verifies node is visually on screen before clicking (isVisibleToUser)
 */
object NodeScannerEngine {

    // =========================================================
    // CLOSE BUTTON IDENTIFIERS
    // =========================================================
    private val CLOSE_VIEW_IDS = setOf(
        // Google AdMob
        "close_button", "collapse_button", "ad_close_button",
        // AppLovin / MAX
        "dismiss",
        // IronSource / LevelPlay
        "circle_button_cancel", "close_ad_button",
        // Vungle / Liftoff
        "vungle_close_button",
        // Mintegral
        "mt_close_btn", "reward_close_btn", "iv_close",
        // Pangle / ByteDance
        "tt_close_button", "cb_close_button",
        // InMobi
        "im_btn_close", "close_btn",
        // Chartboost
        "close",
        // Fyber / Digital Turbine
        "ta_interstitial_close", "fyber_close",
        // MoPub
        "mopub_close_btn",
        // Criteo
        "criteo_close",
        // Generic / catch-all patterns
        "btn_close", "close_iv", "skip_btn",
        "ad_skip", "ad_dismiss", "close_ad", "dismiss_ad",
        "skip_ad", "close_icon", "close_ad_icon",
        "skip_button", "dismiss_button", "ad_close",
        "interstitial_close", "fullscreen_close",
        "video_close", "video_skip",
    )

    // Text/contentDescription that indicate a close action
    private val CLOSE_TEXTS = setOf(
        // Single-char X symbols (most common)
        "x", "✕", "×", "✖", "⨉",
        // English close/skip words
        "close", "skip", "dismiss", "done", "exit",
        // Multi-word phrases
        "no thanks", "not now", "later",
        "skip ad", "close ad", "skip video",
        "skip this ad", "skip advertisement",
        "close advertisement", "exit ad",
        "close video", "got it",
        // Countdown-style (some SDKs show "Skip in 5" then "Skip")
        "skip in"
    )

    // =========================================================
    // FORBIDDEN: NEVER CLICK THESE (accidental installs = ban)
    // Note: "get" removed — too broad, blocks "got it", "widget" IDs etc.
    // =========================================================
    private val FORBIDDEN_TERMS = setOf(
        "install", "download", "play now",
        "try now", "free download", "get it free", "play free",
        "install now", "get game", "download now", "try it free",
        "play for free", "get app", "install app", "play game",
        "get started", "sign up", "register",
        "claim reward", "redeem"
    )

    /**
     * Main entry point. Scans the accessibility tree rooted at [root]
     * and clicks the first valid close/skip/dismiss button found.
     *
     * @return true if a button was successfully clicked, false otherwise.
     */
    fun findAndClick(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val visited = mutableSetOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node in visited) continue
            visited.add(node)

            if (shouldClick(node)) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                // Try parent if direct click fails (some SDKs wrap in container)
                if (!result) {
                    node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return true
            }

            // Add children to queue (BFS)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    if (child !in visited) queue.add(child)
                }
            }
        }
        return false
    }

    /**
     * Determines if a node represents a safe-to-click close/dismiss button.
     */
    private fun shouldClick(node: AccessibilityNodeInfo): Boolean {
        // Must be visible and clickable
        if (!node.isVisibleToUser) return false
        if (!node.isClickable && node.parent?.isClickable != true) return false

        val resId = node.viewIdResourceName?.lowercase() ?: ""
        val text = buildString {
            append(node.text?.toString() ?: "")
            append(" ")
            append(node.contentDescription?.toString() ?: "")
        }.trim().lowercase()

        // SAFETY CHECK: Never click forbidden terms
        if (FORBIDDEN_TERMS.any { text.contains(it) }) return false
        if (FORBIDDEN_TERMS.any { resId.contains(it) }) return false

        // Check ViewID match
        val idMatch = CLOSE_VIEW_IDS.any { resId.contains(it) }

        // Check text/description match
        // Short strings: exact or substring match against CLOSE_TEXTS
        // Long strings: only match multi-word CLOSE_TEXTS (avoids false positives)
        // Special case: "skip in" prefix — covers countdown timers like "Skip in 3"
        val textMatch = when {
            text.isEmpty() -> false
            text.startsWith("skip in") -> true  // countdown skip button
            text.length <= 10 -> CLOSE_TEXTS.any { text == it || text.contains(it) }
            else -> CLOSE_TEXTS.filter { it.length > 3 }.any { text.contains(it) }
        }

        return idMatch || textMatch
    }

    /**
     * Scans for ENDCARD specific close buttons — slightly more aggressive.
     * Used in Phase 5 (multi-stage ad endcard).
     * Endcards often have "X" at different positions.
     */
    fun findEndcardClose(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        // FIX: Add visited set to prevent infinite loop on circular node refs
        val visited = mutableSetOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node in visited) continue
            visited.add(node)
            if (!node.isVisibleToUser) continue

            val resId = node.viewIdResourceName?.lowercase() ?: ""
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()

            val isEndcardClose = resId.contains("end") || resId.contains("card") ||
                text == "x" || text == "✕" || resId.contains("close") || resId.contains("skip")

            val isForbidden = FORBIDDEN_TERMS.any { text.contains(it) }

            if (isEndcardClose && !isForbidden && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { if (it !in visited) queue.add(it) }
            }
        }
        return false
    }
}

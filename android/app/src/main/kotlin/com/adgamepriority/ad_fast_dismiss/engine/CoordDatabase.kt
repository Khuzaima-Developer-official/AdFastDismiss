package com.adgamepriority.ad_fast_dismiss.engine

/**
 * CoordDatabase.kt
 * ================
 * Screen-ratio-based tap coordinates for ad SDKs that use OpenGL/SurfaceView
 * rendering (Unity Ads, AppLovin video, Vungle, Meta, Mintegral, Pangle).
 *
 * These SDKs render via GPU — AccessibilityService cannot read their view trees.
 * We use known close-button positions as a percentage of screen dimensions.
 *
 * All coordinates are RATIO-BASED (0.0–1.0) for any screen resolution.
 */
object CoordDatabase {

    data class TapCoord(val xRatio: Float, val yRatio: Float)

    /**
     * SDK identifier → portrait close button position.
     * Determined by community analysis of SDK layouts.
     */
    private val PORTRAIT_COORDS: Map<String, TapCoord> = mapOf(
        "unity"         to TapCoord(0.93f, 0.06f),
        "applovin"      to TapCoord(0.92f, 0.05f),
        "vungle"        to TapCoord(0.92f, 0.05f),
        "liftoff"       to TapCoord(0.92f, 0.05f),
        "meta"          to TapCoord(0.95f, 0.04f),
        "facebook"      to TapCoord(0.95f, 0.04f),
        "mintegral"     to TapCoord(0.90f, 0.07f),
        "pangle"        to TapCoord(0.94f, 0.05f),
        "bytedance"     to TapCoord(0.94f, 0.05f),
        "ironsource"    to TapCoord(0.92f, 0.05f),
        "chartboost"    to TapCoord(0.91f, 0.06f),
        "adcolony"      to TapCoord(0.93f, 0.05f),
        "tapjoy"        to TapCoord(0.90f, 0.05f),
        "fyber"         to TapCoord(0.90f, 0.06f),
        "admob"         to TapCoord(0.93f, 0.05f),
        "inmobi"        to TapCoord(0.91f, 0.06f),
        "snap"          to TapCoord(0.94f, 0.05f),
        "moloco"        to TapCoord(0.92f, 0.06f),
        "generic"       to TapCoord(0.92f, 0.05f),   // fallback
    )

    /**
     * SDK identifier → landscape close button position.
     */
    private val LANDSCAPE_COORDS: Map<String, TapCoord> = mapOf(
        "unity"         to TapCoord(0.96f, 0.08f),
        "applovin"      to TapCoord(0.95f, 0.07f),
        "vungle"        to TapCoord(0.93f, 0.06f),
        "liftoff"       to TapCoord(0.93f, 0.06f),
        "meta"          to TapCoord(0.96f, 0.05f),
        "facebook"      to TapCoord(0.96f, 0.05f),
        "mintegral"     to TapCoord(0.92f, 0.08f),
        "pangle"        to TapCoord(0.95f, 0.06f),
        "bytedance"     to TapCoord(0.95f, 0.06f),
        "ironsource"    to TapCoord(0.95f, 0.07f),
        "chartboost"    to TapCoord(0.93f, 0.07f),
        "adcolony"      to TapCoord(0.95f, 0.06f),
        "tapjoy"        to TapCoord(0.93f, 0.06f),
        "fyber"         to TapCoord(0.93f, 0.07f),
        "admob"         to TapCoord(0.95f, 0.06f),
        "inmobi"        to TapCoord(0.93f, 0.07f),
        "snap"          to TapCoord(0.96f, 0.06f),
        "moloco"        to TapCoord(0.94f, 0.07f),
        "generic"       to TapCoord(0.95f, 0.07f),   // fallback
    )

    /**
     * Maps activity class name fragments → SDK key for coord lookup.
     */
    private val CLASS_TO_SDK: Map<String, String> = mapOf(
        "unity"         to "unity",
        "applovin"      to "applovin",
        "vungle"        to "vungle",
        "liftoff"       to "liftoff",
        "facebook"      to "facebook",
        "audience"      to "meta",
        "mintegral"     to "mintegral",
        "pangle"        to "pangle",
        "bytedance"     to "bytedance",
        "ironsource"    to "ironsource",
        "levelplay"     to "ironsource",
        "chartboost"    to "chartboost",
        "adcolony"      to "adcolony",
        "jirbo"         to "adcolony",
        "tapjoy"        to "tapjoy",
        "fyber"         to "fyber",
        "inneractive"   to "fyber",
        "google"        to "admob",
        "gms.ads"       to "admob",
        "inmobi"        to "inmobi",
        "snap"          to "snap",
        "moloco"        to "moloco",
    )

    /**
     * Returns pixel coordinates for a tap based on SDK + screen dimensions.
     * @param adClassName Full activity class name of the ad
     * @param screenW Screen width in pixels
     * @param screenH Screen height in pixels
     * @param isLandscape Whether screen is currently in landscape orientation
     */
    fun getCloseButtonPixels(
        adClassName: String,
        screenW: Int,
        screenH: Int,
        isLandscape: Boolean
    ): Pair<Float, Float> {
        val sdkKey = detectSdk(adClassName)
        val coords = if (isLandscape) {
            LANDSCAPE_COORDS[sdkKey] ?: LANDSCAPE_COORDS["generic"]!!
        } else {
            PORTRAIT_COORDS[sdkKey] ?: PORTRAIT_COORDS["generic"]!!
        }
        return Pair(screenW * coords.xRatio, screenH * coords.yRatio)
    }

    /**
     * Aggressive sweep — returns a list of 8 candidate tap points
     * covering all typical close button positions across all known SDKs.
     * Used in Phase 6 when nothing else works.
     */
    fun getSweepCoords(screenW: Int, screenH: Int): List<Pair<Float, Float>> {
        return listOf(
            // Top-right region (most common)
            Pair(screenW * 0.93f, screenH * 0.05f),
            Pair(screenW * 0.95f, screenH * 0.05f),
            Pair(screenW * 0.90f, screenH * 0.06f),
            Pair(screenW * 0.92f, screenH * 0.07f),
            // Top-left region (some SDKs)
            Pair(screenW * 0.07f, screenH * 0.05f),
            Pair(screenW * 0.05f, screenH * 0.07f),
            // Center-top (rare)
            Pair(screenW * 0.50f, screenH * 0.05f),
            // Bottom-center (endcard close sometimes)
            Pair(screenW * 0.50f, screenH * 0.92f),
        )
    }

    private fun detectSdk(className: String): String {
        val lower = className.lowercase()
        for ((fragment, sdk) in CLASS_TO_SDK) {
            if (lower.contains(fragment)) return sdk
        }
        return "generic"
    }
}

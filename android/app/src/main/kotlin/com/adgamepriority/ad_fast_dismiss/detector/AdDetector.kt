package com.adgamepriority.ad_fast_dismiss.detector

/**
 * AdDetector.kt
 * =============
 * Registry of 30+ known ad SDK activity class names.
 * Also handles rewarded ad detection — if rewarded, service goes DORMANT.
 *
 * Policy: We NEVER dismiss rewarded ads — user earns rewards from these.
 */
object AdDetector {

    // =========================================================
    // KNOWN AD ACTIVITY CLASS NAMES (30+ SDKs)
    // Only NON-REWARDED interstitial activity classes go here.
    // Rewarded classes MUST NOT be in this list — they are caught
    // by isRewardedAd() AFTER the isAdActivity() check.
    // =========================================================
    private val AD_ACTIVITY_CLASSES = setOf(
        // Google AdMob / GMS
        "com.google.android.gms.ads.AdActivity",
        "com.google.android.gms.ads.OutOfContextTestingActivity",

        // AppLovin / MAX
        "com.applovin.adview.AppLovinFullscreenActivity",
        "com.applovin.mediation.activities.MaxAdActivity",
        "com.applovin.mediation.hybridAds.MaxHybridMRecAdActivity",
        "com.applovin.sdk.AppLovinWebViewActivity",

        // Unity Ads
        "com.unity3d.services.ads.adunit.AdUnitActivity",
        "com.unity3d.services.ads.adunit.AdUnitTransparentActivity",
        "com.unity3d.services.ads.adunit.AdUnitTransparentSoftwareActivity",
        "com.unity3d.services.ads.adunit.AdUnitSoftwareActivity",

        // IronSource / LevelPlay
        "com.ironsource.sdk.controller.ControllerActivity",
        "com.ironsource.sdk.controller.InterstitialActivity",
        "com.ironsource.sdk.controller.OpenUrlActivity",

        // Vungle / Liftoff
        "com.vungle.warren.ui.VungleActivity",
        "com.vungle.warren.ui.VungleSplashActivity",

        // Meta / Facebook Audience Network
        "com.facebook.ads.AudienceNetworkActivity",
        "com.facebook.ads.internal.AdActivity",

        // Mintegral — ONLY non-rewarded interstitial classes
        // NOTE: MTGRewardVideoActivity is NOT listed here — it's rewarded and
        // caught by isRewardedAd() ("reward" in class name) → service stays dormant
        "com.mintegral.msdk.activity.MTGCommonActivity",
        "com.mintegral.msdk.interstitial.activity.MTGInterstitialVideoActivity",

        // Pangle / ByteDance / TikTok Ads (interstitial only)
        "com.pangle.cn.ad.AdSlotActivity",
        "com.bytedance.sdk.openadsdk.activity.TTInterstitialActivity",
        "com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity",
        // NOTE: TTRewardVideoActivity intentionally excluded — it's rewarded

        // InMobi
        "com.inmobi.ads.rendering.InMobiAdActivity",

        // Chartboost
        "com.chartboost.sdk.Chartboost\$CBActivity",
        "com.chartboost.sdk.CBImpressionActivity",

        // Tapjoy
        "com.tapjoy.TJAdUnitActivity",
        "com.tapjoy.mraid.view.ActionHandler",

        // Digital Turbine / Fyber / InnerActive
        "com.fyber.inneractive.sdk.ad.InneractiveAdActivity",
        "com.fyber.inneractive.sdk.ad.InneractiveFullscreenAdActivity",
        "com.digitalturbine.ignite.sdk.ui.IgniteActivity",

        // Ogury
        "io.ogury.ad.AdViewActivity",

        // Liftoff / Verizon Media
        "com.verizon.ads.edition.StandardEdition",

        // AdColony
        "com.jirbo.adcolony.AdColonyActivity",
        "com.jirbo.adcolony.AdColonyInterstitialActivity",

        // Snap Audience Network
        "com.snap.ads.SnapAdActivity",

        // Moloco
        "com.moloco.sdk.internal.ui.MolocoActivity",

        // Verve / PubNative
        "net.pubnative.lite.sdk.interstitial.activity.HyBidInterstitialActivity",

        // Generic WebView ad patterns (offerwall partners)
        "com.android.browser.BrowserActivity",

        // MoPub (now part of AppLovin MAX — still active in older game builds)
        "com.mopub.mobileads.MoPubActivity",
        "com.mopub.mobileads.MraidActivity",
        "com.mopub.mobileads.RewardedMraidActivity",

        // Criteo
        "com.criteo.publisher.interstitial.InterstitialActivity",

        // Yahoo / Flurry
        "com.flurry.android.FlurryFullscreenAdActivity",

        // Verizon Media / Oath
        "com.verizon.ads.vastcontroller.VastVideoControllerActivity",

        // Liftoff (new package alongside Vungle)
        "com.liftoff.sdk.ads.FullscreenActivity",
    )

    // =========================================================
    // REWARDED AD INDICATORS — NEVER DISMISS THESE
    // If ANY of these strings appear in the class name → DORMANT
    //
    // SAFETY: false negative (treating a rewarded as non-rewarded) = user loses reward
    // SAFETY: false positive (treating a non-rewarded as rewarded) = slower dismiss
    // We prefer false positives — better to miss a dismissal than steal a reward.
    //
    // FIX BUG-12: Added "offer" — catches OfferVideoActivity from newer SDKs
    // =========================================================
    private val REWARDED_INDICATORS = listOf(
        "reward", "incentiv", "bonus", "earn",
        "offerwall", "offer_wall", "offerWall",
        "offer",   // catches OfferVideoActivity, OfferWallActivity
        "survey",  // survey completion = reward
        "rewardvideo", "rewarded_video", "rewardedvideo",
        "rewardedinterstitial",  // Google AdMob rewarded interstitial
        "incentivized",          // older SDK naming
        "sponsored_reward",      // IronSource
        "s2s",                   // server-to-server reward callbacks
        "completion"             // some SDKs: CompletionActivity = reward complete
    )

    // =========================================================
    // GAME / LAUNCHER PACKAGES — NEVER TRIGGER ON THESE
    // =========================================================
    private val SAFE_GAME_ENGINES = setOf(
        "com.unity3d.player.UnityPlayerActivity",    // Unity game itself (not ad)
        "com.epicgames.ue4.GameActivity",             // Unreal Engine
        "com.godot.game.GodotApp",                   // Godot
    )

    /**
     * Returns true if this window event is a known ad activity.
     *
     * FIX BUG-4: Use equals() for exact matching (not contains()) against full class names.
     *   The old code used contains() which is wrong — if the event provides a full class name,
     *   it should match exactly. contains() is only valid if className is a *substring*, but
     *   events always provide the full class name. Using equals() with ignoreCase is correct.
     *
     * FIX BUG-5: Generic pattern is SKIPPED if packageName belongs to a watched game.
     *   Prevents false positive: "com.badlogic.gdx.SplashActivity" contains "ad" and "activity"
     *   but belongs to the user's game, not an ad SDK.
     *
     * @param className   The activity class name from the AccessibilityEvent
     * @param packageName The package name from the AccessibilityEvent
     * @param watchedPkgs The set of game packages the user is watching (from SharedPrefs)
     */
    fun isAdActivity(
        className: String?,
        packageName: String? = null,
        watchedPkgs: Set<String> = emptySet()
    ): Boolean {
        if (className.isNullOrBlank()) return false

        // FIX BUG-4: Exact match — event provides fully-qualified class name
        if (AD_ACTIVITY_CLASSES.any { className.equals(it, ignoreCase = true) }) return true

        // FIX BUG-5: Skip generic pattern for known game packages (prevents false positives)
        if (packageName != null && watchedPkgs.contains(packageName)) return false

        // Safe game engine check — these are NEVER ads
        if (SAFE_GAME_ENGINES.any { className.equals(it, ignoreCase = true) }) return false

        // Generic pattern matching for unknown/new SDKs
        return isGenericAdPattern(className)
    }

    /**
     * Generic pattern matching for unknown/new SDKs.
     * Matches class names that look like ad activities.
     * Only called when packageName is NOT a watched game package.
     *
     * Requires ALL THREE:
     *   1. Contains "ad" or "ads" — indicates it is ad-related
     *   2. Contains a UI type word — it's a displayed UI element
     *   3. Contains a format/placement word — it's the specific ad format we care about
     *
     * FIX: Removed "native" from format words — "native" matches too broadly
     * (e.g., com.company.native.SomeActivity). Native ads are inline anyway,
     * not full-screen Activities, so they don't need dismissal.
     */
    private fun isGenericAdPattern(className: String): Boolean {
        val lower = className.lowercase()
        return (lower.contains("ad") || lower.contains("ads"))
            && (lower.contains("activity") || lower.contains("dialog") || lower.contains("view"))
            && (lower.contains("interstitial") || lower.contains("fullscreen")
                || lower.contains("banner") || lower.contains("video")
                || lower.contains("splash"))
    }

    /**
     * Returns true if this is a REWARDED ad — service must NOT dismiss it.
     * User earns in-game rewards / offerwall points from these.
     */
    fun isRewardedAd(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val lower = className.lowercase()
        return REWARDED_INDICATORS.any { lower.contains(it) }
    }

    /**
     * Returns true if the class is a known game engine activity (not an ad).
     */
    fun isSafeGameActivity(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return SAFE_GAME_ENGINES.any { className.equals(it, ignoreCase = true) }
    }
}

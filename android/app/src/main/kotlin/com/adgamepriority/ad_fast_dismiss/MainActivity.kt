package com.adgamepriority.ad_fast_dismiss

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.adgamepriority.ad_fast_dismiss.service.GamePriorityService
import com.adgamepriority.ad_fast_dismiss.util.StatsHelper
import com.adgamepriority.ad_fast_dismiss.engine.CoordLearner

/**
 * MainActivity.kt
 * ===============
 * Flutter ↔ Kotlin bridge via MethodChannel + EventChannel.
 *
 * Flutter UI can:
 * - Check if Accessibility Service is enabled
 * - Open Accessibility Settings (for user to enable the service)
 * - Toggle service on/off (enabled state in SharedPreferences)
 * - Get stats (ads dismissed, time saved)
 * - Set watched game packages
 * - Clear learned coordinates
 * - Clear stats
 */
class MainActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL = "com.adgamepriority.ad_fast_dismiss/service"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var statsHelper: StatsHelper
    private lateinit var coordLearner: CoordLearner

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        prefs = getSharedPreferences("afd_prefs", Context.MODE_PRIVATE)
        statsHelper = StatsHelper(this)
        coordLearner = CoordLearner(this)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {

                    // Check if our AccessibilityService is enabled in Android Settings
                    "isAccessibilityEnabled" -> {
                        result.success(isAccessibilityServiceEnabled())
                    }

                    // Open Android Accessibility Settings so user can enable our service
                    "openAccessibilitySettings" -> {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        result.success(true)
                    }

                    // Toggle service active/inactive
                    "setServiceEnabled" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: true
                        prefs.edit()
                            .putBoolean(GamePriorityService.PREF_ENABLED, enabled)
                            .apply()
                        result.success(true)
                    }

                    // Get current enabled state
                    "isServiceEnabled" -> {
                        result.success(prefs.getBoolean(GamePriorityService.PREF_ENABLED, true))
                    }

                    // Get stats for display in Flutter UI
                    "getStats" -> {
                        val statsMap = mapOf(
                            "totalAds"       to statsHelper.getTotalAds(),
                            "totalSecsSaved" to statsHelper.getTotalSecsSaved(),
                            "todayAds"       to statsHelper.getTodayAds(),
                            "todaySecsSaved" to statsHelper.getTodaySecsSaved(),
                        )
                        result.success(statsMap)
                    }

                    // Get phase breakdown — which cascade phase is most effective
                    "getPhaseBreakdown" -> {
                        val breakdown = statsHelper.getPhaseBreakdown()
                            .map { (k, v) -> k.toString() to v }
                            .toMap()
                        result.success(breakdown)
                    }

                    // Get all learned coordinates (for advanced stats / debug)
                    "getLearnedCoords" -> {
                        val entries = coordLearner.getAllEntries()
                        val mapped = entries.map { (key, coord) ->
                            mapOf(
                                "key"          to key,
                                "xRatio"       to coord.xRatio.toDouble(),
                                "yRatio"       to coord.yRatio.toDouble(),
                                "successCount" to coord.successCount,
                                "highConf"     to coord.isHighConfidence
                            )
                        }
                        result.success(mapped)
                    }

                    // Set game packages to watch
                    "setGamePackages" -> {
                        val packages = call.argument<List<String>>("packages") ?: emptyList()
                        prefs.edit()
                            .putStringSet(GamePriorityService.PREF_GAME_PACKAGES, packages.toSet())
                            .apply()
                        result.success(true)
                    }

                    // Get current game packages
                    "getGamePackages" -> {
                        val packages = prefs.getStringSet(
                            GamePriorityService.PREF_GAME_PACKAGES, emptySet()
                        )?.toList() ?: emptyList<String>()
                        result.success(packages)
                    }

                    // Clear all stats
                    "clearStats" -> {
                        statsHelper.clearAll()
                        result.success(true)
                    }

                    // Clear all learned coordinates (troubleshooting)
                    "clearLearnedCoords" -> {
                        coordLearner.clearAll()
                        result.success(true)
                    }

                    // Returns list of installed user apps (name + pkg) for game picker
                    // FIX C-5: Use PackageManager.MATCH_ALL — required on Android 11+ (API 30+)
                    // Without this flag, queryIntentActivities returns empty on modern devices.
                    "getInstalledApps" -> {
                        val pm = packageManager
                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                        @Suppress("DEPRECATION")
                        val apps = pm.queryIntentActivities(
                            intent, android.content.pm.PackageManager.MATCH_ALL
                        ).mapNotNull { ri ->
                                try {
                                    val appInfo = ri.activityInfo.applicationInfo
                                    // Skip system apps (FLAG_SYSTEM set)
                                    if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
                                    mapOf(
                                        "name" to pm.getApplicationLabel(appInfo).toString(),
                                        "pkg"  to appInfo.packageName
                                    )
                                } catch (e: Exception) { null }
                            }
                            .sortedBy { it["name"]?.lowercase() }
                        result.success(apps)
                    }

                    // Check if overlay permission is granted (for SYSTEM_ALERT_WINDOW)
                    "canDrawOverlays" -> {
                        val canDraw = android.provider.Settings.canDrawOverlays(this)
                        result.success(canDraw)
                    }

                    // Open overlay settings
                    "openOverlaySettings" -> {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        result.success(true)
                    }

                    // FIX BUG-8: Request POST_NOTIFICATIONS at runtime (Android 13+)
                    // platform_bridge.dart calls this — was missing Kotlin handler
                    "requestNotificationPermission" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                100
                            )
                        }
                        result.success(true)
                    }

                    // FIX BUG-8: Return app version string
                    // platform_bridge.dart calls this — was missing Kotlin handler
                    "getAppVersion" -> {
                        try {
                            val version = packageManager
                                .getPackageInfo(packageName, 0).versionName
                            result.success(version ?: "1.0.0")
                        } catch (e: Exception) {
                            result.success("1.0.0")
                        }
                    }

                    else -> result.notImplemented()
                }
            }
    }

    /**
     * Checks if our GamePriorityService is currently enabled in Android Settings.
     * Uses the standard Android accessibility service check mechanism.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "${packageName}/${GamePriorityService::class.java.canonicalName}"
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(serviceId, ignoreCase = true)) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}

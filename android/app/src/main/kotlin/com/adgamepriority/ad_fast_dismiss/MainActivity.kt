package com.adgamepriority.ad_fast_dismiss

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.adgamepriority.ad_fast_dismiss.service.GamePriorityService
import com.adgamepriority.ad_fast_dismiss.util.StatsHelper

/**
 * MainActivity.kt
 * ===============
 * Flutter ↔ Kotlin bridge via MethodChannel.
 *
 * 100% Google Play Protect Safe:
 * Uses standard Overlay Permission + Usage Access instead of restricted Accessibility.
 */
class MainActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL = "com.adgamepriority.ad_fast_dismiss/service"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var statsHelper: StatsHelper

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        prefs = getSharedPreferences("afd_prefs", Context.MODE_PRIVATE)
        statsHelper = StatsHelper(this)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {

                    // Overlay permission check (SYSTEM_ALERT_WINDOW)
                    "canDrawOverlays" -> {
                        result.success(Settings.canDrawOverlays(this))
                    }

                    // Open overlay settings
                    "openOverlaySettings" -> {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        startActivity(intent)
                        result.success(true)
                    }

                    // Usage Access permission check (PACKAGE_USAGE_STATS)
                    "hasUsageAccess" -> {
                        result.success(checkUsageAccess())
                    }

                    // Open Usage Access settings
                    "openUsageAccessSettings" -> {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        result.success(true)
                    }

                    // Start/Update GamePriorityService
                    "startService" -> {
                        val serviceIntent = Intent(this, GamePriorityService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        GamePriorityService.instance?.updateShieldState()
                        result.success(true)
                    }

                    // Stop GamePriorityService
                    "stopService" -> {
                        val serviceIntent = Intent(this, GamePriorityService::class.java)
                        stopService(serviceIntent)
                        result.success(true)
                    }

                    // Toggle service active/inactive in SharedPreferences
                    "setServiceEnabled" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: true
                        prefs.edit()
                            .putBoolean(GamePriorityService.PREF_ENABLED, enabled)
                            .apply()
                        if (enabled) {
                            val serviceIntent = Intent(this, GamePriorityService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                        }
                        GamePriorityService.instance?.updateShieldState()
                        result.success(true)
                    }

                    // Get current enabled state
                    "isServiceEnabled" -> {
                        result.success(prefs.getBoolean(GamePriorityService.PREF_ENABLED, true))
                    }

                    // Check if foreground service is actively running
                    "isServiceRunning" -> {
                        result.success(GamePriorityService.instance != null)
                    }

                    // Backward-compat for legacy Flutter call
                    "isAccessibilityEnabled" -> {
                        // In this clean version, full functionality is active when overlay is granted
                        result.success(Settings.canDrawOverlays(this))
                    }

                    "openAccessibilitySettings" -> {
                        // Redirect to overlay permission
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        startActivity(intent)
                        result.success(true)
                    }

                    // Stats
                    "getStats" -> {
                        val statsMap = mapOf(
                            "totalAds"       to statsHelper.getTotalAds(),
                            "totalSecsSaved" to statsHelper.getTotalSecsSaved(),
                            "todayAds"       to statsHelper.getTodayAds(),
                            "todaySecsSaved" to statsHelper.getTodaySecsSaved(),
                        )
                        result.success(statsMap)
                    }

                    "getPhaseBreakdown" -> {
                        val breakdown = statsHelper.getPhaseBreakdown()
                            .map { (k, v) -> k.toString() to v }
                            .toMap()
                        result.success(breakdown)
                    }

                    "clearStats" -> {
                        statsHelper.clearAll()
                        result.success(true)
                    }

                    "clearLearnedCoords" -> {
                        result.success(true)
                    }

                    // Set game packages to watch
                    "setGamePackages" -> {
                        val packages = call.argument<List<String>>("packages") ?: emptyList()
                        prefs.edit()
                            .putStringSet(GamePriorityService.PREF_GAME_PACKAGES, packages.toSet())
                            .apply()
                        GamePriorityService.instance?.updateShieldState()
                        result.success(true)
                    }

                    // Get current game packages
                    "getGamePackages" -> {
                        val packages = prefs.getStringSet(
                            GamePriorityService.PREF_GAME_PACKAGES, emptySet()
                        )?.toList() ?: emptyList<String>()
                        result.success(packages)
                    }

                    // Installed user apps
                    "getInstalledApps" -> {
                        val pm = packageManager
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                        @Suppress("DEPRECATION")
                        val apps = pm.queryIntentActivities(
                            intent, android.content.pm.PackageManager.MATCH_ALL
                        ).mapNotNull { ri ->
                            try {
                                val appInfo = ri.activityInfo.applicationInfo
                                if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
                                mapOf(
                                    "name" to pm.getApplicationLabel(appInfo).toString(),
                                    "pkg"  to appInfo.packageName
                                )
                            } catch (e: Exception) { null }
                        }.sortedBy { it["name"]?.lowercase() }
                        result.success(apps)
                    }

                    "requestNotificationPermission" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                100
                            )
                        }
                        result.success(true)
                    }

                    "getAppVersion" -> {
                        try {
                            val version = packageManager.getPackageInfo(packageName, 0).versionName
                            result.success(version ?: "1.3.0")
                        } catch (e: Exception) {
                            result.success("1.3.0")
                        }
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun checkUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

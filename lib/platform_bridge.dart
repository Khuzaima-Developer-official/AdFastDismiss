import 'package:flutter/services.dart';

/// PlatformBridge — MethodChannel bridge to Kotlin layer.
/// All Android system calls go through this single channel.
class PlatformBridge {
  static const _channel =
      MethodChannel('com.adgamepriority.ad_fast_dismiss/service');

  /// Check if the Accessibility Service is enabled in Android Settings.
  static Future<bool> isAccessibilityEnabled() async {
    try {
      return await _channel.invokeMethod<bool>('isAccessibilityEnabled') ?? false;
    } catch (_) { return false; }
  }

  /// Opens Android Accessibility Settings so user can enable the service.
  static Future<void> openAccessibilitySettings() async {
    try { await _channel.invokeMethod('openAccessibilitySettings'); } catch (_) {}
  }

  /// Toggles the service active/inactive.
  static Future<void> setServiceEnabled(bool enabled) async {
    try { await _channel.invokeMethod('setServiceEnabled', {'enabled': enabled}); } catch (_) {}
  }

  /// Gets current service enabled state from SharedPreferences.
  static Future<bool> isServiceEnabled() async {
    try {
      return await _channel.invokeMethod<bool>('isServiceEnabled') ?? true;
    } catch (_) { return true; }
  }

  /// Fetches stats map: totalAds, totalSecsSaved, todayAds, todaySecsSaved.
  static Future<Map<String, int>> getStats() async {
    try {
      final raw = await _channel.invokeMethod<Map>('getStats') ?? {};
      return raw.map((k, v) => MapEntry(k.toString(), (v as int?) ?? 0));
    } catch (_) { return {}; }
  }

  /// Sets the list of game package names to watch.
  static Future<void> setGamePackages(List<String> packages) async {
    try { await _channel.invokeMethod('setGamePackages', {'packages': packages}); } catch (_) {}
  }

  /// Gets the current list of watched game packages.
  static Future<List<String>> getGamePackages() async {
    try {
      final result = await _channel.invokeMethod<List>('getGamePackages') ?? [];
      return result.map((e) => e.toString()).toList();
    } catch (_) { return []; }
  }

  /// Clears all stored statistics.
  static Future<void> clearStats() async {
    try { await _channel.invokeMethod('clearStats'); } catch (_) {}
  }

  /// Clears all learned coordinates (troubleshooting — resets CoordLearner memory).
  static Future<void> clearLearnedCoords() async {
    try { await _channel.invokeMethod('clearLearnedCoords'); } catch (_) {}
  }

  /// Returns list of installed user apps as [{name: String, pkg: String}].
  /// Used by the game picker in Settings.
  static Future<List<Map<String, String>>> getInstalledApps() async {
    try {
      final raw = await _channel.invokeMethod<List>('getInstalledApps') ?? [];
      return raw.map((e) {
        final map = Map<String, dynamic>.from(e as Map);
        return {
          'name': map['name']?.toString() ?? '',
          'pkg': map['pkg']?.toString() ?? '',
        };
      }).toList();
    } catch (_) { return []; }
  }

  /// Checks if overlay permission (SYSTEM_ALERT_WINDOW) is granted.
  static Future<bool> canDrawOverlays() async {
    try {
      return await _channel.invokeMethod<bool>('canDrawOverlays') ?? false;
    } catch (_) { return false; }
  }

  /// Opens overlay permission settings.
  static Future<void> openOverlaySettings() async {
    try { await _channel.invokeMethod('openOverlaySettings'); } catch (_) {}
  }

  /// Requests POST_NOTIFICATIONS permission (Android 13+).
  static Future<bool> requestNotificationPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestNotificationPermission') ?? false;
    } catch (_) { return false; }
  }

  /// Gets the current app version string.
  static Future<String> getAppVersion() async {
    try {
      return await _channel.invokeMethod<String>('getAppVersion') ?? '1.0.0';
    } catch (_) { return '1.0.0'; }
  }

  /// Gets phase breakdown — which cascade phase is most effective.
  /// Returns e.g. {'3': 12, '4': 7, '5': 2} meaning Phase 3 succeeded 12 times.
  static Future<Map<String, int>> getPhaseBreakdown() async {
    try {
      final raw = await _channel.invokeMethod<Map>('getPhaseBreakdown') ?? {};
      return raw.map((k, v) => MapEntry(k.toString(), (v as int?) ?? 0));
    } catch (_) { return {}; }
  }

  /// Gets all learned coordinates from CoordLearner.
  /// Returns list of maps with keys: key, xRatio, yRatio, successCount, highConf.
  static Future<List<Map<String, dynamic>>> getLearnedCoords() async {
    try {
      final raw = await _channel.invokeMethod<List>('getLearnedCoords') ?? [];
      return raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    } catch (_) { return []; }
  }
}

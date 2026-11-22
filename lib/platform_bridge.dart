import 'package:flutter/services.dart';

/// PlatformBridge — MethodChannel bridge to Kotlin layer.
/// 100% Google Play Protect Safe (No Accessibility Dependency).
class PlatformBridge {
  static const _channel =
      MethodChannel('com.adgamepriority.ad_fast_dismiss/service');

  /// Check if Overlay permission (Display over other apps) is granted.
  static Future<bool> canDrawOverlays() async {
    try {
      return await _channel.invokeMethod<bool>('canDrawOverlays') ?? false;
    } catch (_) { return false; }
  }

  /// Opens Android settings to grant Overlay permission.
  static Future<void> openOverlaySettings() async {
    try { await _channel.invokeMethod('openOverlaySettings'); } catch (_) {}
  }

  /// Check if Usage Access permission is granted.
  static Future<bool> hasUsageAccess() async {
    try {
      return await _channel.invokeMethod<bool>('hasUsageAccess') ?? false;
    } catch (_) { return false; }
  }

  /// Opens Android settings to grant Usage Access.
  static Future<void> openUsageAccessSettings() async {
    try { await _channel.invokeMethod('openUsageAccessSettings'); } catch (_) {}
  }

  /// Starts the Foreground GamePriorityService.
  static Future<void> startService() async {
    try { await _channel.invokeMethod('startService'); } catch (_) {}
  }

  /// Stops the Foreground GamePriorityService.
  static Future<void> stopService() async {
    try { await _channel.invokeMethod('stopService'); } catch (_) {}
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

  /// Backward-compat: returns true if overlay is granted
  static Future<bool> isAccessibilityEnabled() async {
    return await canDrawOverlays();
  }

  static Future<void> openAccessibilitySettings() async {
    await openOverlaySettings();
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

  /// Clears all learned coordinates.
  static Future<void> clearLearnedCoords() async {
    try { await _channel.invokeMethod('clearLearnedCoords'); } catch (_) {}
  }

  /// Returns list of installed user apps as [{name: String, pkg: String}].
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

  /// Requests POST_NOTIFICATIONS permission (Android 13+).
  static Future<bool> requestNotificationPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestNotificationPermission') ?? false;
    } catch (_) { return false; }
  }

  /// Gets the current app version string.
  static Future<String> getAppVersion() async {
    try {
      return await _channel.invokeMethod<String>('getAppVersion') ?? '1.3.0';
    } catch (_) { return '1.3.0'; }
  }

  /// Gets phase breakdown.
  static Future<Map<String, int>> getPhaseBreakdown() async {
    try {
      final raw = await _channel.invokeMethod<Map>('getPhaseBreakdown') ?? {};
      return raw.map((k, v) => MapEntry(k.toString(), (v as int?) ?? 0));
    } catch (_) { return {}; }
  }
}

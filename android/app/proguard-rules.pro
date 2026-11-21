# =====================================================
# ProGuard / R8 Rules — AdFastDismiss
# =====================================================
# Without these rules, R8 (release minifier) will strip
# AccessibilityService, BroadcastReceivers, and other
# Android components that are not referenced in Dart code.
# =====================================================

# Keep ALL AccessibilityService subclasses (critical — without this, service is stripped)
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Keep all Service subclasses
-keep class * extends android.app.Service { *; }

# Keep our entire package
-keep class com.adgamepriority.ad_fast_dismiss.** { *; }

# Keep Flutter engine (already handled by Flutter plugin, but explicit is safer)
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# Keep androidx.core (NotificationCompat etc.)
-keep class androidx.core.app.** { *; }
-keep class androidx.core.content.** { *; }

# Keep Android accessibility node classes
-keep class android.view.accessibility.** { *; }
-keep class android.accessibilityservice.** { *; }

# Keep GestureDescription (used in GestureEngine)
-keep class android.accessibilityservice.GestureDescription { *; }
-keep class android.accessibilityservice.GestureDescription$StrokeDescription { *; }

# Prevent stripping of MediaProjection classes
-keep class android.media.projection.** { *; }
-keep class android.media.ImageReader { *; }
-keep class android.media.Image { *; }

# Keep SharedPreferences (used in StatsHelper, CoordLearner, settings)
-keep class android.content.SharedPreferences { *; }

# Remove logging in release (saves APK size, hides internal logs)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Ignore Play Core deferred components warnings in Flutter
-dontwarn com.google.android.play.core.**

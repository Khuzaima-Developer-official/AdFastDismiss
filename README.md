# ⚡ PlayBlitz (AdFastDismiss)
> **Ultra-Fast, Sub-1-Second Ad Interruption Minimizer & Gaming Assistant**
> *Built with Flutter (Modern UI) + Kotlin (High-Performance Accessibility Engine)*

---

## 🎮 What is PlayBlitz?

PlayBlitz is designed for mobile gamers and offerwall players who want **uninterrupted gameplay flow**. When full-screen non-rewarded interstitial ads interrupt every 10–15 seconds, PlayBlitz uses a **Sub-1-Second Blitz Architecture** to free your screen in less than 0.8 seconds.

### 🚀 Key Features

* **⚡ Sub-1-Second Blitz Dismissal:**
  * Strikes at `t = 30ms` with hardware back gesture before ad JavaScript locks the key.
  * Pre-emptively taps close coordinates on frame 1 (`t = 100ms`).
* **📺 Snap-to-Mini-Bar (Unskippable Ads):**
  * For stubborn unskippable video ads, snaps the ad to a tiny **15% top bar** within 0.18s.
  * Frees **85% of your screen** so you can continue playing your game immediately!
  * Ad video runs live in the top strip to prevent timer freezes.
* **🔄 Auto-Restore Full Screen:**
  * The instant the ad completes or dismisses, PlayBlitz automatically expands your game back to **100% full screen**.
* **🛡️ 100% Policy-Safe & Zero Violations:**
  * **Zero Network / VPN Tampering:** No DNS blocking, no proxy, no TLS interception.
  * **Rewarded Ads Untouched:** Rewarded ads are never skipped — your offerwall points and in-game gems are always protected.
  * **Zero Click Fraud:** "Install", "Download", and "Play Now" buttons are strictly blacklisted.
* **🧠 Adaptive AI Memory (CoordLearner):**
  * Learns close-button coordinates per game and ad SDK over time for instant dismissals.
* **📱 OEM Fallback:**
  * If split-screen is unsupported on certain OEM ROMs, instantly reorders game to the front via `FLAG_ACTIVITY_REORDER_TO_FRONT`.

---

## 🏗️ Architecture

```
PlayBlitz/
├── lib/ (Flutter UI Layer)
│   ├── main.dart
│   ├── platform_bridge.dart          # MethodChannel bridge
│   └── screens/
│       ├── home_screen.dart          # Main toggle, status & quick setup
│       ├── settings_screen.dart      # Game picker from installed apps
│       └── stats_screen.dart         # Ads dismissed, time saved & phase breakdown
│
└── android/app/src/main/kotlin/com/adgamepriority/ad_fast_dismiss/
    ├── MainActivity.kt               # MethodChannel handler & installed apps query
    ├── service/
    │   ├── GamePriorityService.kt    # Core AccessibilityService & Blitz Cascade
    │   └── PersistentForegroundService.kt
    ├── detector/
    │   └── AdDetector.kt             # 30+ SDK signatures & Rewarded ad guard
    ├── engine/
    │   ├── WindowShiftEngine.kt      # Split-screen & divider drag engine
    │   ├── GestureEngine.kt          # System edge-swipe & back dispatcher
    │   ├── NodeScannerEngine.kt      # BFS AccessibilityNode tree scanner
    │   ├── CoordDatabase.kt          # Multi-SDK coordinate database
    │   └── CoordLearner.kt           # Adaptive coordinate memory
    └── util/
        ├── NotificationHelper.kt     # Return-to-game persistent alerts
        └── StatsHelper.kt            # Lifetime & daily savings analytics
```

---

## 🛠️ Build & Installation

### Requirements:
* Flutter SDK 3.10+
* Android SDK (minSdk: 24, targetSdk: 34)
* Java 17

### Build Commands:
```bash
# Analyze code
flutter analyze

# Build debug APK
flutter build apk --debug

# Build optimized release APK
flutter build apk --release
```

Generated APK:
`build/app/outputs/flutter-apk/app-debug.apk`

---

## 📜 Policy Disclaimer
PlayBlitz operates strictly within standard Android AccessibilityService and WindowManager APIs. It does not modify network traffic, bypass payment gateways, alter advertising attribution (MMP pings), or tamper with rewarded advertisements.

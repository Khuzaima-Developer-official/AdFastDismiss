import 'dart:async';
import 'package:flutter/material.dart';
import '../platform_bridge.dart';
import 'settings_screen.dart';
import 'stats_screen.dart';

/// HomeScreen — Main screen of AdFastDismiss.
/// Shows service toggle, permission status, and quick stats.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  bool _accessibilityEnabled = false;
  bool _serviceEnabled = true;
  int _todayAds = 0;
  int _todaySecsSaved = 0;
  int _gameCount = 0;        // FIX M-8: no games = not fully ready
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadState();
    // Refresh every 3 seconds to pick up new stats
    _refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) => _loadState());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _refreshTimer?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _loadState();
      // Restart timer when app comes back to foreground
      _refreshTimer ??= Timer.periodic(const Duration(seconds: 3), (_) => _loadState());
    } else if (state == AppLifecycleState.paused) {
      // FIX: Cancel timer when app goes to background — saves battery
      _refreshTimer?.cancel();
      _refreshTimer = null;
    }
  }

  Future<void> _loadState() async {
    final accessibility = await PlatformBridge.isAccessibilityEnabled();
    final enabled = await PlatformBridge.isServiceEnabled();
    final stats = await PlatformBridge.getStats();
    final packages = await PlatformBridge.getGamePackages();

    if (mounted) {
      setState(() {
        _accessibilityEnabled = accessibility;
        _serviceEnabled = enabled;
        _todayAds = stats['todayAds'] ?? 0;
        _todaySecsSaved = stats['todaySecsSaved'] ?? 0;
        _gameCount = packages.length; // FIX M-8
      });
    }
  }

  String _formatTime(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    if (m > 0) return '${m}m ${s}s';
    return '${s}s';
  }

  @override
  Widget build(BuildContext context) {
    // FIX M-8: allOk requires accessibility ON + service ON + at least 1 game added
    final allOk = _accessibilityEnabled && _serviceEnabled && _gameCount > 0;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: const Text(
          '⚡ PlayBlitz',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 20),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.bar_chart_rounded),
            tooltip: 'Stats',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const StatsScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings_rounded),
            tooltip: 'Settings',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ).then((_) => _loadState()),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ─── STATUS BANNER ───────────────────────────────
            _StatusBanner(isActive: allOk, accessibilityEnabled: _accessibilityEnabled),
            const SizedBox(height: 24),

            // ─── ONBOARDING STEPS (shown until fully set up) ─
            if (!allOk) ...[
              _OnboardingCard(
                accessibilityEnabled: _accessibilityEnabled,
                gameCount: _gameCount,
                onEnableAccessibility: () async {
                  await PlatformBridge.openAccessibilitySettings();
                },
                onGoToSettings: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const SettingsScreen()),
                ).then((_) => _loadState()),
              ),
              const SizedBox(height: 24),
            ],

            // ─── BIG TOGGLE ──────────────────────────────────
            _BigToggle(
              enabled: _serviceEnabled,
              onChanged: (val) async {
                await PlatformBridge.setServiceEnabled(val);
                setState(() => _serviceEnabled = val);
              },
            ),
            const SizedBox(height: 24),

            // ─── TODAY STATS ─────────────────────────────────
            Row(children: [
              Expanded(
                child: _StatCard(
                  icon: Icons.shield_rounded,
                  label: 'Ads Dismissed',
                  value: '$_todayAds',
                  sub: 'today',
                  color: Colors.greenAccent,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _StatCard(
                  icon: Icons.timer_rounded,
                  label: 'Time Saved',
                  value: _formatTime(_todaySecsSaved),
                  sub: 'today',
                  color: Colors.cyanAccent,
                ),
              ),
            ]),
            const SizedBox(height: 24),

            // ─── PERMISSION CHECKLIST ─────────────────────────
            const Text(
              'Setup',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: Colors.white70,
              ),
            ),
            const SizedBox(height: 10),
            _PermissionTile(
              label: 'Accessibility Service',
              subtitle: _accessibilityEnabled
                  ? 'Enabled — service is running'
                  : 'Required — tap to enable in Settings',
              granted: _accessibilityEnabled,
              onTap: _accessibilityEnabled
                  ? null
                  : () async {
                      await PlatformBridge.openAccessibilitySettings();
                    },
            ),
            const SizedBox(height: 24),

            // ─── HOW IT WORKS ────────────────────────────────
            const _HowItWorksCard(),
          ],
        ),
      ),
    );
  }
}

// ============================================================
// WIDGETS
// ============================================================

class _StatusBanner extends StatelessWidget {
  final bool isActive;
  final bool accessibilityEnabled;
  const _StatusBanner({required this.isActive, required this.accessibilityEnabled});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: isActive
            ? Colors.green.withValues(alpha: 0.15)
            : Colors.orange.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isActive ? Colors.greenAccent : Colors.orangeAccent,
          width: 1.2,
        ),
      ),
      child: Row(children: [
        Icon(
          isActive ? Icons.check_circle_rounded : Icons.warning_rounded,
          color: isActive ? Colors.greenAccent : Colors.orangeAccent,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            isActive
                ? 'Active — Ads will be dismissed fast!'
                : 'Setup needed — Accessibility not enabled',
            style: TextStyle(
              color: isActive ? Colors.greenAccent : Colors.orangeAccent,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ]),
    );
  }
}

class _BigToggle extends StatelessWidget {
  final bool enabled;
  final ValueChanged<bool> onChanged;
  const _BigToggle({required this.enabled, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => onChanged(!enabled),
      child: Container(
        padding: const EdgeInsets.all(28),
        decoration: BoxDecoration(
          color: enabled
              ? Colors.green.withValues(alpha: 0.2)
              : Colors.grey.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: enabled ? Colors.greenAccent : Colors.grey,
            width: 1.5,
          ),
        ),
        child: Column(children: [
          Icon(
            enabled ? Icons.play_circle_filled_rounded : Icons.pause_circle_rounded,
            size: 64,
            color: enabled ? Colors.greenAccent : Colors.grey,
          ),
          const SizedBox(height: 12),
          Text(
            enabled ? 'Service: ON' : 'Service: OFF',
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: enabled ? Colors.greenAccent : Colors.grey,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            enabled ? 'Tap to pause' : 'Tap to activate',
            style: const TextStyle(color: Colors.white38, fontSize: 13),
          ),
        ]),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final String sub;
  final Color color;
  const _StatCard({
    required this.icon,
    required this.label,
    required this.value,
    required this.sub,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: color, size: 22),
        const SizedBox(height: 8),
        Text(value,
            style: TextStyle(
                fontSize: 28, fontWeight: FontWeight.bold, color: color)),
        Text(label, style: const TextStyle(color: Colors.white60, fontSize: 12)),
        Text(sub, style: const TextStyle(color: Colors.white38, fontSize: 11)),
      ]),
    );
  }
}

class _PermissionTile extends StatelessWidget {
  final String label;
  final String subtitle;
  final bool granted;
  final VoidCallback? onTap;
  const _PermissionTile({
    required this.label,
    required this.subtitle,
    required this.granted,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      color: const Color(0xFF1A1A2E),
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: Icon(
          granted ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
          color: granted ? Colors.greenAccent : Colors.orangeAccent,
        ),
        title: Text(label,
            style: const TextStyle(
                fontWeight: FontWeight.w600, color: Colors.white)),
        subtitle: Text(subtitle,
            style: const TextStyle(color: Colors.white54, fontSize: 12)),
        trailing: granted
            ? null
            : const Icon(Icons.arrow_forward_ios_rounded,
                size: 14, color: Colors.white38),
        onTap: onTap,
      ),
    );
  }
}

class _OnboardingCard extends StatelessWidget {
  final bool accessibilityEnabled;
  final int gameCount;
  final VoidCallback onEnableAccessibility;
  final VoidCallback onGoToSettings;

  const _OnboardingCard({
    required this.accessibilityEnabled,
    required this.gameCount,
    required this.onEnableAccessibility,
    required this.onGoToSettings,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.blue.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.blueAccent.withValues(alpha: 0.4)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Row(children: [
          Icon(Icons.rocket_launch_rounded, color: Colors.blueAccent, size: 20),
          SizedBox(width: 8),
          Text('Quick Setup — 2 Steps',
              style: TextStyle(
                  fontWeight: FontWeight.bold,
                  color: Colors.blueAccent,
                  fontSize: 15)),
        ]),
        const SizedBox(height: 14),
        _OnboardingStep(
          step: 1,
          done: accessibilityEnabled,
          title: 'Enable Accessibility Service',
          description: 'Allows AdFastDismiss to detect and dismiss ads.',
          actionLabel: 'Open Settings',
          onAction: onEnableAccessibility,
        ),
        const SizedBox(height: 10),
        _OnboardingStep(
          step: 2,
          done: gameCount > 0,
          title: 'Add Your Games',
          description: gameCount > 0
              ? '$gameCount game(s) added ✓'
              : 'Tap Settings → pick games from your installed apps.',
          actionLabel: 'Go to Settings',
          onAction: onGoToSettings,
        ),
      ]),
    );
  }
}

class _OnboardingStep extends StatelessWidget {
  final int step;
  final bool done;
  final String title;
  final String description;
  final String actionLabel;
  final VoidCallback onAction;

  const _OnboardingStep({
    required this.step, required this.done, required this.title,
    required this.description, required this.actionLabel, required this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(
        width: 28, height: 28,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: done ? Colors.greenAccent : Colors.blueAccent,
        ),
        child: Center(
          child: done
              ? const Icon(Icons.check, color: Colors.black, size: 16)
              : Text('$step',
                  style: const TextStyle(
                      color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
        ),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title,
              style: TextStyle(
                  color: done ? Colors.white54 : Colors.white,
                  fontWeight: FontWeight.w600, fontSize: 13,
                  decoration: done ? TextDecoration.lineThrough : null)),
          const SizedBox(height: 2),
          Text(description,
              style: const TextStyle(color: Colors.white38, fontSize: 12)),
          if (!done) ...[
            const SizedBox(height: 6),
            GestureDetector(
              onTap: onAction,
              child: Text(actionLabel,
                  style: const TextStyle(
                      color: Colors.blueAccent,
                      fontWeight: FontWeight.bold, fontSize: 12)),
            ),
          ],
        ]),
      ),
    ]);
  }
}

class _HowItWorksCard extends StatelessWidget {
  const _HowItWorksCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('How it works',
            style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 15,
                color: Colors.white)),
        const SizedBox(height: 10),
        ...[
          '🔍 Detects ads in your games automatically',
          '🪟 Tries to shift ad to small window (play while ad runs)',
          '⏩ Taps Skip/Close button the moment it appears',
          '🤖 Learns your games\' ad patterns over time',
          '✅ Never touches rewarded ads (you keep your rewards)',
          '🔒 Zero network/attribution modification',
        ].map((text) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 3),
              child: Text(text,
                  style: const TextStyle(
                      color: Colors.white70, fontSize: 13)),
            )),
      ]),
    );
  }
}

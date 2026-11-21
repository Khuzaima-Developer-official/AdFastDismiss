import 'package:flutter/material.dart';
import '../platform_bridge.dart';

/// SettingsScreen — Configure game packages and service behavior.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  List<String> _packages = [];
  final _pkgController = TextEditingController();
  // Full list of installed apps — loaded once on init
  List<_InstalledApp> _installedApps = [];
  bool _loadingApps = false;

  @override
  void initState() {
    super.initState();
    _load();
    _loadInstalledApps();
  }

  @override
  void dispose() {
    _pkgController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final pkgs = await PlatformBridge.getGamePackages();
    if (mounted) setState(() => _packages = pkgs);
  }

  /// Loads installed user apps (non-system) for the game picker.
  Future<void> _loadInstalledApps() async {
    if (mounted) setState(() => _loadingApps = true);
    try {
      final result = await PlatformBridge.getInstalledApps();
      final apps = result
          .map((e) => _InstalledApp(name: e['name'] ?? '', pkg: e['pkg'] ?? ''))
          .where((a) => a.pkg.isNotEmpty && a.name.isNotEmpty)
          .toList()
        ..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      if (mounted) setState(() { _installedApps = apps; _loadingApps = false; });
    } catch (_) {
      if (mounted) setState(() => _loadingApps = false);
    }
  }

  Future<void> _addPackage(String pkg) async {
    final trimmed = pkg.trim();
    if (trimmed.isEmpty || _packages.contains(trimmed)) return;
    final updated = [..._packages, trimmed];
    await PlatformBridge.setGamePackages(updated);
    _pkgController.clear();
    if (mounted) setState(() => _packages = updated);
  }

  Future<void> _removePackage(String pkg) async {
    final updated = _packages.where((p) => p != pkg).toList();
    await PlatformBridge.setGamePackages(updated);
    if (mounted) setState(() => _packages = updated);
  }

  Future<void> _confirmClearCoords() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Clear Learned Coords?',
            style: TextStyle(color: Colors.white)),
        content: const Text(
          'This resets the AI tap memory. The app will re-learn optimal '
          'close button positions over the next few sessions.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Reset',
                  style: TextStyle(color: Colors.orangeAccent))),
        ],
      ),
    );
    if (confirm == true) {
      await PlatformBridge.clearLearnedCoords();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('✅ Learned coordinates reset.')),
        );
      }
    }
  }

  void _showAppPicker() {
    // Snapshot the full list at the moment the picker opens.
    // localFiltered is managed entirely within StatefulBuilder so setLocal()
    // correctly rebuilds the bottom-sheet ListView without touching the parent.
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF0F0F1A),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.75,
        maxChildSize: 0.95,
        minChildSize: 0.5,
        builder: (_, scrollCtrl) {
          // localFiltered lives here — purely local to this closure
          List<_InstalledApp> localFiltered = List.from(_installedApps);

          return StatefulBuilder(
            builder: (_, setLocal) => Column(
              children: [
                // ── Drag handle ──────────────────────────────
                const SizedBox(height: 8),
                Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.white24,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Select a Game',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 12),

                // ── Search field ─────────────────────────────
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: TextField(
                    autofocus: true,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: 'Search games\u2026',
                      hintStyle: const TextStyle(color: Colors.white38),
                      prefixIcon:
                          const Icon(Icons.search, color: Colors.white38),
                      filled: true,
                      fillColor: const Color(0xFF1A1A2E),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(10),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 12),
                    ),
                    onChanged: (q) {
                      // FIX M-9: setLocal rebuilds only THIS StatefulBuilder
                      // scope — the bottom sheet ListView updates correctly.
                      setLocal(() {
                        localFiltered = q.isEmpty
                            ? List.from(_installedApps)
                            : _installedApps.where((app) {
                                final lower = q.toLowerCase();
                                return app.name
                                        .toLowerCase()
                                        .contains(lower) ||
                                    app.pkg.toLowerCase().contains(lower);
                              }).toList();
                      });
                    },
                  ),
                ),
                const SizedBox(height: 8),

                // ── App list ─────────────────────────────────
                Expanded(
                  child: _loadingApps
                      ? const Center(
                          child: CircularProgressIndicator(
                              color: Colors.greenAccent))
                      : localFiltered.isEmpty
                          ? Center(
                              child: Text(
                                _installedApps.isEmpty
                                    ? 'No apps found.\nMake sure permissions are granted.'
                                    : 'No results for that search.',
                                textAlign: TextAlign.center,
                                style:
                                    const TextStyle(color: Colors.white38),
                              ),
                            )
                          : ListView.builder(
                              controller: scrollCtrl,
                              itemCount: localFiltered.length,
                              itemBuilder: (_, i) {
                                final app = localFiltered[i];
                                final alreadyAdded =
                                    _packages.contains(app.pkg);
                                return ListTile(
                                  leading: const Icon(
                                      Icons.sports_esports_rounded,
                                      color: Colors.cyanAccent,
                                      size: 20),
                                  title: Text(app.name,
                                      style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 14)),
                                  subtitle: Text(app.pkg,
                                      style: const TextStyle(
                                          color: Colors.white38,
                                          fontSize: 11)),
                                  trailing: alreadyAdded
                                      ? const Icon(
                                          Icons.check_circle_rounded,
                                          color: Colors.greenAccent,
                                          size: 20)
                                      : const Icon(
                                          Icons.add_circle_outline,
                                          color: Colors.white38,
                                          size: 20),
                                  onTap: alreadyAdded
                                      ? null
                                      : () {
                                          _addPackage(app.pkg);
                                          Navigator.pop(ctx);
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(SnackBar(
                                            content: Text(
                                                '\u2705 ${app.name} added!'),
                                          ));
                                        },
                                );
                              },
                            ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('\u2699\ufe0f Settings')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // ─── GAME PACKAGES ───────────────────────────────
          const Text(
            'Games to Protect',
            style: TextStyle(
                fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
          ),
          const SizedBox(height: 6),
          const Text(
            'AdFastDismiss only activates when these games are running.',
            style: TextStyle(color: Colors.white54, fontSize: 13),
          ),
          const SizedBox(height: 14),

          // Add from installed apps (primary)
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _showAppPicker,
              icon: const Icon(Icons.add_rounded),
              label: const Text('Add a Game from Installed Apps'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.greenAccent,
                foregroundColor: Colors.black,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
            ),
          ),
          const SizedBox(height: 10),

          // Manual input (advanced / fallback)
          Row(children: [
            Expanded(
              child: TextField(
                controller: _pkgController,
                style: const TextStyle(color: Colors.white, fontSize: 13),
                decoration: InputDecoration(
                  hintText: 'Or type package name manually\u2026',
                  hintStyle:
                      const TextStyle(color: Colors.white38, fontSize: 13),
                  filled: true,
                  fillColor: const Color(0xFF1A1A2E),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: BorderSide.none,
                  ),
                  contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14, vertical: 12),
                ),
                onSubmitted: _addPackage,
              ),
            ),
            const SizedBox(width: 8),
            ElevatedButton(
              onPressed: () => _addPackage(_pkgController.text),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF1A1A2E),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
              ),
              child: const Text('Add'),
            ),
          ]),
          const SizedBox(height: 10),

          // Package list
          if (_packages.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(
                child: Text(
                  'No games added yet.\nTap "Add a Game" above.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white38),
                ),
              ),
            )
          else
            ..._packages.map((pkg) => Card(
                  color: const Color(0xFF1A1A2E),
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    leading: const Icon(Icons.sports_esports_rounded,
                        color: Colors.cyanAccent),
                    title: Text(pkg,
                        style: const TextStyle(
                            color: Colors.white, fontSize: 13)),
                    trailing: IconButton(
                      icon: const Icon(Icons.delete_outline_rounded,
                          color: Colors.redAccent, size: 20),
                      onPressed: () => _removePackage(pkg),
                    ),
                  ),
                )),

          const SizedBox(height: 28),
          const Divider(color: Colors.white12),
          const SizedBox(height: 16),

          // ─── REWARDED ADS NOTE ───────────────────────────
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: Colors.green.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(14),
              border:
                  Border.all(color: Colors.greenAccent.withValues(alpha: 0.3)),
            ),
            child: const Row(children: [
              Icon(Icons.verified_rounded, color: Colors.greenAccent),
              SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Rewarded Ads Protection is always ON.\n'
                  'AdFastDismiss NEVER dismisses rewarded ads — '
                  'your in-game rewards and offerwall earnings are always safe.',
                  style:
                      TextStyle(color: Colors.white70, fontSize: 13, height: 1.4),
                ),
              ),
            ]),
          ),

          const SizedBox(height: 20),

          // ─── TROUBLESHOOTING ─────────────────────────────
          const Text(
            'Troubleshooting',
            style: TextStyle(
                fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
          ),
          const SizedBox(height: 10),

          Card(
            color: const Color(0xFF1A1A2E),
            child: ListTile(
              leading: const Icon(Icons.psychology_alt_rounded,
                  color: Colors.orangeAccent),
              title: const Text('Reset Learned Coordinates',
                  style: TextStyle(color: Colors.white)),
              subtitle: const Text(
                  'If the app is tapping the wrong spot, reset its memory.',
                  style: TextStyle(color: Colors.white54, fontSize: 12)),
              trailing: const Icon(Icons.refresh_rounded,
                  color: Colors.white38, size: 18),
              onTap: _confirmClearCoords,
            ),
          ),
        ],
      ),
    );
  }
}

class _InstalledApp {
  final String name;
  final String pkg;
  const _InstalledApp({required this.name, required this.pkg});
}

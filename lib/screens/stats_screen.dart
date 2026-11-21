import 'package:flutter/material.dart';
import '../platform_bridge.dart';

/// StatsScreen — Shows lifetime and today's ad dismissal statistics,
/// plus a phase breakdown showing which cascade method is most effective.
class StatsScreen extends StatefulWidget {
  const StatsScreen({super.key});

  @override
  State<StatsScreen> createState() => _StatsScreenState();
}

class _StatsScreenState extends State<StatsScreen> {
  Map<String, int> _stats = {};
  Map<String, int> _phaseBreakdown = {};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final stats = await PlatformBridge.getStats();
    final phases = await PlatformBridge.getPhaseBreakdown();
    if (mounted) {
      setState(() {
        _stats = stats;
        _phaseBreakdown = phases;
        _loading = false;
      });
    }
  }

  String _formatTime(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;
    if (h > 0) return '${h}h ${m}m';
    if (m > 0) return '${m}m ${s}s';
    return '${s}s';
  }

  String _phaseName(String phase) {
    switch (phase) {
      case '1': return 'Phase 1 — Split Screen';
      case '3': return 'Phase 3 — Back Gesture / Edge Swipe';
      case '4': return 'Phase 4 — Skip Button (Node Scan)';
      case '5': return 'Phase 5 — Endcard Close';
      case '6': return 'Phase 6 — Coordinate Sweep';
      case '7': return 'Phase 7 — Recents Recovery';
      default:  return 'Phase $phase';
    }
  }

  Color _phaseColor(String phase) {
    switch (phase) {
      case '1': return Colors.cyanAccent;
      case '3': return Colors.blueAccent;
      case '4': return Colors.greenAccent;
      case '5': return Colors.lightGreenAccent;
      case '6': return Colors.orangeAccent;
      case '7': return Colors.redAccent;
      default:  return Colors.white38;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('📊 Statistics'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline_rounded),
            tooltip: 'Clear Stats',
            onPressed: () async {
              final confirm = await showDialog<bool>(
                context: context,
                builder: (_) => AlertDialog(
                  backgroundColor: const Color(0xFF1A1A2E),
                  title: const Text('Clear Stats?',
                      style: TextStyle(color: Colors.white)),
                  content: const Text(
                    'This will reset all counters. Cannot be undone.',
                    style: TextStyle(color: Colors.white70),
                  ),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(context, false),
                        child: const Text('Cancel')),
                    TextButton(onPressed: () => Navigator.pop(context, true),
                        child: const Text('Clear',
                            style: TextStyle(color: Colors.redAccent))),
                  ],
                ),
              );
              if (confirm == true) {
                await PlatformBridge.clearStats();
                _load();
              }
            },
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Colors.greenAccent))
          : SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // ─── TODAY ───────────────────────────────────
                  _SectionHeader('Today'),
                  const SizedBox(height: 10),
                  Row(children: [
                    Expanded(child: _BigStatCard(
                      label: 'Ads Dismissed',
                      value: '${_stats['todayAds'] ?? 0}',
                      color: Colors.greenAccent,
                      icon: Icons.shield_rounded,
                    )),
                    const SizedBox(width: 12),
                    Expanded(child: _BigStatCard(
                      label: 'Time Saved',
                      value: _formatTime(_stats['todaySecsSaved'] ?? 0),
                      color: Colors.cyanAccent,
                      icon: Icons.timer_rounded,
                    )),
                  ]),
                  const SizedBox(height: 24),

                  // ─── LIFETIME ────────────────────────────────
                  _SectionHeader('Lifetime'),
                  const SizedBox(height: 10),
                  Row(children: [
                    Expanded(child: _BigStatCard(
                      label: 'Total Dismissed',
                      value: '${_stats['totalAds'] ?? 0}',
                      color: Colors.purpleAccent,
                      icon: Icons.auto_awesome_rounded,
                    )),
                    const SizedBox(width: 12),
                    Expanded(child: _BigStatCard(
                      label: 'Total Saved',
                      value: _formatTime(_stats['totalSecsSaved'] ?? 0),
                      color: Colors.amberAccent,
                      icon: Icons.savings_rounded,
                    )),
                  ]),
                  const SizedBox(height: 24),

                  // ─── PHASE BREAKDOWN ─────────────────────────
                  if (_phaseBreakdown.isNotEmpty) ...[
                    _SectionHeader('Dismissal Method'),
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1A1A2E),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: () {
                          final total = _phaseBreakdown.values
                              .fold(0, (s, v) => s + v);
                          final sorted = _phaseBreakdown.entries.toList()
                            ..sort((a, b) => b.value.compareTo(a.value));
                          return sorted.map((e) {
                            final pct = total > 0
                                ? (e.value / total * 100).round()
                                : 0;
                            return Padding(
                              padding: const EdgeInsets.only(bottom: 12),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Expanded(
                                        child: Text(_phaseName(e.key),
                                            style: const TextStyle(
                                                color: Colors.white70,
                                                fontSize: 12)),
                                      ),
                                      Text('${e.value}x ($pct%)',
                                          style: const TextStyle(
                                              color: Colors.white38,
                                              fontSize: 12)),
                                    ],
                                  ),
                                  const SizedBox(height: 5),
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(4),
                                    child: LinearProgressIndicator(
                                      value: total > 0 ? e.value / total : 0,
                                      backgroundColor: Colors.white10,
                                      valueColor: AlwaysStoppedAnimation<Color>(
                                          _phaseColor(e.key)),
                                      minHeight: 6,
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }).toList();
                        }(),
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],

                  // ─── INFO CARD ────────────────────────────────
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1A1A2E),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('💡 About Stats',
                            style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.white)),
                        SizedBox(height: 8),
                        Text(
                          'Time saved is estimated based on typical ad duration (~30s). '
                          'Actual savings may vary by game and ad network.\n\n'
                          'The dismissal method breakdown shows which cascade phase '
                          'is most effective for your games — the app learns over time.',
                          style: TextStyle(color: Colors.white60, fontSize: 13, height: 1.4),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Text(title,
        style: const TextStyle(
            fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white70));
  }
}

class _BigStatCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  final IconData icon;
  const _BigStatCard({
    required this.label,
    required this.value,
    required this.color,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: color, size: 24),
        const SizedBox(height: 10),
        Text(value,
            style: TextStyle(
                fontSize: 32, fontWeight: FontWeight.bold, color: color)),
        const SizedBox(height: 4),
        Text(label,
            style: const TextStyle(color: Colors.white54, fontSize: 12)),
      ]),
    );
  }
}

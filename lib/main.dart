import 'package:flutter/material.dart';
import 'screens/home_screen.dart';

/// AdFastDismiss — Main Entry Point
/// Flutter UI + Kotlin AccessibilityService hybrid app.
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AdFastDismissApp());
}

class AdFastDismissApp extends StatelessWidget {
  const AdFastDismissApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PlayBlitz',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00C853), // Green — game-ready color
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFF0A0A0A),
        cardColor: const Color(0xFF1A1A2E),
      ),
      home: const HomeScreen(),
    );
  }
}

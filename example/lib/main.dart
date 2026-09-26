import 'dart:async';

import 'package:background_location_free/background_location_free.dart';
import 'package:flutter/material.dart';

/// Runs in a background Flutter engine when no UI listens.
@pragma('vm:entry-point')
Future<void> onBackgroundFix(LocationFix fix) async {
  // Keep this short: persist or upload the fix, then return.
  debugPrint('headless fix: ${fix.lat}, ${fix.lon}');
}

void main() => runApp(const MaterialApp(home: TrackerPage()));

/// Minimal tracker screen. Grant permissions first (for example with
/// permission_handler); this plugin only reads their state.
class TrackerPage extends StatefulWidget {
  /// Creates the page.
  const TrackerPage({super.key});

  @override
  State<TrackerPage> createState() => _TrackerPageState();
}

class _TrackerPageState extends State<TrackerPage> {
  final _fixes = <LocationFix>[];
  StreamSubscription<LocationFix>? _sub;
  LocationStatus? _status;
  bool _running = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    final status = await BackgroundLocation.status();
    final running = await BackgroundLocation.isRunning();
    final buffered = await BackgroundLocation.drainBuffered();
    if (!mounted) return;
    setState(() {
      _status = status;
      _running = running;
      _fixes.insertAll(0, buffered.reversed);
    });
    if (running) _listen();
  }

  void _listen() {
    _sub ??= BackgroundLocation.locations.listen(
      (fix) => setState(() => _fixes.insert(0, fix)),
      onError: (Object e) => setState(() => _error = '$e'),
    );
  }

  Future<void> _start() async {
    try {
      await BackgroundLocation.registerHeadlessCallback(onBackgroundFix);
      await BackgroundLocation.start(LocationConfig(
        interval: const Duration(seconds: 5),
        minDistanceMeters: 5,
        notification: const NotificationConfig(
          title: 'Tracking your trip',
          text: 'Location is recorded in the background',
        ),
      ));
      _listen();
      setState(() {
        _running = true;
        _error = null;
      });
    } on BackgroundLocationException catch (e) {
      setState(() => _error = '$e');
    }
  }

  Future<void> _stop() async {
    await BackgroundLocation.stop();
    await _sub?.cancel();
    _sub = null;
    setState(() => _running = false);
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = _status;
    return Scaffold(
      appBar: AppBar(title: const Text('background_location_free')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (s != null)
            Text('API ${s.sdkInt} | location ${s.location.name} | '
                'background ${s.backgroundLocation.name} | '
                'notifications ${s.notifications.name} | '
                'services ${s.locationServicesEnabled ? 'on' : 'off'} | '
                'battery exempt ${s.batteryOptimizationExempt.name}'),
          if (_error != null)
            Text(_error!, style: const TextStyle(color: Colors.red)),
          Row(children: [
            FilledButton(
              onPressed: _running ? null : _start,
              child: const Text('Start'),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: _running ? _stop : null,
              child: const Text('Stop'),
            ),
            const SizedBox(width: 8),
            TextButton(onPressed: _refresh, child: const Text('Refresh')),
          ]),
          for (final f in _fixes.take(50))
            ListTile(
              dense: true,
              title: Text('${f.lat.toStringAsFixed(5)}, '
                  '${f.lon.toStringAsFixed(5)}'),
              subtitle: Text('${f.time.toLocal()} | ${f.provider} | '
                  '±${f.accuracy?.toStringAsFixed(0)} m'
                  '${f.isMocked ? ' | mock' : ''}'),
            ),
        ],
      ),
    );
  }
}

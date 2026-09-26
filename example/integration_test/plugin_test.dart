// Emulator integration test: runs the real foreground service and feeds it
// positions with `adb emu geo fix` (GNSS HAL, isMocked false) and with a
// `cmd location` test provider (isMocked true). adb requests are served by
// tool/run_on_device.dart.
//
// The first test needs a fresh install (no location permission granted):
// `pm revoke` kills the app, so the denied path runs before the grants.
//
// Run from example/:
//   dart run tool/run_on_device.dart \
//     dev.abdeldjalilchougui.background_location_free_example emulator-5554

import 'dart:io';

import 'package:background_location_free/background_location_free.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'adb_bridge.dart';

const _pkg = 'dev.abdeldjalilchougui.background_location_free_example';
const _headlessLog = '/data/data/$_pkg/files/headless_fixes.txt';

/// Headless callback: runs in the background engine and appends each fix to
/// a file the UI isolate reads back.
@pragma('vm:entry-point')
Future<void> recordHeadlessFix(LocationFix fix) async {
  File(_headlessLog).writeAsStringSync(
      '${fix.lat},${fix.lon},${fix.provider},${fix.isMocked}\n',
      mode: FileMode.append,
      flush: true);
}

LocationConfig _config({String title = 'Tracking test'}) => LocationConfig(
      interval: const Duration(seconds: 1),
      fastestInterval: Duration.zero,
      notification: NotificationConfig(title: title, text: 'Initial text'),
    );

/// A point of the injected track.
class _Pt {
  const _Pt(this.lat, this.lon);
  final double lat;
  final double lon;
  bool matches(LocationFix f) =>
      (f.lat - lat).abs() < 1e-4 && (f.lon - lon).abs() < 1e-4;
  @override
  String toString() => '($lat, $lon)';
}

// A short walk in Algiers, then a second track for the buffer and headless
// tests; every point is at least ~100 m from the previous one.
const _track = [
  _Pt(36.7538, 3.0588),
  _Pt(36.7550, 3.0600),
  _Pt(36.7562, 3.0612),
  _Pt(36.7574, 3.0624),
];
const _bufferTrack = [_Pt(36.7600, 3.0700), _Pt(36.7612, 3.0712)];
const _headlessTrack = [_Pt(36.7700, 3.0800), _Pt(36.7712, 3.0812)];
const _mockPt = _Pt(35.6971, -0.6308);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  final adb = AdbBridge(_pkg);

  Future<void> geoFix(_Pt p) async {
    final r = await adb.run('emu geo fix ${p.lon} ${p.lat}');
    expect(r.exitCode, 0, reason: r.output);
  }

  /// Our ServiceRecord blocks from `dumpsys activity services`, or ''.
  /// (The dump always starts with the system's "Last ANR service" record.)
  Future<String> services() async {
    final dump = await adb.shell('dumpsys activity services $_pkg');
    final blocks = dump.split(RegExp(r'\n(?=\s*\* ServiceRecord\{)'));
    return blocks
        .where((b) => b.contains('ServiceRecord{') && b.contains('$_pkg/'))
        .join('\n');
  }

  /// Title and text of our notifications in the live notification list.
  Future<String> ourNotifications() async {
    final dump = await adb.shell('dumpsys notification --noredact');
    final start = dump.indexOf('Notification List:');
    if (start < 0) return '';
    final end = dump.indexOf('mArchive=', start);
    final list = dump.substring(start, end < 0 ? dump.length : end);
    return list
        .split(RegExp(r'\n(?=\s*NotificationRecord\()'))
        .where((r) => r.contains('pkg=$_pkg '))
        .expand((r) => RegExp(r'android\.(?:title|text)=String \((.*)\)')
            .allMatches(r)
            .map((m) => m.group(0)!))
        .join('\n');
  }

  testWidgets('without a location permission: status denied, typed errors',
      (tester) async {
    await tester.pumpWidget(const SizedBox());
    final status = await BackgroundLocation.status();
    debugPrint('status before grants: sdk ${status.sdkInt}, '
        'location ${status.location}, bg ${status.backgroundLocation}');
    expect(status.sdkInt, greaterThanOrEqualTo(34));
    expect(status.location, PermissionState.denied,
        reason: 'run on a fresh install (uninstall the example first)');
    expect(status.canStart, isFalse);
    await expectLater(
        BackgroundLocation.start(_config()),
        throwsA(isA<BackgroundLocationException>().having((e) => e.code, 'code',
            BackgroundLocationException.permissionDenied)));
    await expectLater(
        BackgroundLocation.lastKnown(),
        throwsA(isA<BackgroundLocationException>().having((e) => e.code, 'code',
            BackgroundLocationException.permissionDenied)));
    expect(await BackgroundLocation.isRunning(), isFalse);
    await expectLater(
        BackgroundLocation.updateNotification(title: 'x'),
        throwsA(isA<BackgroundLocationException>().having(
            (e) => e.code, 'code', BackgroundLocationException.notRunning)));
  });

  testWidgets('status reflects pm grant', (tester) async {
    await tester.pumpWidget(const SizedBox());
    for (final p in [
      'ACCESS_FINE_LOCATION',
      'ACCESS_COARSE_LOCATION',
      'ACCESS_BACKGROUND_LOCATION',
      'POST_NOTIFICATIONS',
    ]) {
      await adb.shell('pm grant $_pkg android.permission.$p');
    }
    final status = await BackgroundLocation.status();
    expect(status.location, PermissionState.granted);
    expect(status.preciseLocation, isTrue);
    expect(status.backgroundLocation, PermissionState.granted);
    expect(status.notifications, PermissionState.granted);
    expect(status.locationServicesEnabled, isTrue);
    expect(status.canStart, isTrue);
    expect(status.canTrackWhileClosed, isTrue);
  });

  testWidgets('start runs a location foreground service; fixes stream in',
      (tester) async {
    await tester.pumpWidget(const SizedBox());
    await BackgroundLocation.start(_config());
    expect(await BackgroundLocation.isRunning(), isTrue);

    final dump = await services();
    debugPrint('dumpsys services:\n$dump');
    expect(dump, contains('LocationService'));
    expect(dump, contains('isForeground=true'));
    // ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION == 0x8.
    expect(dump, matches(RegExp(r'types=0x0*8\b')));

    final fixes = <LocationFix>[];
    final sub = BackgroundLocation.locations.listen(fixes.add);
    try {
      for (final p in _track) {
        final got = await eventually(() async {
          await geoFix(p);
          return fixes.where(p.matches).toList();
        }, (List<LocationFix> l) => l.isNotEmpty,
            timeout: const Duration(seconds: 15));
        expect(got, isNotEmpty, reason: 'no fix at $p; got $fixes');
        final f = got.first;
        debugPrint('fix for $p: $f mocked=${f.isMocked}');
        expect(f.provider, 'gps');
        // `emu geo fix` goes through the emulator's GNSS HAL, like a real
        // receiver, so Android does not flag it as a mock location.
        expect(f.isMocked, isFalse);
        expect(f.accuracy, isNotNull);
        expect(f.time.difference(DateTime.now().toUtc()).abs(),
            lessThan(const Duration(minutes: 2)));
      }
      // The track arrived in order.
      final order = [
        for (final p in _track) fixes.indexWhere(p.matches),
      ];
      expect(order, orderedEquals([...order]..sort()));
      final last = await BackgroundLocation.lastKnown();
      expect(last, isNotNull);
      expect(_track.last.matches(last!), isTrue, reason: '$last');
    } finally {
      await sub.cancel();
    }
  });

  testWidgets('updateNotification changes the posted notification',
      (tester) async {
    await tester.pumpWidget(const SizedBox());
    final ours = ourNotifications;
    final before = await ours();
    expect(before, contains('Tracking test'));
    expect(before, contains('Initial text'));
    await BackgroundLocation.updateNotification(
        title: 'Trip 42', text: 'Recorded 4 points');
    final after = await eventually(
        ours, (String s) => s.contains('Recorded 4 points'),
        timeout: const Duration(seconds: 5));
    debugPrint('notification after update:\n$after');
    expect(after, contains('Trip 42'));
    expect(after, contains('Recorded 4 points'));
    expect(after, isNot(contains('Initial text')));
    // Only the title changes; the text stays.
    await BackgroundLocation.updateNotification(title: 'Trip 43');
    final third = await eventually(ours, (String s) => s.contains('Trip 43'),
        timeout: const Duration(seconds: 5));
    expect(third, contains('Recorded 4 points'));
    // Still the service's notification: the service stays in the foreground.
    expect(await services(), contains('isForeground=true'));
  });

  testWidgets('without a listener fixes are buffered; drainBuffered empties',
      (tester) async {
    await tester.pumpWidget(const SizedBox());
    // Nothing listens here (the previous subscription was cancelled).
    await BackgroundLocation.drainBuffered();
    for (final p in _bufferTrack) {
      final before = await BackgroundLocation.bufferedCount();
      // Resend while waiting: the emulator's GNSS HAL sometimes misses one.
      final now = await eventually(() async {
        await geoFix(p);
        return BackgroundLocation.bufferedCount();
      }, (int n) => n > before, timeout: const Duration(seconds: 15));
      expect(now, greaterThan(before));
      await Future<void>.delayed(const Duration(seconds: 1));
    }
    final count = await BackgroundLocation.bufferedCount();
    final drained = await BackgroundLocation.drainBuffered();
    debugPrint('buffered $count, drained ${drained.length}: '
        '${drained.map((f) => '${f.lat},${f.lon}').toSet()}');
    expect(drained.length, greaterThanOrEqualTo(count));
    for (final p in _bufferTrack) {
      expect(drained.any(p.matches), isTrue, reason: 'no buffered fix at $p');
    }
    // Oldest first.
    for (var i = 1; i < drained.length; i++) {
      expect(drained[i].time.isBefore(drained[i - 1].time), isFalse);
    }
    expect(drained.indexWhere(_bufferTrack[0].matches),
        lessThan(drained.indexWhere(_bufferTrack[1].matches)));
    expect(await BackgroundLocation.bufferedCount(), lessThan(3),
        reason: 'drained, only fixes that arrived since may be buffered');
  });

  testWidgets('headless callback gets fixes while the UI is in the background',
      (tester) async {
    await tester.pumpWidget(const SizedBox());
    final log = File(_headlessLog);
    if (log.existsSync()) log.deleteSync();
    await BackgroundLocation.registerHeadlessCallback(recordHeadlessFix);
    await BackgroundLocation.drainBuffered();
    try {
      await adb.shell('input keyevent KEYCODE_HOME');
      await Future<void>.delayed(const Duration(seconds: 2));
      expect(await adb.topResumedActivity(), isNot(contains(_pkg)));
      List<String> lines() => log.existsSync() ? log.readAsLinesSync() : [];
      bool logged(_Pt p) => lines().any((l) {
            final parts = l.split(',');
            return (double.parse(parts[0]) - p.lat).abs() < 1e-4 &&
                (double.parse(parts[1]) - p.lon).abs() < 1e-4;
          });
      for (final p in _headlessTrack) {
        final ok = await eventually(() async {
          await geoFix(p);
          return logged(p);
        }, (bool b) => b, timeout: const Duration(seconds: 30));
        expect(ok, isTrue,
            reason: 'headless callback never saw $p; log: ${lines()}');
      }
      debugPrint('headless log: ${lines().length} lines, '
          'last ${lines().last}');
      expect(lines().every((l) => l.endsWith(',gps,false')), isTrue);
      // Delivered to the callback, not buffered.
      expect(await BackgroundLocation.bufferedCount(), 0);
      expect(await BackgroundLocation.isRunning(), isTrue);
    } finally {
      await adb.shell('am start -n $_pkg/.MainActivity');
      await adb.backTo(_pkg);
      await BackgroundLocation.unregisterHeadlessCallback();
    }
  });

  testWidgets('a test provider location is flagged isMocked', (tester) async {
    await tester.pumpWidget(const SizedBox());
    await adb.shell('appops set com.android.shell android:mock_location allow');
    final fixes = <LocationFix>[];
    final errors = <Object>[];
    final sub =
        BackgroundLocation.locations.listen(fixes.add, onError: errors.add);
    try {
      // The test provider replaces the real gps provider and starts
      // disabled: the service reports that as a stream error.
      await adb.shell('cmd location providers add-test-provider gps');
      final disabled = await eventually(
          () async => errors.toList(), (List<Object> e) => e.isNotEmpty,
          timeout: const Duration(seconds: 5));
      debugPrint('errors after add-test-provider: $disabled');
      expect(
          disabled.first,
          isA<BackgroundLocationException>()
              .having((e) => e.code, 'code', 'provider_disabled'));
      await adb.shell('cmd location providers set-test-provider-enabled gps '
          'true');
      final got = await eventually(() async {
        await adb.shell('cmd location providers set-test-provider-location '
            'gps --location ${_mockPt.lat},${_mockPt.lon} --accuracy 7');
        return fixes.where(_mockPt.matches).toList();
      }, (List<LocationFix> l) => l.isNotEmpty,
          timeout: const Duration(seconds: 20));
      expect(got, isNotEmpty, reason: 'no mock fix; got $fixes');
      debugPrint('mock fix: ${got.first} mocked=${got.first.isMocked}');
      expect(got.first.isMocked, isTrue);
      expect(got.first.accuracy, closeTo(7, 0.01));
    } finally {
      await sub.cancel();
      await adb.shell('cmd location providers remove-test-provider gps');
      await adb
          .shell('appops set com.android.shell android:mock_location default');
    }
    // The real GNSS provider is back: geo fixes are not mocked again.
    final after = <LocationFix>[];
    final sub2 = BackgroundLocation.locations.listen(after.add);
    try {
      final real = await eventually(() async {
        await geoFix(_track.first);
        return after.where(_track.first.matches).toList();
      }, (List<LocationFix> l) => l.isNotEmpty,
          timeout: const Duration(seconds: 15));
      expect(real, isNotEmpty);
      expect(real.first.isMocked, isFalse);
    } finally {
      await sub2.cancel();
    }
  });

  testWidgets('stop removes the service', (tester) async {
    await tester.pumpWidget(const SizedBox());
    await BackgroundLocation.stop();
    final dump = await eventually(services, (String s) => s.isEmpty,
        timeout: const Duration(seconds: 5));
    expect(dump, isEmpty);
    expect(await BackgroundLocation.isRunning(), isFalse);
    expect(await ourNotifications(), isEmpty);
    await expectLater(
        BackgroundLocation.updateNotification(text: 'x'),
        throwsA(isA<BackgroundLocationException>().having(
            (e) => e.code, 'code', BackgroundLocationException.notRunning)));
  });

  testWidgets('start from the background fails with start_not_allowed',
      (tester) async {
    await tester.pumpWidget(const SizedBox());
    await adb.shell('input keyevent KEYCODE_HOME');
    try {
      // Leave the grace period after the activity stops.
      await Future<void>.delayed(const Duration(seconds: 15));
      expect(await adb.topResumedActivity(), isNot(contains(_pkg)));
      Object? error;
      try {
        await BackgroundLocation.start(_config());
      } catch (e) {
        error = e;
      }
      debugPrint('start from background: $error');
      expect(
          error,
          isA<BackgroundLocationException>().having((e) => e.code, 'code',
              BackgroundLocationException.startNotAllowed));
      expect(await BackgroundLocation.isRunning(), isFalse);
      // The system keeps a record of the refused start (app=null), but
      // nothing runs in the foreground.
      expect(await services(), isNot(contains('isForeground=true')));
    } finally {
      await BackgroundLocation.stop();
      await adb.shell('am start -n $_pkg/.MainActivity');
      await adb.backTo(_pkg);
    }
  });
}

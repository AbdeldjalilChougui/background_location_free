import 'dart:ui';

import 'package:background_location_free/background_location_free.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

Map<String, Object?> fixMap(double lat, int timeMs) => <String, Object?>{
      'lat': lat,
      'lon': 3.0,
      'accuracy': 5.0,
      'time': timeMs,
      'provider': 'gps',
      'isMocked': false,
    };

@pragma('vm:entry-point')
Future<void> topLevelCallback(LocationFix fix) async {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const method = MethodChannel(BackgroundLocation.methodChannelName);
  const events = EventChannel(BackgroundLocation.eventChannelName);
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<MethodCall> calls;
  late Object? Function(MethodCall call) reply;

  setUp(() {
    calls = <MethodCall>[];
    reply = (_) => null;
    messenger.setMockMethodCallHandler(method, (call) async {
      calls.add(call);
      return reply(call);
    });
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(method, null);
    messenger.setMockStreamHandler(events, null);
    BackgroundLocation.debugHandleOf = null;
  });

  group('service control', () {
    test('start sends the full config', () async {
      final config = LocationConfig(
        interval: const Duration(seconds: 3),
        minDistanceMeters: 10,
        notification: const NotificationConfig(title: 'T', text: 'x'),
      );
      await BackgroundLocation.start(config);
      expect(calls.single.method, 'start');
      expect(calls.single.arguments, config.toMap());
    });

    test('native errors become BackgroundLocationException', () async {
      reply = (_) => throw PlatformException(
          code: BackgroundLocationException.permissionDenied,
          message: 'no location permission');
      await expectLater(
        BackgroundLocation.start(LocationConfig(
            notification: const NotificationConfig(title: 'T', text: 'x'))),
        throwsA(isA<BackgroundLocationException>()
            .having((e) => e.code, 'code', 'permission_denied')
            .having((e) => e.message, 'message', 'no location permission')),
      );
    });

    test('stop and isRunning', () async {
      reply = (call) => call.method == 'isRunning' ? true : null;
      await BackgroundLocation.stop();
      expect(await BackgroundLocation.isRunning(), isTrue);
      expect(calls.map((c) => c.method), ['stop', 'isRunning']);
    });

    test('isRunning treats a null reply as false', () async {
      expect(await BackgroundLocation.isRunning(), isFalse);
    });

    test('updateNotification sends only the given fields', () async {
      await BackgroundLocation.updateNotification(title: 'New');
      await BackgroundLocation.updateNotification(text: 'Body');
      expect(calls[0].arguments, <String, Object?>{'title': 'New'});
      expect(calls[1].arguments, <String, Object?>{'text': 'Body'});
      expect(calls.every((c) => c.method == 'updateNotification'), isTrue);
    });

    test('updateNotification needs a title or a text', () {
      expect(
          () => BackgroundLocation.updateNotification(), throwsArgumentError);
    });
  });

  group('fixes', () {
    test('locations parses events from the EventChannel', () async {
      messenger.setMockStreamHandler(
        events,
        MockStreamHandler.inline(onListen: (args, sink) {
          sink.success(fixMap(1, 1000));
          sink.success(fixMap(2, 2000));
        }),
      );
      final fixes = await BackgroundLocation.locations.take(2).toList();
      expect(fixes.map((f) => f.lat), [1.0, 2.0]);
      expect(fixes.last.time.millisecondsSinceEpoch, 2000);
    });

    test('the stream can be listened to again after a cancel', () async {
      messenger.setMockStreamHandler(
        events,
        MockStreamHandler.inline(onListen: (args, sink) {
          sink.success(fixMap(5, 5000));
        }),
      );
      expect((await BackgroundLocation.locations.first).lat, 5.0);
      expect((await BackgroundLocation.locations.first).lat, 5.0);
    });

    test('stream errors are BackgroundLocationException', () async {
      messenger.setMockStreamHandler(
        events,
        MockStreamHandler.inline(onListen: (args, sink) {
          sink.error(code: 'provider_disabled', message: 'gps off');
        }),
      );
      await expectLater(
        BackgroundLocation.locations,
        emitsError(isA<BackgroundLocationException>()
            .having((e) => e.code, 'code', 'provider_disabled')),
      );
    });

    test('lastKnown returns null or a fix', () async {
      reply = (_) => null;
      expect(await BackgroundLocation.lastKnown(), isNull);
      reply = (_) => fixMap(7, 5000);
      final fix = await BackgroundLocation.lastKnown();
      expect(fix!.lat, 7.0);
      expect(calls.map((c) => c.method), ['lastKnown', 'lastKnown']);
    });

    test('drainBuffered parses the list in order', () async {
      reply = (_) => <Object?>[fixMap(1, 1), fixMap(2, 2), fixMap(3, 3)];
      final fixes = await BackgroundLocation.drainBuffered();
      expect(fixes.map((f) => f.lat), [1.0, 2.0, 3.0]);
      expect(calls.single.method, 'drainBuffered');
    });

    test('drainBuffered treats null as empty', () async {
      expect(await BackgroundLocation.drainBuffered(), isEmpty);
    });

    test('bufferedCount', () async {
      reply = (_) => 42;
      expect(await BackgroundLocation.bufferedCount(), 42);
    });
  });

  group('status', () {
    test('status maps the native reply', () async {
      reply = (_) => <String, Object?>{
            'sdkInt': 35,
            'location': 'granted',
            'preciseLocation': true,
            'backgroundLocation': 'granted',
            'notifications': 'denied',
            'locationServicesEnabled': true,
            'batteryOptimizationExempt': 'denied',
          };
      final status = await BackgroundLocation.status();
      expect(status.sdkInt, 35);
      expect(status.notifications, PermissionState.denied);
      expect(status.canTrackWhileClosed, isTrue);
      expect(calls.single.method, 'status');
    });
  });

  group('headless callback', () {
    test('register sends the dispatcher and callback handles', () async {
      final handles = <Function, int>{};
      BackgroundLocation.debugHandleOf = (f) {
        handles.putIfAbsent(f, () => 100 + handles.length);
        return CallbackHandle.fromRawHandle(handles[f]!);
      };
      await BackgroundLocation.registerHeadlessCallback(topLevelCallback);
      expect(calls.single.method, 'registerHeadless');
      final args = calls.single.arguments as Map<Object?, Object?>;
      expect(args['callbackHandle'], handles[topLevelCallback]);
      expect(args['dispatcherHandle'], isA<int>());
      expect(args['dispatcherHandle'], isNot(args['callbackHandle']));
    });

    test('register rejects functions without a callback handle', () {
      BackgroundLocation.debugHandleOf = (_) => null;
      expect(() => BackgroundLocation.registerHeadlessCallback((_) async {}),
          throwsArgumentError);
      expect(calls, isEmpty);
    });

    test('real handle lookup works for a top-level function', () {
      final handle = PluginUtilities.getCallbackHandle(topLevelCallback);
      expect(handle, isNotNull);
    });

    test('unregister', () async {
      await BackgroundLocation.unregisterHeadlessCallback();
      expect(calls.single.method, 'unregisterHeadless');
    });

    test('the dispatcher invokes the registered callback with the fix',
        () async {
      final received = <LocationFix>[];
      Future<void> cb(LocationFix f) async => received.add(f);
      final result = await handleHeadlessCall(
        MethodCall('onFix', <String, Object?>{
          'callbackHandle': 7,
          'fix': fixMap(9, 9000),
        }),
        lookup: (h) => h.toRawHandle() == 7 ? cb : null,
      );
      expect(result, isTrue);
      expect(received.single.lat, 9.0);
    });

    test('the dispatcher fails loudly for an unknown handle', () async {
      await expectLater(
        handleHeadlessCall(
          MethodCall('onFix', <String, Object?>{
            'callbackHandle': 1,
            'fix': fixMap(9, 9000),
          }),
          lookup: (_) => null,
        ),
        throwsA(isA<PlatformException>()
            .having((e) => e.code, 'code', 'callback_not_found')),
      );
    });

    test('the dispatcher rejects a callback of the wrong type', () async {
      await expectLater(
        handleHeadlessCall(
          MethodCall('onFix', <String, Object?>{
            'callbackHandle': 1,
            'fix': fixMap(9, 9000),
          }),
          lookup: (_) => (int x) => x,
        ),
        throwsA(isA<PlatformException>()
            .having((e) => e.code, 'code', 'callback_not_found')),
      );
    });

    test('the dispatcher rejects unknown methods', () async {
      await expectLater(
        handleHeadlessCall(const MethodCall('nope'), lookup: (_) => null),
        throwsA(isA<MissingPluginException>()),
      );
    });

    test('callback errors surface to the native side', () async {
      await expectLater(
        handleHeadlessCall(
          MethodCall('onFix', <String, Object?>{
            'callbackHandle': 1,
            'fix': fixMap(9, 9000),
          }),
          lookup: (_) => (LocationFix f) async => throw StateError('boom'),
        ),
        throwsA(isA<StateError>()),
      );
    });
  });

  test('channel names are stable', () {
    expect(BackgroundLocation.methodChannelName,
        'dev.abdeldjalilchougui/background_location_free');
    expect(BackgroundLocation.eventChannelName,
        'dev.abdeldjalilchougui/background_location_free/locations');
    expect(BackgroundLocation.headlessChannelName,
        'dev.abdeldjalilchougui/background_location_free/headless');
  });
}

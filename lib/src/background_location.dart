import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'channels.dart' as ch;
import 'exception.dart';
import 'headless.dart';
import 'location_config.dart';
import 'location_fix.dart';
import 'location_status.dart';

/// Background location on Android through a foreground service.
///
/// Call [start] while the app is visible. Fixes arrive on [locations] while
/// a Dart listener is attached. Otherwise they go to the headless callback
/// (see [registerHeadlessCallback]) or, without one, to a native ring
/// buffer that [drainBuffered] empties.
///
/// This class never asks for permissions; check them with [status].
abstract final class BackgroundLocation {
  /// Name of the command method channel.
  static const String methodChannelName = ch.methodChannelName;

  /// Name of the fix event channel.
  static const String eventChannelName = ch.eventChannelName;

  /// Name of the channel used by the headless background engine.
  static const String headlessChannelName = ch.headlessChannelName;

  static const MethodChannel _channel = MethodChannel(methodChannelName);
  static const EventChannel _events = EventChannel(eventChannelName);

  /// Resolves a function to its callback handle. `null` uses
  /// `PluginUtilities.getCallbackHandle`. Tests replace it.
  @visibleForTesting
  static CallbackHandle? Function(Function callback)? debugHandleOf;

  static Stream<LocationFix>? _locations;

  /// Starts the foreground service with [config], or reconfigures it when
  /// it already runs.
  ///
  /// Call it while an activity of the app is visible: on API 31+ Android
  /// refuses to start a foreground service from the background, and on API
  /// 34+ a `location` service also needs a location permission granted.
  /// Throws [BackgroundLocationException] with
  /// [BackgroundLocationException.permissionDenied],
  /// [BackgroundLocationException.startNotAllowed] or
  /// [BackgroundLocationException.noProvider].
  static Future<void> start(LocationConfig config) =>
      _invoke<void>('start', config.toMap());

  /// Stops the service and cancels any restart after reboot. Buffered fixes
  /// are kept until [drainBuffered].
  static Future<void> stop() => _invoke<void>('stop');

  /// Whether the service is running in this process.
  static Future<bool> isRunning() async =>
      await _invoke<bool>('isRunning') ?? false;

  /// Changes the notification [title] and/or [text] of the running
  /// service. Throws [ArgumentError] when both are `null`, and
  /// [BackgroundLocationException] with
  /// [BackgroundLocationException.notRunning] when the service is stopped.
  static Future<void> updateNotification({String? title, String? text}) {
    if (title == null && text == null) {
      throw ArgumentError('Give a title, a text, or both');
    }
    return _invoke<void>('updateNotification', <String, Object?>{
      if (title != null) 'title': title,
      if (text != null) 'text': text,
    });
  }

  /// Live fixes from the service.
  ///
  /// This is one shared broadcast stream: every access returns the same
  /// stream, and the native side counts a listener while at least one
  /// subscription is active. While nobody listens, fixes go to the headless
  /// callback or the buffer. Native errors arrive as
  /// [BackgroundLocationException].
  static Stream<LocationFix> get locations => _locations ??= _events
      .receiveBroadcastStream()
      .transform(StreamTransformer<dynamic, LocationFix>.fromHandlers(
        handleData: (data, sink) =>
            sink.add(LocationFix.fromMap(data as Map<Object?, Object?>)),
        handleError: (error, stack, sink) =>
            sink.addError(_mapError(error), stack),
      ));

  /// The newest last-known fix across the device's providers, or `null`.
  /// Does not start the service and does not turn on any provider.
  static Future<LocationFix?> lastKnown() async {
    final map = await _invoke<Map<Object?, Object?>>('lastKnown');
    return map == null ? null : LocationFix.fromMap(map);
  }

  /// Returns and removes the fixes buffered natively while no Dart
  /// listener or headless callback took them, oldest first.
  static Future<List<LocationFix>> drainBuffered() async {
    final list = await _invoke<List<Object?>>('drainBuffered');
    if (list == null) return const [];
    return [
      for (final item in list)
        LocationFix.fromMap(item! as Map<Object?, Object?>),
    ];
  }

  /// Number of fixes waiting in the native buffer.
  static Future<int> bufferedCount() async =>
      await _invoke<int>('bufferedCount') ?? 0;

  /// Reads permissions and settings that affect background location.
  static Future<LocationStatus> status() async {
    final map = await _invoke<Map<Object?, Object?>>('status');
    return LocationStatus.fromMap(map ?? const {});
  }

  /// Registers [callback] to receive fixes when no Dart listener is
  /// attached, for example after the user closed the UI. The service then
  /// starts a background Flutter engine and calls [callback] there.
  ///
  /// **Experimental.** [callback] must be a top-level or static function
  /// annotated with `@pragma('vm:entry-point')`; it runs in another
  /// isolate, so it cannot touch the UI isolate's state. Throws
  /// [ArgumentError] when no callback handle can be made for it.
  static Future<void> registerHeadlessCallback(
      HeadlessLocationCallback callback) {
    final handleOf = debugHandleOf ?? PluginUtilities.getCallbackHandle;
    final dispatcher = handleOf(headlessDispatcher);
    final user = handleOf(callback);
    if (user == null) {
      throw ArgumentError.value(callback, 'callback',
          "must be a top-level or static function with @pragma('vm:entry-point')");
    }
    if (dispatcher == null) {
      throw StateError('No callback handle for the headless dispatcher');
    }
    return _invoke<void>('registerHeadless', <String, Object?>{
      'dispatcherHandle': dispatcher.toRawHandle(),
      'callbackHandle': user.toRawHandle(),
    });
  }

  /// Removes the headless callback; fixes then go to the buffer while no
  /// listener is attached.
  static Future<void> unregisterHeadlessCallback() =>
      _invoke<void>('unregisterHeadless');

  static Future<T?> _invoke<T>(String method, [Object? args]) async {
    try {
      return await _channel.invokeMethod<T>(method, args);
    } on PlatformException catch (e) {
      throw _mapError(e);
    }
  }

  static Object _mapError(Object error) => error is PlatformException
      ? BackgroundLocationException(error.code, error.message)
      : error;
}

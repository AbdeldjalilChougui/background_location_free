import 'dart:async';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'channels.dart';
import 'location_fix.dart';

/// A function run for each fix when no Dart listener is attached. It must
/// be a top-level or static function annotated with
/// `@pragma('vm:entry-point')`, because it runs in a separate background
/// Flutter engine with its own isolate.
typedef HeadlessLocationCallback = FutureOr<void> Function(LocationFix fix);

/// Entry point of the background engine. The native service looks it up by
/// its callback handle and runs it. It wires the headless channel and tells
/// the native side it is ready for fixes.
@pragma('vm:entry-point')
void headlessDispatcher() {
  WidgetsFlutterBinding.ensureInitialized();
  DartPluginRegistrant.ensureInitialized();
  const channel = MethodChannel(headlessChannelName);
  channel.setMethodCallHandler(
    (call) =>
        handleHeadlessCall(call, lookup: PluginUtilities.getCallbackFromHandle),
  );
  unawaited(channel.invokeMethod<void>('ready'));
}

/// Handles one call on the headless channel. Public so the plumbing can be
/// tested; apps do not call it.
///
/// `onFix` carries `callbackHandle` and `fix`. The callback is found with
/// [lookup] (normally `PluginUtilities.getCallbackFromHandle`) and awaited.
/// Returns `true` on success. Throws a [PlatformException] with code
/// `callback_not_found` when the handle does not resolve to a
/// [HeadlessLocationCallback].
Future<Object?> handleHeadlessCall(
  MethodCall call, {
  required Function? Function(CallbackHandle handle) lookup,
}) async {
  if (call.method != 'onFix') {
    throw MissingPluginException('Unknown headless method ${call.method}');
  }
  final args = call.arguments as Map<Object?, Object?>;
  final raw = args['callbackHandle'];
  final fn = raw is int ? lookup(CallbackHandle.fromRawHandle(raw)) : null;
  if (fn is! HeadlessLocationCallback) {
    throw PlatformException(
      code: 'callback_not_found',
      message: 'No headless callback for handle $raw. Register a top-level '
          "function annotated with @pragma('vm:entry-point').",
    );
  }
  final fix = LocationFix.fromMap(args['fix']! as Map<Object?, Object?>);
  await fn(fix);
  return true;
}

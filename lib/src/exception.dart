/// An error reported by the native side.
class BackgroundLocationException implements Exception {
  /// Creates an exception with a machine-readable [code].
  const BackgroundLocationException(this.code, [this.message]);

  /// No location permission is granted.
  static const String permissionDenied = 'permission_denied';

  /// Android refused to start the foreground service, usually because the
  /// app was in the background on API 31+
  /// (`ForegroundServiceStartNotAllowedException`).
  static const String startNotAllowed = 'start_not_allowed';

  /// The configuration was rejected by the native side.
  static const String invalidConfig = 'invalid_config';

  /// The service is not running.
  static const String notRunning = 'not_running';

  /// The device has no usable location provider.
  static const String noProvider = 'no_provider';

  /// One of the codes above, or another code from the native side.
  final String code;

  /// Human-readable detail, if any.
  final String? message;

  @override
  String toString() => 'BackgroundLocationException($code'
      '${message == null ? '' : ', $message'})';
}

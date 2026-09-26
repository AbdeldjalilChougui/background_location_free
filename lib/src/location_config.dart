/// How hard the location provider should try. Maps onto a
/// `LocationManager` provider on the native side.
enum LocationPriority {
  /// GPS provider; falls back to the network provider when GPS is absent.
  highAccuracy('highAccuracy'),

  /// Network provider (Wi-Fi/cell); falls back to GPS when absent.
  balanced('balanced'),

  /// Network provider; falls back to the passive provider.
  lowPower('lowPower'),

  /// Passive provider: only fixes that other apps request. No extra power.
  passive('passive');

  const LocationPriority(this.wireName);

  /// Name used on the platform channel.
  final String wireName;
}

/// The notification shown while the foreground service runs. Android
/// requires one for every foreground service.
class NotificationConfig {
  /// Creates a notification description.
  ///
  /// [iconResource] is the name of a drawable or mipmap resource in the app
  /// (for example `ic_stat_location`); `null` uses the app icon. Status bar
  /// icons should be white-on-transparent. [color] is `0xAARRGGBB`.
  const NotificationConfig({
    required this.title,
    required this.text,
    this.iconResource,
    this.channelId = 'background_location',
    this.channelName = 'Background location',
    this.color,
    this.ongoing = true,
  });

  /// Notification title.
  final String title;

  /// Notification body text.
  final String text;

  /// Drawable or mipmap resource name for the small icon, or `null` for the
  /// app icon.
  final String? iconResource;

  /// Notification channel id (Android 8+).
  final String channelId;

  /// User-visible notification channel name (Android 8+).
  final String channelName;

  /// Accent colour as `0xAARRGGBB`, or `null` for the system default.
  final int? color;

  /// Whether the notification is marked ongoing. Android 14+ lets the user
  /// dismiss foreground service notifications anyway.
  final bool ongoing;

  /// Throws [ArgumentError] when a field is invalid.
  void validate() {
    if (title.isEmpty) {
      throw ArgumentError.value(title, 'title', 'must not be empty');
    }
    if (channelId.isEmpty) {
      throw ArgumentError.value(channelId, 'channelId', 'must not be empty');
    }
    if (channelName.isEmpty) {
      throw ArgumentError.value(
          channelName, 'channelName', 'must not be empty');
    }
    final c = color;
    if (c != null && (c < 0 || c > 0xFFFFFFFF)) {
      throw ArgumentError.value(c, 'color', 'must be 0xAARRGGBB');
    }
  }

  /// The wire form used by the platform channel.
  Map<String, Object?> toMap() => <String, Object?>{
        'title': title,
        'text': text,
        'iconResource': iconResource,
        'channelId': channelId,
        'channelName': channelName,
        'color': color,
        'ongoing': ongoing,
      };
}

/// Settings for `BackgroundLocation.start`.
class LocationConfig {
  /// Creates and validates a configuration. Throws [ArgumentError] when a
  /// value is out of range.
  ///
  /// - [interval]: requested time between fixes; must be positive.
  /// - [fastestInterval]: fixes closer together than this are dropped
  ///   natively; defaults to [interval] and must not exceed it.
  /// - [minDistanceMeters]: fixes closer than this to the last delivered
  ///   fix are dropped; `0` keeps all.
  /// - [stopOnTaskRemoved]: stop the service when the user swipes the app
  ///   out of Recents.
  /// - [restartOnBoot]: restart after a reboot (needs extra manifest
  ///   entries; see the README).
  /// - [bufferCapacity]: fixes kept natively while no Dart listener is
  ///   attached; the oldest are dropped first.
  LocationConfig({
    required this.notification,
    this.interval = const Duration(seconds: 5),
    Duration? fastestInterval,
    this.minDistanceMeters = 0,
    this.priority = LocationPriority.highAccuracy,
    this.stopOnTaskRemoved = true,
    this.restartOnBoot = false,
    this.bufferCapacity = 1000,
  }) : fastestInterval = fastestInterval ?? interval {
    if (interval <= Duration.zero) {
      throw ArgumentError.value(interval, 'interval', 'must be positive');
    }
    if (this.fastestInterval < Duration.zero ||
        this.fastestInterval > interval) {
      throw ArgumentError.value(this.fastestInterval, 'fastestInterval',
          'must be within [0, interval]');
    }
    if (!minDistanceMeters.isFinite || minDistanceMeters < 0) {
      throw ArgumentError.value(
          minDistanceMeters, 'minDistanceMeters', 'must be >= 0');
    }
    if (bufferCapacity < 1 || bufferCapacity > maxBufferCapacity) {
      throw ArgumentError.value(bufferCapacity, 'bufferCapacity',
          'must be within [1, $maxBufferCapacity]');
    }
    notification.validate();
  }

  /// Largest accepted [bufferCapacity].
  static const int maxBufferCapacity = 100000;

  /// Requested time between fixes.
  final Duration interval;

  /// Smallest time between delivered fixes.
  final Duration fastestInterval;

  /// Smallest distance between delivered fixes, in metres.
  final double minDistanceMeters;

  /// Which provider to use.
  final LocationPriority priority;

  /// The foreground service notification.
  final NotificationConfig notification;

  /// Whether swiping the app away from Recents stops tracking.
  final bool stopOnTaskRemoved;

  /// Whether tracking restarts after a reboot when it was running.
  final bool restartOnBoot;

  /// Most fixes held natively while nobody listens.
  final int bufferCapacity;

  /// The wire form used by the platform channel.
  Map<String, Object?> toMap() => <String, Object?>{
        'intervalMs': interval.inMilliseconds,
        'fastestIntervalMs': fastestInterval.inMilliseconds,
        'minDistanceMeters': minDistanceMeters.toDouble(),
        'priority': priority.wireName,
        'stopOnTaskRemoved': stopOnTaskRemoved,
        'restartOnBoot': restartOnBoot,
        'bufferCapacity': bufferCapacity,
        'notification': notification.toMap(),
      };
}

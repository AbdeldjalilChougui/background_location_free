/// State of one permission or setting.
enum PermissionState {
  /// Granted, or the setting is in the state this package needs.
  granted('granted'),

  /// Not granted.
  denied('denied'),

  /// Does not exist on this Android version (for example background
  /// location before API 29, or battery optimisation before API 23).
  notApplicable('notApplicable');

  const PermissionState(this.wireName);

  /// Name used on the platform channel.
  final String wireName;

  /// Whether the state does not block anything ([granted] or
  /// [notApplicable]).
  bool get isOk => this != denied;

  /// Parses [wireName]. Throws [FormatException] for unknown names.
  static PermissionState fromWire(Object? name) {
    for (final s in values) {
      if (s.wireName == name) return s;
    }
    throw FormatException('Unknown permission state: $name');
  }
}

/// A snapshot of everything that affects background location, from
/// `BackgroundLocation.status`. This package only reads these; request them
/// with `permission_handler` or `android_special_permissions`.
class LocationStatus {
  /// Creates a snapshot.
  const LocationStatus({
    required this.sdkInt,
    required this.location,
    required this.preciseLocation,
    required this.backgroundLocation,
    required this.notifications,
    required this.locationServicesEnabled,
    required this.batteryOptimizationExempt,
  });

  /// Reads the map sent by the native side. Throws [FormatException] on an
  /// unexpected shape.
  factory LocationStatus.fromMap(Map<Object?, Object?> map) {
    final sdk = map['sdkInt'];
    if (sdk is! int) throw FormatException('Invalid status: $map');
    return LocationStatus(
      sdkInt: sdk,
      location: PermissionState.fromWire(map['location']),
      preciseLocation: map['preciseLocation'] == true,
      backgroundLocation: PermissionState.fromWire(map['backgroundLocation']),
      notifications: PermissionState.fromWire(map['notifications']),
      locationServicesEnabled: map['locationServicesEnabled'] == true,
      batteryOptimizationExempt:
          PermissionState.fromWire(map['batteryOptimizationExempt']),
    );
  }

  /// `Build.VERSION.SDK_INT` of the device.
  final int sdkInt;

  /// `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` granted.
  final PermissionState location;

  /// `ACCESS_FINE_LOCATION` granted (precise, not approximate).
  final bool preciseLocation;

  /// `ACCESS_BACKGROUND_LOCATION` ("Allow all the time"), API 29+.
  final PermissionState backgroundLocation;

  /// Notifications enabled for the app (`POST_NOTIFICATIONS` on API 33+,
  /// the app notification switch before). When denied, the service still
  /// runs, but its notification only shows in the Task Manager.
  final PermissionState notifications;

  /// Location services switched on in system settings.
  final bool locationServicesEnabled;

  /// The app is exempt from battery optimisation (Doze), API 23+.
  final PermissionState batteryOptimizationExempt;

  /// Whether `BackgroundLocation.start` can deliver fixes: a location
  /// permission is granted and location services are on.
  bool get canStart =>
      location == PermissionState.granted && locationServicesEnabled;

  /// Whether fixes keep coming when the service is started or restarted
  /// while the app is not visible (after a reboot, for example). Needs the
  /// background permission on API 29+.
  bool get canTrackWhileClosed =>
      location == PermissionState.granted && backgroundLocation.isOk;
}

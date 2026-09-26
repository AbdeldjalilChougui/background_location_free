/// One position reported by Android's `LocationManager`.
///
/// Units are SI: metres, metres per second and degrees. Optional fields are
/// `null` when the provider did not report them.
class LocationFix {
  /// Creates a fix. Throws [ArgumentError] when [lat] or [lon] is out of
  /// range.
  LocationFix({
    required this.lat,
    required this.lon,
    required this.time,
    this.accuracy,
    this.altitude,
    this.speed,
    this.bearing,
    this.provider,
    this.isMocked = false,
  }) {
    if (lat.isNaN || lat < -90 || lat > 90) {
      throw ArgumentError.value(lat, 'lat', 'must be within [-90, 90]');
    }
    if (lon.isNaN || lon < -180 || lon > 180) {
      throw ArgumentError.value(lon, 'lon', 'must be within [-180, 180]');
    }
  }

  /// Reads the map sent by the native side (see [toMap]). Throws
  /// [FormatException] when a required field is missing, has the wrong type
  /// or is out of range.
  factory LocationFix.fromMap(Map<Object?, Object?> map) {
    final lat = map['lat'];
    final lon = map['lon'];
    final time = map['time'];
    if (lat is! num || lon is! num || time is! int) {
      throw FormatException('Invalid location fix: $map');
    }
    double? opt(String key) {
      final v = map[key];
      if (v == null) return null;
      if (v is! num) throw FormatException('Invalid location fix $key: $v');
      return v.toDouble();
    }

    final provider = map['provider'];
    try {
      return LocationFix(
        lat: lat.toDouble(),
        lon: lon.toDouble(),
        time: DateTime.fromMillisecondsSinceEpoch(time, isUtc: true),
        accuracy: opt('accuracy'),
        altitude: opt('altitude'),
        speed: opt('speed'),
        bearing: opt('bearing'),
        provider: provider is String ? provider : null,
        isMocked: map['isMocked'] == true,
      );
    } on ArgumentError catch (e) {
      throw FormatException('Invalid location fix: ${e.message}');
    }
  }

  /// Latitude in degrees, `[-90, 90]`.
  final double lat;

  /// Longitude in degrees, `[-180, 180]`.
  final double lon;

  /// Horizontal accuracy radius in metres (68% confidence), or `null`.
  final double? accuracy;

  /// Altitude above the WGS-84 ellipsoid in metres, or `null`.
  final double? altitude;

  /// Ground speed in metres per second, or `null`.
  final double? speed;

  /// Bearing in degrees clockwise from true north, `[0, 360)`, or `null`.
  final double? bearing;

  /// When the fix was produced (UTC wall-clock time of the fix).
  final DateTime time;

  /// Name of the `LocationManager` provider (`gps`, `network`, `fused`,
  /// `passive`), or `null` when unknown.
  final String? provider;

  /// Whether Android flagged the fix as coming from a mock location
  /// provider (`Location.isMock()` on API 31+, `isFromMockProvider()`
  /// before).
  final bool isMocked;

  /// The wire form used by the platform channels. [time] is milliseconds
  /// since the epoch; `null` fields are omitted.
  Map<String, Object?> toMap() => <String, Object?>{
        'lat': lat,
        'lon': lon,
        if (accuracy != null) 'accuracy': accuracy,
        if (altitude != null) 'altitude': altitude,
        if (speed != null) 'speed': speed,
        if (bearing != null) 'bearing': bearing,
        'time': time.millisecondsSinceEpoch,
        if (provider != null) 'provider': provider,
        'isMocked': isMocked,
      };

  @override
  bool operator ==(Object other) =>
      other is LocationFix &&
      other.lat == lat &&
      other.lon == lon &&
      other.accuracy == accuracy &&
      other.altitude == altitude &&
      other.speed == speed &&
      other.bearing == bearing &&
      other.time.isAtSameMomentAs(time) &&
      other.provider == provider &&
      other.isMocked == isMocked;

  @override
  int get hashCode => Object.hash(lat, lon, accuracy, altitude, speed, bearing,
      time.millisecondsSinceEpoch, provider, isMocked);

  @override
  String toString() => 'LocationFix($lat, $lon, acc: $accuracy, '
      'provider: $provider, at: ${time.toIso8601String()})';
}

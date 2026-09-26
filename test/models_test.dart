import 'package:background_location_free/background_location_free.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('LocationFix', () {
    test('parses every field from the native map', () {
      final fix = LocationFix.fromMap(<Object?, Object?>{
        'lat': 36.75,
        'lon': 3.06,
        'accuracy': 4.5,
        'altitude': 120.0,
        'speed': 1.25,
        'bearing': 270.0,
        'time': 1767225600000,
        'provider': 'gps',
        'isMocked': true,
      });
      expect(fix.lat, 36.75);
      expect(fix.lon, 3.06);
      expect(fix.accuracy, 4.5);
      expect(fix.altitude, 120.0);
      expect(fix.speed, 1.25);
      expect(fix.bearing, 270.0);
      expect(fix.time, DateTime.utc(2026));
      expect(fix.time.isUtc, isTrue);
      expect(fix.provider, 'gps');
      expect(fix.isMocked, isTrue);
    });

    test('accepts ints for doubles and leaves missing fields null', () {
      final fix = LocationFix.fromMap(<Object?, Object?>{
        'lat': 1,
        'lon': 2,
        'time': 0,
      });
      expect(fix.lat, 1.0);
      expect(fix.lon, 2.0);
      expect(fix.accuracy, isNull);
      expect(fix.altitude, isNull);
      expect(fix.speed, isNull);
      expect(fix.bearing, isNull);
      expect(fix.provider, isNull);
      expect(fix.isMocked, isFalse);
      expect(fix.time, DateTime.fromMillisecondsSinceEpoch(0, isUtc: true));
    });

    test('rejects a map without coordinates or time', () {
      expect(() => LocationFix.fromMap(<Object?, Object?>{'lat': 1, 'time': 0}),
          throwsFormatException);
      expect(() => LocationFix.fromMap(<Object?, Object?>{'lat': 1, 'lon': 2}),
          throwsFormatException);
      expect(
          () => LocationFix.fromMap(
              <Object?, Object?>{'lat': 'x', 'lon': 2, 'time': 0}),
          throwsFormatException);
    });

    test('rejects out-of-range coordinates', () {
      expect(
          () => LocationFix.fromMap(
              <Object?, Object?>{'lat': 91, 'lon': 0, 'time': 0}),
          throwsFormatException);
      expect(
          () => LocationFix.fromMap(
              <Object?, Object?>{'lat': 0, 'lon': -181, 'time': 0}),
          throwsFormatException);
    });

    test('toMap round-trips', () {
      final fix = LocationFix(
        lat: 10,
        lon: 20,
        time: DateTime.utc(2026, 9, 1),
        accuracy: 3,
        speed: 2,
        bearing: 90,
        altitude: 5,
        provider: 'network',
        isMocked: true,
      );
      expect(LocationFix.fromMap(fix.toMap()), fix);
      expect(fix.hashCode, LocationFix.fromMap(fix.toMap()).hashCode);
    });
  });

  group('LocationConfig', () {
    const notification = NotificationConfig(title: 'Tracking', text: 'On');

    test('serialises every field with milliseconds', () {
      final config = LocationConfig(
        interval: const Duration(seconds: 10),
        fastestInterval: const Duration(seconds: 2),
        minDistanceMeters: 15,
        priority: LocationPriority.balanced,
        notification: const NotificationConfig(
          title: 'Trip',
          text: 'Recording',
          iconResource: 'ic_stat_trip',
          channelId: 'trips',
          channelName: 'Trips',
          color: 0xFF2196F3,
          ongoing: false,
        ),
        stopOnTaskRemoved: false,
        restartOnBoot: true,
        bufferCapacity: 50,
      );
      expect(config.toMap(), <String, Object?>{
        'intervalMs': 10000,
        'fastestIntervalMs': 2000,
        'minDistanceMeters': 15.0,
        'priority': 'balanced',
        'stopOnTaskRemoved': false,
        'restartOnBoot': true,
        'bufferCapacity': 50,
        'notification': <String, Object?>{
          'title': 'Trip',
          'text': 'Recording',
          'iconResource': 'ic_stat_trip',
          'channelId': 'trips',
          'channelName': 'Trips',
          'color': 0xFF2196F3,
          'ongoing': false,
        },
      });
    });

    test('defaults: 5 s interval, fastest = interval, high accuracy', () {
      final config = LocationConfig(notification: notification);
      final map = config.toMap();
      expect(map['intervalMs'], 5000);
      expect(map['fastestIntervalMs'], 5000);
      expect(map['minDistanceMeters'], 0.0);
      expect(map['priority'], 'highAccuracy');
      expect(map['stopOnTaskRemoved'], isTrue);
      expect(map['restartOnBoot'], isFalse);
      expect(map['bufferCapacity'], 1000);
      final n = map['notification']! as Map<String, Object?>;
      expect(n['channelId'], 'background_location');
      expect(n['channelName'], 'Background location');
      expect(n['iconResource'], isNull);
      expect(n['color'], isNull);
      expect(n['ongoing'], isTrue);
    });

    test('rejects invalid values', () {
      expect(
          () => LocationConfig(
              notification: notification, interval: Duration.zero),
          throwsArgumentError);
      expect(
          () => LocationConfig(
                notification: notification,
                interval: const Duration(seconds: 1),
                fastestInterval: const Duration(seconds: 2),
              ),
          throwsArgumentError);
      expect(
          () => LocationConfig(
              notification: notification,
              fastestInterval: const Duration(milliseconds: -1)),
          throwsArgumentError);
      expect(
          () =>
              LocationConfig(notification: notification, minDistanceMeters: -1),
          throwsArgumentError);
      expect(
          () => LocationConfig(
              notification: notification, minDistanceMeters: double.nan),
          throwsArgumentError);
      expect(
          () => LocationConfig(notification: notification, bufferCapacity: 0),
          throwsArgumentError);
      expect(
          () => LocationConfig(
              notification: const NotificationConfig(title: '', text: 'x')),
          throwsArgumentError);
      expect(
          () => LocationConfig(
              notification: const NotificationConfig(
                  title: 't', text: 'x', channelId: '')),
          throwsArgumentError);
    });

    test('priority wire names match the native side', () {
      expect(LocationPriority.values.map((p) => p.wireName),
          ['highAccuracy', 'balanced', 'lowPower', 'passive']);
    });
  });

  group('LocationStatus', () {
    test('maps every native field', () {
      final status = LocationStatus.fromMap(<Object?, Object?>{
        'sdkInt': 34,
        'location': 'granted',
        'preciseLocation': false,
        'backgroundLocation': 'denied',
        'notifications': 'granted',
        'locationServicesEnabled': true,
        'batteryOptimizationExempt': 'notApplicable',
      });
      expect(status.sdkInt, 34);
      expect(status.location, PermissionState.granted);
      expect(status.preciseLocation, isFalse);
      expect(status.backgroundLocation, PermissionState.denied);
      expect(status.notifications, PermissionState.granted);
      expect(status.locationServicesEnabled, isTrue);
      expect(status.batteryOptimizationExempt, PermissionState.notApplicable);
      expect(status.canStart, isTrue);
      expect(status.canTrackWhileClosed, isFalse);
    });

    test('background tracking needs the background grant on API 29+', () {
      LocationStatus make(String bg, int sdk) =>
          LocationStatus.fromMap(<Object?, Object?>{
            'sdkInt': sdk,
            'location': 'granted',
            'preciseLocation': true,
            'backgroundLocation': bg,
            'notifications': 'granted',
            'locationServicesEnabled': true,
            'batteryOptimizationExempt': 'granted',
          });
      expect(make('granted', 34).canTrackWhileClosed, isTrue);
      expect(make('notApplicable', 28).canTrackWhileClosed, isTrue);
      expect(make('denied', 34).canTrackWhileClosed, isFalse);
    });

    test('cannot start without a location grant or with services off', () {
      final status = LocationStatus.fromMap(<Object?, Object?>{
        'sdkInt': 30,
        'location': 'denied',
        'preciseLocation': false,
        'backgroundLocation': 'denied',
        'notifications': 'denied',
        'locationServicesEnabled': false,
        'batteryOptimizationExempt': 'denied',
      });
      expect(status.canStart, isFalse);
    });

    test('rejects unknown permission states', () {
      expect(() => PermissionState.fromWire('maybe'), throwsFormatException);
      expect(PermissionState.fromWire('notApplicable'),
          PermissionState.notApplicable);
    });
  });
}

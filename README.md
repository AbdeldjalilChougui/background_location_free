# background_location_free

Keep receiving location on Android after the user leaves your app, using a
foreground service and the framework `LocationManager`. MIT licensed, no
licence key and no Google Play services.

[![pub package](https://img.shields.io/pub/v/background_location_free.svg)](https://pub.dev/packages/background_location_free)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Android only; iOS not supported.** On other platforms every call throws
`MissingPluginException`.

Why this package exists: `flutter_background_geolocation` is maintained, but
Android release builds need a paid licence. `background_locator_2` has had no
release since 2023. This package does one job, a location foreground service,
and nothing else.

## Features

- Foreground service of type `location` with a notification you configure
  (title, text, icon, channel, colour, ongoing). You can change the title and
  text while it runs.
- `LocationManager` providers only (`gps`, `network`, `passive`), so it works
  on de-Googled devices. There is no Play services dependency.
- `Stream<LocationFix>` with latitude, longitude, accuracy, altitude, speed,
  bearing, time, provider and a mock flag (`Location.isMock()` on API 31+,
  `isFromMockProvider()` before).
- A native interval and distance filter (`fastestInterval`,
  `minDistanceMeters`), because `LocationManager` treats both as hints.
- A native ring buffer in app storage. It keeps fixes while no Dart listener
  is attached; `drainBuffered()` empties it.
- Headless delivery (**experimental**). A top-level Dart callback runs in a
  background `FlutterEngine` when the UI is gone.
- `status()` reports location, precise, background, notification, location
  services and battery-optimisation state.
- Optional restart after a reboot or an app update (`restartOnBoot`).
- `lastKnown()` returns the newest cached fix across providers.

## Install

```yaml
dependencies:
  background_location_free: ^0.1.0
```

## Manifest guide

The plugin's own manifest already adds `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_LOCATION`, the service with
`android:foregroundServiceType="location"`, and a disabled boot receiver. Your
app adds the rest in `android/app/src/main/AndroidManifest.xml`:

1. **Location (required).** Declare both. On Android 12+ the user can grant
   approximate only, and that still works.

   ```xml
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
   <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
   ```

2. **Notifications (recommended, Android 13+).** Without this permission the
   service still runs, but its notification only shows in the Task Manager,
   not in the drawer.

   ```xml
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   ```

3. **Background location (only if you need it).** You need it when the service
   is started or restarted while no activity is visible: after a reboot, after
   an app update, or when the system restarts a killed service. You do not
   need it when you start tracking from the UI and the service keeps running.
   It triggers a Play Console review (see below).

   ```xml
   <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
   ```

4. **Reboot restart (only for `restartOnBoot: true`).**

   ```xml
   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
   ```

5. **Notification icon (recommended).** Add a white-on-transparent drawable,
   for example `android/app/src/main/res/drawable/ic_stat_location.xml`, and
   pass `iconResource: 'ic_stat_location'`. Without one the app icon is used.

`minSdk` is 21. Build with `compileSdk` 34 or newer.

## Permissions flow

This package never asks for permissions. Request them with
[`permission_handler`](https://pub.dev/packages/permission_handler) (runtime
permissions) and
[`android_special_permissions`](https://pub.dev/packages/android_special_permissions)
(the battery optimisation and background location Settings screens).
`status()` tells you where you are.

```
 app visible
     |
     v
 status().location granted? --no--> request FINE + COARSE (runtime dialog)
     |                                     | denied -> explain; cannot track
     | yes                                 v
     v <-----------------------------------+
 status().locationServicesEnabled? --no--> ask the user to switch location on
     | yes
     v
 API 33+ and notifications denied? --yes--> request POST_NOTIFICATIONS
     |                                      (optional: service runs anyway)
     v
 need tracking when started from the background (reboot, sticky restart)?
     | yes                                   | no
     v                                       |
 in-app disclosure ("... even when the       |
 app is closed or not in use")               |
     |                                       |
 API 29: ACCESS_BACKGROUND_LOCATION dialog   |
 API 30+: send the user to Settings          |
   ("Allow all the time")                    |
     |                                       |
     v                                       v
 optional: ask for the battery-optimisation exemption
     |
     v
 BackgroundLocation.start(config)   <- while an activity is visible
```

## Usage

```dart
import 'package:background_location_free/background_location_free.dart';

Future<void> startTracking() async {
  final status = await BackgroundLocation.status();
  if (!status.canStart) return; // request permissions first

  await BackgroundLocation.start(LocationConfig(
    interval: const Duration(seconds: 5),
    fastestInterval: const Duration(seconds: 2),
    minDistanceMeters: 10,
    priority: LocationPriority.highAccuracy,
    notification: const NotificationConfig(
      title: 'Recording your run',
      text: 'Tap to open',
      iconResource: 'ic_stat_location',
      channelId: 'tracking',
      channelName: 'Tracking',
      color: 0xFF2E7D32,
    ),
    stopOnTaskRemoved: false,
  ));

  final sub = BackgroundLocation.locations.listen((fix) {
    print('${fix.lat}, ${fix.lon} ±${fix.accuracy} m via ${fix.provider}');
  });

  await BackgroundLocation.updateNotification(text: '3.2 km so far');

  // Later:
  await sub.cancel();
  await BackgroundLocation.stop();
}

Future<void> onAppResumed() async {
  // Fixes recorded while nothing listened, oldest first.
  final missed = await BackgroundLocation.drainBuffered();
  print('caught up on ${missed.length} fixes');
}
```

Error codes on `BackgroundLocationException.code` are `permission_denied`,
`start_not_allowed` (Android 12+ refused a start from the background),
`no_provider`, `invalid_config`, `not_running` and `start_timeout`. The stream
can also emit `provider_disabled` when the user turns a provider off.

### Where fixes go

For each fix that passes the filter, the service picks one destination:

1. If a Dart listener is attached to `locations`, the fix goes there.
2. Otherwise, if a headless callback is registered, it runs the callback.
3. Otherwise the fix goes to the ring buffer (`bufferCapacity`, default 1000;
   the oldest fixes are dropped first).

Buffered fixes are not replayed on the stream automatically. Call
`drainBuffered()` when your UI comes back.

### Headless callback (experimental)

```dart
@pragma('vm:entry-point')
Future<void> onBackgroundFix(LocationFix fix) async {
  // Runs in a separate isolate: no access to UI state. Keep it short.
  // Persist or upload the fix here.
}

await BackgroundLocation.registerHeadlessCallback(onBackgroundFix);
```

How it works: `registerHeadlessCallback` stores two raw callback handles from
`PluginUtilities.getCallbackHandle` in shared preferences. One is for the
package's own dispatcher and one is for your function. When a fix arrives and
no listener is attached, the service starts a `FlutterEngine`, looks up the
dispatcher with `FlutterCallbackInformation.lookupCallbackInformation`, and
runs it. The dispatcher sends `ready`, and the service then sends each fix
together with your callback's handle. If the handle does not resolve, or the
callback throws, the fix goes to the ring buffer, so it is not lost. When a
UI listener attaches again, the background engine is destroyed.

This is marked experimental. The handle plumbing and the Dart dispatcher are
unit-tested, but the engine start-up has not been run on a device (see
Limitations). Callback handles can change between app builds. Call
`registerHeadlessCallback` on every app start.

### Adapter for throttled_location

The sibling pure-Dart package
[`throttled_location`](https://pub.dev/packages/throttled_location) decides
when to send a position and what to keep while offline. This package does not
depend on it. To connect the two, add both packages to your app and use this
adapter:

```dart
import 'package:background_location_free/background_location_free.dart' as bg;
import 'package:throttled_location/throttled_location.dart';

final backgroundSource = LocationSource.fromStream(
  () => bg.BackgroundLocation.locations.map(
    (f) => LocationFix(
      lat: f.lat,
      lon: f.lon,
      accuracy: f.accuracy,
      speed: f.speed,
      heading: f.bearing,
      altitude: f.altitude,
      timestamp: f.time,
      isMocked: f.isMocked,
    ),
  ),
);
```

The prefix is needed because both packages export a `LocationFix`. Leave
`minDistanceMeters` at 0 and let `throttled_location` do the throttling.

## Android rules this package follows

- **Service type.** From Android 14 (API 34) every foreground service must
  declare a type in the manifest and hold the matching permission. For
  `location` that is `FOREGROUND_SERVICE_LOCATION`, and the app must hold
  `ACCESS_COARSE_LOCATION` or `ACCESS_FINE_LOCATION`.
  [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#location)
- **Starting from the background.** Apps targeting Android 12 (API 31) or
  higher cannot start a foreground service from the background, apart from
  listed exemptions. The system throws
  `ForegroundServiceStartNotAllowedException`, which this package reports as
  `start_not_allowed`. `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are exempt.
  [Background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- **While-in-use.** "You cannot create a `location` foreground service while
  your app is in the background, unless you've been granted the
  `ACCESS_BACKGROUND_LOCATION` runtime permission."
  [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#location)
- **Boot receivers on Android 15.** Boot receivers may not launch `dataSync`,
  `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` or `microphone`
  services. `location` is not on that list.
  [Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed)
- **Notifications.** A foreground service must show a notification. On
  Android 13+ without `POST_NOTIFICATIONS`, the notice appears in the Task
  Manager but not in the drawer.
  [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions)
  From Android 14 users can dismiss the notification even if it is `ongoing`
  (except while the phone is locked); the service keeps running.
  [Android 14 behaviour changes](https://developer.android.com/about/versions/14/behavior-changes-all)
- **Background location.** On Android 11+ "Allow all the time" is not in the
  dialog. The user grants it on a Settings page, and you request it separately
  after foreground location.
  [Request background location](https://developer.android.com/develop/sensors-and-location/location/permissions/background)

## Battery optimisation

Doze and OEM battery managers can delay or kill even a foreground service,
and some vendors are aggressive about it. `status().batteryOptimizationExempt`
tells you whether the app is exempt. Play policy forbids asking directly for
the exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) "unless the core
function of the app is adversely affected". Only ask when tracking is the
main purpose of the app, and explain why first. See
[Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases).
Vendor-specific settings are collected at
[dontkillmyapp.com](https://dontkillmyapp.com/).

## Google Play declarations

- **Foreground service types.** Apps targeting Android 14+ declare each
  foreground service type in Play Console (App content, "Foreground service
  permissions"). For each type you give a description, the user impact if it
  is deferred, and a demonstration video. For `location` the listed use cases
  include user-initiated location sharing and navigation.
  [Play Console help](https://support.google.com/googleplay/android-developer/answer/13392821)
- **Background location.** If you declare `ACCESS_BACKGROUND_LOCATION`, you
  must fill in the location permissions declaration (App content > Sensitive
  app permissions). It must describe **one** feature that needs background
  access and be its core function. You also need an in-app prominent
  disclosure before the permission prompt, a video of at most 30 seconds, and
  a privacy policy that covers location.
  [Play policy on background location](https://support.google.com/googleplay/android-developer/answer/9799150)
- Apps that start tracking only from the UI and never need a start from the
  background can leave out `ACCESS_BACKGROUND_LOCATION`. That avoids the
  second declaration. The foreground service declaration is still required.

## What this does NOT do

- No geofencing.
- No activity recognition, motion detection or stop detection.
- No iOS (and no web or desktop).
- No Google Play services Fused Location Provider. Supporting it would mean a
  hard `play-services-location` dependency or reflection over it. Neither is
  clean, so this package uses `LocationManager` only.
- No permission requests, no HTTP upload, no persistence beyond the ring
  buffer.

## Limitations

- The Kotlin logic (config parsing, notification data, the ring buffer, the
  distance and interval filter, API-level branching, routing and the plugin
  manifest) has JUnit tests, and the Dart API has mocked-channel tests.
- The example app has an emulator integration test (`example/integration_test/`,
  run with `dart run tool/run_on_device.dart <applicationId>` from `example/`).
  It checks `status()` before and after `pm grant`, the `permission_denied`,
  `not_running` and `start_not_allowed` errors, that the service runs in the
  foreground with type `location`, fixes injected with `adb emu geo fix`
  (in order, `isMocked` false), a `cmd location` test provider (`isMocked`
  true, and `provider_disabled` on the stream while it replaces `gps`),
  `updateNotification` in `dumpsys notification`, buffering with
  `drainBuffered()`, the headless callback while the UI is in the background,
  and `stop()`.
- Not tested end to end: `restartOnBoot` after a real reboot, a sticky restart
  after the process is killed, and the headless callback after the task is
  swiped away from Recents (that would stop the test itself).
- `highAccuracy` uses the `gps` provider only (with `network` as a fallback
  when GPS is absent), so indoor fixes can be slow. `balanced` and `lowPower`
  use `network`, which some de-Googled ROMs lack or back with a separate
  provider app.
- One provider per session. There is no merging of GPS and network fixes.
- Headless mode is experimental (see above).
- If the process dies and the system restarts the sticky service while no
  activity is visible, location only flows with the background permission on
  Android 10+.
- `restartOnBoot` restarts only when tracking was running and the app holds
  location permission, plus the background permission on Android 10+. It
  does not survive a force stop: Android does not deliver `BOOT_COMPLETED` to
  force-stopped apps until the user opens them again.
- The event stream is shared: every access to `locations` returns the same
  broadcast stream.

**Tested on:** Android Emulator, Pixel 10 image, API 37 (physical phone not tested).

## License

MIT. See [LICENSE](LICENSE).

Made by [Abdeldjalil Chougui](https://abdeldjalilchougui.biz/).

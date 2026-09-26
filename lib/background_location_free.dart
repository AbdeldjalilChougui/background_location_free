/// Background location on Android through a foreground service and the
/// framework `LocationManager`. No Google Play services needed.
library;

export 'src/background_location.dart';
export 'src/exception.dart';
export 'src/headless.dart' show HeadlessLocationCallback, handleHeadlessCall;
export 'src/location_config.dart';
export 'src/location_fix.dart';
export 'src/location_status.dart';

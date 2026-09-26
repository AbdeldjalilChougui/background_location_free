package dev.abdeldjalilchougui.background_location_free

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Reads permissions and settings for `BackgroundLocation.status()`. */
internal object StatusReader {
    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun read(context: Context): Map<String, Any?> {
        val sdk = Build.VERSION.SDK_INT
        val fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val any = fine || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = sdk >= 29 && granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val exempt = sdk >= 23 &&
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        return mapOf(
            "sdkInt" to sdk,
            "location" to if (any) "granted" else "denied",
            "preciseLocation" to fine,
            "backgroundLocation" to ApiLevels.backgroundLocationState(sdk, background),
            "notifications" to
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "granted" else "denied",
            "locationServicesEnabled" to LocationManagerCompat.isLocationEnabled(lm),
            "batteryOptimizationExempt" to ApiLevels.batteryOptimizationState(sdk, exempt),
        )
    }
}

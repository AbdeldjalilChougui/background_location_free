package dev.abdeldjalilchougui.background_location_free

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restarts tracking after a reboot or an app update when the app opted in
 * (`restartOnBoot`) and tracking was running. Disabled in the manifest and
 * enabled at runtime only while opted in. Needs `RECEIVE_BOOT_COMPLETED` in
 * the app manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val cfg = Prefs.loadConfig(context)
        val decision = ApiLevels.bootRestart(
            sdk = Build.VERSION.SDK_INT,
            optedIn = cfg?.restartOnBoot == true,
            wasRunning = Prefs.wasRunning(context),
            hasLocationPermission = StatusReader.hasLocationPermission(context),
            hasBackgroundPermission = Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
        if (decision != BootRestart.START) {
            Log.i(TAG, "Not restarting after ${intent.action}: $decision")
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Restart after ${intent.action} refused", e)
        }
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

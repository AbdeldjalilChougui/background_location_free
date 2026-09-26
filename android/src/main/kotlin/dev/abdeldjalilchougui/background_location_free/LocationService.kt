package dev.abdeldjalilchougui.background_location_free

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat

/**
 * Foreground service (type `location`) that requests updates from the
 * framework [LocationManager] and hands each fix to [LocationHub].
 */
class LocationService : Service() {
    private var config: TrackingConfig? = null
    private var filter: FixFilter? = null
    private var listener: LocationListenerCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is a sticky restart by the system: reuse the stored config.
        val cfg = Prefs.loadConfig(this)
        if (cfg == null) {
            LocationHub.reportStart(BackgroundLocationFreePlugin.ERR_INVALID_CONFIG, "No stored configuration")
            stopSelf()
            return START_NOT_STICKY
        }
        val error = promote(cfg)
        if (error != null) {
            LocationHub.reportStart(error.first, error.second)
            stopSelf()
            return START_NOT_STICKY
        }
        config = cfg
        val provider = requestUpdates(cfg)
        if (provider == null) {
            LocationHub.reportStart(BackgroundLocationFreePlugin.ERR_NO_PROVIDER, "No location provider fits ${cfg.priority.wireName}")
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        LocationHub.reportStart(null, null)
        return START_STICKY
    }

    /** Calls startForeground; returns (code, message) on failure. */
    private fun promote(cfg: TrackingConfig): Pair<String, String>? {
        return try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID,
                Notifications.build(this, cfg.notification),
                ApiLevels.foregroundServiceType(Build.VERSION.SDK_INT),
            )
            null
        } catch (e: SecurityException) {
            // API 34+: location permission missing, or not eligible for while-in-use access.
            BackgroundLocationFreePlugin.ERR_PERMISSION_DENIED to (e.message ?: "SecurityException")
        } catch (e: IllegalStateException) {
            // API 31+: ForegroundServiceStartNotAllowedException extends IllegalStateException.
            BackgroundLocationFreePlugin.ERR_START_NOT_ALLOWED to (e.message ?: e.toString())
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(cfg: TrackingConfig): String? {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listener?.let { LocationManagerCompat.removeUpdates(manager, it) }
        val provider = ApiLevels.provider(cfg.priority, manager.allProviders.toSet()) ?: return null
        filter = FixFilter(cfg.fastestIntervalMs, cfg.minDistanceMeters)
        val quality = when (cfg.priority) {
            Priority.HIGH_ACCURACY -> LocationRequestCompat.QUALITY_HIGH_ACCURACY
            Priority.BALANCED -> LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
            Priority.LOW_POWER, Priority.PASSIVE -> LocationRequestCompat.QUALITY_LOW_POWER
        }
        val request = LocationRequestCompat.Builder(cfg.intervalMs)
            .setMinUpdateIntervalMillis(cfg.fastestIntervalMs)
            .setMinUpdateDistanceMeters(cfg.minDistanceMeters)
            .setQuality(quality)
            .build()
        val l = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) = onFix(location)

            override fun onProviderDisabled(provider: String) {
                LocationHub.sendError("provider_disabled", "Location provider '$provider' was disabled")
            }

            override fun onProviderEnabled(provider: String) {}
        }
        return try {
            LocationManagerCompat.requestLocationUpdates(manager, provider, request, l, Looper.getMainLooper())
            listener = l
            provider
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission revoked", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Provider $provider rejected the request", e)
            null
        }
    }

    private fun onFix(location: Location) {
        val fix = toRecord(location)
        if (filter?.accept(fix) != false) LocationHub.deliver(this, fix)
    }

    /** Called on the main thread by the plugin. */
    internal fun updateNotification(title: String?, text: String?) {
        val cfg = config ?: return
        val updated = cfg.copy(notification = cfg.notification.updated(title, text))
        config = updated
        Prefs.saveConfig(this, updated)
        Notifications.show(this, updated.notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (config?.stopOnTaskRemoved != false) {
            Prefs.setRunning(this, false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        listener?.let {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            LocationManagerCompat.removeUpdates(manager, it)
        }
        listener = null
        LocationHub.onServiceStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        internal var instance: LocationService? = null
            private set

        @Volatile
        internal var running = false
            private set

        @Suppress("DEPRECATION")
        @SuppressLint("NewApi")
        internal fun toRecord(location: Location): FixRecord = FixRecord(
            lat = location.latitude,
            lon = location.longitude,
            timeMs = location.time,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            provider = location.provider,
            isMocked = ApiLevels.isMock(
                Build.VERSION.SDK_INT,
                { location.isMock },
                { location.isFromMockProvider },
            ),
        )
    }
}

package dev.abdeldjalilchougui.background_location_free

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** Flutter entry point: one instance per engine (UI and headless). */
class BackgroundLocationFreePlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var context: Context
    private var channel: MethodChannel? = null
    private var events: EventChannel? = null
    private var sink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL).also { it.setMethodCallHandler(this) }
        events = EventChannel(binding.binaryMessenger, EVENT_CHANNEL).also { it.setStreamHandler(this) }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        LocationHub.removeListener(sink)
        sink = null
        channel?.setMethodCallHandler(null)
        events?.setStreamHandler(null)
        channel = null
        events = null
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        LocationHub.removeListener(sink)
        sink = events
        LocationHub.addListener(events)
    }

    override fun onCancel(arguments: Any?) {
        LocationHub.removeListener(sink)
        sink = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "start" -> start(call, result)
            "stop" -> {
                Prefs.setRunning(context, false)
                BootReceiver.setEnabled(context, false)
                context.stopService(Intent(context, LocationService::class.java))
                result.success(null)
            }
            "isRunning" -> result.success(LocationService.running)
            "updateNotification" -> {
                val service = LocationService.instance
                if (service == null || !LocationService.running) {
                    result.error(ERR_NOT_RUNNING, "The service is not running", null)
                    return
                }
                try {
                    service.updateNotification(call.argument<String>("title"), call.argument<String>("text"))
                    result.success(null)
                } catch (e: IllegalArgumentException) {
                    result.error(ERR_INVALID_CONFIG, e.message, null)
                }
            }
            "lastKnown" -> lastKnown(result)
            "drainBuffered" -> result.success(LocationHub.buffer(context).drain().map { it.toMap() })
            "bufferedCount" -> result.success(LocationHub.buffer(context).size())
            "status" -> result.success(StatusReader.read(context))
            "registerHeadless" -> {
                val dispatcher = (call.argument<Any>("dispatcherHandle") as? Number)?.toLong()
                val callback = (call.argument<Any>("callbackHandle") as? Number)?.toLong()
                if (dispatcher == null || callback == null) {
                    result.error(ERR_INVALID_CONFIG, "dispatcherHandle and callbackHandle are required", null)
                    return
                }
                LocationHub.stopHeadless()
                Prefs.setHeadless(context, dispatcher, callback)
                result.success(null)
            }
            "unregisterHeadless" -> {
                Prefs.clearHeadless(context)
                LocationHub.stopHeadless()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun start(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments as? Map<*, *>
        val cfg = try {
            TrackingConfig.fromMap(args ?: emptyMap<String, Any?>())
        } catch (e: IllegalArgumentException) {
            result.error(ERR_INVALID_CONFIG, e.message, null)
            return
        }
        if (!StatusReader.hasLocationPermission(context)) {
            result.error(ERR_PERMISSION_DENIED, "ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is not granted", null)
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ApiLevels.provider(cfg.priority, lm.allProviders.toSet()) == null) {
            result.error(ERR_NO_PROVIDER, "No location provider fits ${cfg.priority.wireName}", null)
            return
        }
        Prefs.saveConfig(context, cfg)
        LocationHub.buffer(context).resize(cfg.bufferCapacity)

        var replied = false
        val timeout = Runnable {
            if (!replied) {
                replied = true
                result.error(ERR_START_TIMEOUT, "The service did not report back in time", null)
            }
        }
        LocationHub.awaitStart { code, message ->
            LocationHub.mainHandler.removeCallbacks(timeout)
            if (replied) return@awaitStart
            replied = true
            if (code == null) {
                Prefs.setRunning(context, true)
                BootReceiver.setEnabled(context, cfg.restartOnBoot)
                result.success(null)
            } else {
                Prefs.setRunning(context, false)
                result.error(code, message, null)
            }
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
            LocationHub.mainHandler.postDelayed(timeout, START_TIMEOUT_MS)
        } catch (e: SecurityException) {
            LocationHub.reportStart(ERR_PERMISSION_DENIED, e.message)
        } catch (e: IllegalStateException) {
            // API 31+: ForegroundServiceStartNotAllowedException; older: background start limits.
            LocationHub.reportStart(ERR_START_NOT_ALLOWED, e.message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(result: MethodChannel.Result) {
        if (!StatusReader.hasLocationPermission(context)) {
            result.error(ERR_PERMISSION_DENIED, "No location permission", null)
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val newest = lm.allProviders.mapNotNull { p ->
            try {
                lm.getLastKnownLocation(p)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }
        result.success(newest?.let { LocationService.toRecord(it).toMap() })
    }

    companion object {
        const val METHOD_CHANNEL = "dev.abdeldjalilchougui/background_location_free"
        const val EVENT_CHANNEL = "dev.abdeldjalilchougui/background_location_free/locations"
        const val ERR_PERMISSION_DENIED = "permission_denied"
        const val ERR_START_NOT_ALLOWED = "start_not_allowed"
        const val ERR_INVALID_CONFIG = "invalid_config"
        const val ERR_NOT_RUNNING = "not_running"
        const val ERR_NO_PROVIDER = "no_provider"
        const val ERR_START_TIMEOUT = "start_timeout"
        const val START_TIMEOUT_MS = 10_000L
    }
}

package dev.abdeldjalilchougui.background_location_free

import android.content.Context
import android.util.Log
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation

/**
 * Runs the Dart headless dispatcher in a background [FlutterEngine] and hands
 * it fixes. Fixes that cannot be delivered (unknown handle, Dart error, queue
 * full) go to [onUndelivered], which buffers them. Main thread only.
 */
internal class HeadlessRunner(
    private val context: Context,
    private val onUndelivered: (FixRecord) -> Unit,
) {
    private var engine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var ready = false
    private var failed = false
    private var callbackHandle = 0L
    private val pending = ArrayDeque<FixRecord>()

    fun deliver(fix: FixRecord, dispatcherHandle: Long, callbackHandle: Long) {
        this.callbackHandle = callbackHandle
        when {
            failed -> onUndelivered(fix)
            ready -> send(fix)
            pending.size >= MAX_PENDING -> onUndelivered(fix)
            else -> {
                pending.addLast(fix)
                start(dispatcherHandle)
            }
        }
    }

    private fun start(dispatcherHandle: Long) {
        if (engine != null) return
        try {
            val loader = FlutterInjector.instance().flutterLoader()
            if (!loader.initialized()) {
                loader.startInitialization(context)
                loader.ensureInitializationComplete(context, null)
            }
            val info = FlutterCallbackInformation.lookupCallbackInformation(dispatcherHandle)
            if (info == null) {
                fail("No Dart callback for the dispatcher handle; was the app updated?")
                return
            }
            val eng = FlutterEngine(context)
            val ch = MethodChannel(eng.dartExecutor.binaryMessenger, HEADLESS_CHANNEL)
            ch.setMethodCallHandler { call, result ->
                if (call.method == "ready") {
                    result.success(null)
                    ready = true
                    while (pending.isNotEmpty()) send(pending.removeFirst())
                } else {
                    result.notImplemented()
                }
            }
            channel = ch
            engine = eng
            eng.dartExecutor.executeDartCallback(
                DartExecutor.DartCallback(context.assets, loader.findAppBundlePath(), info),
            )
        } catch (e: Exception) {
            fail("Could not start the headless engine: $e")
        }
    }

    private fun send(fix: FixRecord) {
        val ch = channel ?: return onUndelivered(fix)
        ch.invokeMethod(
            "onFix",
            mapOf("callbackHandle" to callbackHandle, "fix" to fix.toMap()),
            object : MethodChannel.Result {
                override fun success(result: Any?) {}

                override fun error(code: String, message: String?, details: Any?) {
                    Log.w(TAG, "Headless callback failed ($code): $message")
                    onUndelivered(fix)
                }

                override fun notImplemented() {
                    onUndelivered(fix)
                }
            },
        )
    }

    private fun fail(reason: String) {
        Log.e(TAG, reason)
        failed = true
        while (pending.isNotEmpty()) onUndelivered(pending.removeFirst())
        destroy()
    }

    fun destroy() {
        channel?.setMethodCallHandler(null)
        channel = null
        engine?.destroy()
        engine = null
        ready = false
        while (pending.isNotEmpty()) onUndelivered(pending.removeFirst())
    }

    companion object {
        const val HEADLESS_CHANNEL = "dev.abdeldjalilchougui/background_location_free/headless"
        const val MAX_PENDING = 500
    }
}

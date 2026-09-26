package dev.abdeldjalilchougui.background_location_free

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel
import java.io.File

/**
 * Process-wide meeting point of the service and the plugin instances (one per
 * Flutter engine). Every method runs on the main thread.
 */
internal object LocationHub {
    private val listeners = LinkedHashSet<EventChannel.EventSink>()
    private var buffer: FileRingBuffer? = null
    private var headless: HeadlessRunner? = null
    private val startWaiters = ArrayList<(String?, String?) -> Unit>()
    val mainHandler = Handler(Looper.getMainLooper())

    fun buffer(context: Context): FileRingBuffer {
        buffer?.let { return it }
        val capacity = Prefs.loadConfig(context)?.bufferCapacity ?: TrackingConfig.DEFAULT_BUFFER_CAPACITY
        val file = File(context.applicationContext.filesDir, "background_location_free/buffer.txt")
        return FileRingBuffer(file, capacity).also { buffer = it }
    }

    fun addListener(sink: EventChannel.EventSink) {
        listeners.add(sink)
        // The UI is back: the background engine is no longer needed.
        headless?.destroy()
        headless = null
    }

    fun removeListener(sink: EventChannel.EventSink?) {
        if (sink != null) listeners.remove(sink)
    }

    /** Routes one fix: live listener, headless callback, or the buffer. */
    fun deliver(context: Context, fix: FixRecord) {
        val handles = Prefs.headlessHandles(context)
        when (FixRouter.route(listeners.isNotEmpty(), handles != null)) {
            Route.LISTENER -> {
                val map = fix.toMap()
                listeners.toList().forEach { it.success(map) }
            }
            Route.HEADLESS -> {
                val runner = headless ?: HeadlessRunner(context.applicationContext) { failed ->
                    buffer(context).append(failed)
                }.also { headless = it }
                runner.deliver(fix, handles!!.first, handles.second)
            }
            Route.BUFFER -> buffer(context).append(fix)
        }
    }

    fun sendError(code: String, message: String) {
        listeners.toList().forEach { it.error(code, message, null) }
    }

    fun onServiceStopped() {
        headless?.destroy()
        headless = null
    }

    fun stopHeadless() = onServiceStopped()

    /** Registers a one-shot callback for the outcome of the next service start. */
    fun awaitStart(callback: (code: String?, message: String?) -> Unit) {
        startWaiters.add(callback)
    }

    /** Called by the service: `code == null` means it is in the foreground. */
    fun reportStart(code: String?, message: String?) {
        val waiters = startWaiters.toList()
        startWaiters.clear()
        waiters.forEach { it(code, message) }
    }
}

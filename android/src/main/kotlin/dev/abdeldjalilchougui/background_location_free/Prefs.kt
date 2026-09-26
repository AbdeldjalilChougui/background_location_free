package dev.abdeldjalilchougui.background_location_free

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Persisted state: the last config, the "should be running" flag and headless handles. */
internal object Prefs {
    private const val NAME = "dev.abdeldjalilchougui.background_location_free"
    private const val KEY_CONFIG = "config"
    private const val KEY_RUNNING = "running"
    private const val KEY_DISPATCHER = "dispatcherHandle"
    private const val KEY_CALLBACK = "callbackHandle"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun saveConfig(context: Context, config: TrackingConfig) {
        sp(context).edit().putString(KEY_CONFIG, JSONObject(config.toMap()).toString()).apply()
    }

    fun loadConfig(context: Context): TrackingConfig? {
        val raw = sp(context).getString(KEY_CONFIG, null) ?: return null
        return try {
            TrackingConfig.fromMap(toMap(JSONObject(raw)))
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unreadable stored config", e)
            null
        }
    }

    fun setRunning(context: Context, running: Boolean) {
        sp(context).edit().putBoolean(KEY_RUNNING, running).apply()
    }

    fun wasRunning(context: Context): Boolean = sp(context).getBoolean(KEY_RUNNING, false)

    fun setHeadless(context: Context, dispatcherHandle: Long, callbackHandle: Long) {
        sp(context).edit()
            .putLong(KEY_DISPATCHER, dispatcherHandle)
            .putLong(KEY_CALLBACK, callbackHandle)
            .apply()
    }

    fun clearHeadless(context: Context) {
        sp(context).edit().remove(KEY_DISPATCHER).remove(KEY_CALLBACK).apply()
    }

    /** (dispatcher, callback) handles, or null when no headless callback is registered. */
    fun headlessHandles(context: Context): Pair<Long, Long>? {
        val p = sp(context)
        if (!p.contains(KEY_DISPATCHER) || !p.contains(KEY_CALLBACK)) return null
        return p.getLong(KEY_DISPATCHER, 0L) to p.getLong(KEY_CALLBACK, 0L)
    }

    private fun toMap(o: JSONObject): Map<String, Any?> {
        val out = HashMap<String, Any?>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = unwrap(o.get(k))
        }
        return out
    }

    private fun unwrap(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
        else -> v
    }
}

internal const val TAG = "BackgroundLocationFree"

package dev.abdeldjalilchougui.background_location_free

/** How hard to try for a fix; mirrors the Dart `LocationPriority`. */
internal enum class Priority(val wireName: String) {
    HIGH_ACCURACY("highAccuracy"),
    BALANCED("balanced"),
    LOW_POWER("lowPower"),
    PASSIVE("passive");

    companion object {
        fun fromWire(name: Any?): Priority =
            values().firstOrNull { it.wireName == name }
                ?: throw IllegalArgumentException("Unknown priority: $name")
    }
}

/** Plain-data description of the foreground service notification. */
internal data class NotificationSpec(
    val title: String,
    val text: String,
    val iconResource: String?,
    val channelId: String,
    val channelName: String,
    /** ARGB colour, or null for the system default. */
    val color: Int?,
    val ongoing: Boolean,
) {
    init {
        require(title.isNotEmpty()) { "notification title must not be empty" }
        require(channelId.isNotEmpty()) { "notification channelId must not be empty" }
        require(channelName.isNotEmpty()) { "notification channelName must not be empty" }
    }

    /** A copy with [title] and/or [text] replaced; null keeps the current value. */
    fun updated(title: String?, text: String?): NotificationSpec =
        copy(title = title ?: this.title, text = text ?: this.text)

    /**
     * Resolves the small icon. [lookup] is `Resources.getIdentifier(name, type, pkg)`,
     * which returns 0 when absent. Tries drawable, then mipmap, then [fallback] (the
     * app icon), then a framework icon.
     */
    fun resolveIcon(lookup: (name: String, type: String) -> Int, fallback: Int): Int {
        val name = iconResource
        if (name != null) {
            val drawable = lookup(name, "drawable")
            if (drawable != 0) return drawable
            val mipmap = lookup(name, "mipmap")
            if (mipmap != 0) return mipmap
        }
        return if (fallback != 0) fallback else FRAMEWORK_FALLBACK_ICON
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "text" to text,
        "iconResource" to iconResource,
        "channelId" to channelId,
        "channelName" to channelName,
        "color" to color?.let { it.toLong() and 0xFFFFFFFFL },
        "ongoing" to ongoing,
    )

    companion object {
        /** `android.R.drawable.ic_menu_mylocation`, used when the app has no icon. */
        val FRAMEWORK_FALLBACK_ICON: Int = android.R.drawable.ic_menu_mylocation

        fun fromMap(map: Map<*, *>): NotificationSpec {
            val color = when (val c = map["color"]) {
                null -> null
                is Number -> {
                    val v = c.toLong()
                    require(v in 0L..0xFFFFFFFFL) { "notification color must be 0xAARRGGBB" }
                    v.toInt()
                }
                else -> throw IllegalArgumentException("notification color must be an int")
            }
            return NotificationSpec(
                title = map["title"] as? String
                    ?: throw IllegalArgumentException("notification title is required"),
                text = map["text"] as? String ?: "",
                iconResource = map["iconResource"] as? String,
                channelId = map["channelId"] as? String ?: DEFAULT_CHANNEL_ID,
                channelName = map["channelName"] as? String ?: DEFAULT_CHANNEL_NAME,
                color = color,
                ongoing = map["ongoing"] as? Boolean ?: true,
            )
        }

        const val DEFAULT_CHANNEL_ID = "background_location"
        const val DEFAULT_CHANNEL_NAME = "Background location"
    }
}

/** Validated service configuration; mirrors the Dart `LocationConfig`. */
internal data class TrackingConfig(
    val intervalMs: Long,
    val fastestIntervalMs: Long,
    val minDistanceMeters: Float,
    val priority: Priority,
    val notification: NotificationSpec,
    val stopOnTaskRemoved: Boolean,
    val restartOnBoot: Boolean,
    val bufferCapacity: Int,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "intervalMs" to intervalMs,
        "fastestIntervalMs" to fastestIntervalMs,
        "minDistanceMeters" to minDistanceMeters.toDouble(),
        "priority" to priority.wireName,
        "stopOnTaskRemoved" to stopOnTaskRemoved,
        "restartOnBoot" to restartOnBoot,
        "bufferCapacity" to bufferCapacity,
        "notification" to notification.toMap(),
    )

    companion object {
        const val DEFAULT_BUFFER_CAPACITY = 1000
        const val MAX_BUFFER_CAPACITY = 100000

        /** Parses the channel map. Throws [IllegalArgumentException] when invalid. */
        fun fromMap(map: Map<*, *>): TrackingConfig {
            val interval = (map["intervalMs"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("intervalMs is required")
            require(interval > 0) { "intervalMs must be positive" }
            val fastest = (map["fastestIntervalMs"] as? Number)?.toLong() ?: interval
            require(fastest in 0..interval) { "fastestIntervalMs must be within [0, intervalMs]" }
            val distance = (map["minDistanceMeters"] as? Number)?.toDouble() ?: 0.0
            require(!distance.isNaN() && !distance.isInfinite() && distance >= 0) {
                "minDistanceMeters must be >= 0"
            }
            val capacity = (map["bufferCapacity"] as? Number)?.toInt() ?: DEFAULT_BUFFER_CAPACITY
            require(capacity in 1..MAX_BUFFER_CAPACITY) {
                "bufferCapacity must be within [1, $MAX_BUFFER_CAPACITY]"
            }
            val notification = map["notification"] as? Map<*, *>
                ?: throw IllegalArgumentException("notification is required")
            return TrackingConfig(
                intervalMs = interval,
                fastestIntervalMs = fastest,
                minDistanceMeters = distance.toFloat(),
                priority = map["priority"]?.let { Priority.fromWire(it) } ?: Priority.HIGH_ACCURACY,
                notification = NotificationSpec.fromMap(notification),
                stopOnTaskRemoved = map["stopOnTaskRemoved"] as? Boolean ?: true,
                restartOnBoot = map["restartOnBoot"] as? Boolean ?: false,
                bufferCapacity = capacity,
            )
        }
    }
}

/** One fix as plain data, independent of `android.location.Location`. */
internal data class FixRecord(
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Float?,
    val bearing: Float?,
    val provider: String?,
    val isMocked: Boolean,
) {
    /** The Dart wire form (see `LocationFix.fromMap`). Null fields are omitted. */
    fun toMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["lat"] = lat
        m["lon"] = lon
        m["time"] = timeMs
        accuracy?.let { m["accuracy"] = it.toDouble() }
        altitude?.let { m["altitude"] = it }
        speed?.let { m["speed"] = it.toDouble() }
        bearing?.let { m["bearing"] = it.toDouble() }
        provider?.let { m["provider"] = it }
        m["isMocked"] = isMocked
        return m
    }

    /** One line, tab separated, without a trailing newline. */
    fun encode(): String = listOf(
        FORMAT_VERSION,
        lat.toString(),
        lon.toString(),
        timeMs.toString(),
        accuracy?.toString() ?: "",
        altitude?.toString() ?: "",
        speed?.toString() ?: "",
        bearing?.toString() ?: "",
        provider?.let { escape(it) } ?: "",
        if (isMocked) "1" else "0",
    ).joinToString("\t")

    companion object {
        private const val FORMAT_VERSION = "1"
        private const val FIELDS = 10

        /** Parses a line written by [encode]; null when it is corrupt. */
        fun decode(line: String): FixRecord? {
            val p = line.split('\t')
            if (p.size != FIELDS || p[0] != FORMAT_VERSION) return null
            return try {
                FixRecord(
                    lat = p[1].toDouble(),
                    lon = p[2].toDouble(),
                    timeMs = p[3].toLong(),
                    accuracy = p[4].ifEmpty { null }?.toFloat(),
                    altitude = p[5].ifEmpty { null }?.toDouble(),
                    speed = p[6].ifEmpty { null }?.toFloat(),
                    bearing = p[7].ifEmpty { null }?.toFloat(),
                    provider = p[8].ifEmpty { null }?.let { unescape(it) },
                    isMocked = when (p[9]) {
                        "1" -> true
                        "0" -> false
                        else -> return null
                    },
                )
            } catch (e: NumberFormatException) {
                null
            }
        }

        private fun escape(s: String): String = buildString {
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }

        private fun unescape(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(s[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}

package dev.abdeldjalilchougui.background_location_free

/** Where a new fix goes. */
internal enum class Route { LISTENER, HEADLESS, BUFFER }

internal object FixRouter {
    /** A live Dart listener wins; then the headless callback; else the buffer. */
    fun route(hasListener: Boolean, headlessRegistered: Boolean): Route = when {
        hasListener -> Route.LISTENER
        headlessRegistered -> Route.HEADLESS
        else -> Route.BUFFER
    }
}

/** In-memory FIFO that drops the oldest item beyond [capacity]. */
internal class BoundedQueue<T>(private val capacity: Int) {
    private val items = ArrayDeque<T>()

    val size: Int
        get() = items.size

    fun add(item: T) {
        items.addLast(item)
        while (items.size > capacity) items.removeFirst()
    }

    fun drain(): List<T> {
        val out = items.toList()
        items.clear()
        return out
    }
}

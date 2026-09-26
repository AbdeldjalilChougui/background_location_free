package dev.abdeldjalilchougui.background_location_free

import kotlin.test.Test
import kotlin.test.assertEquals

internal class RoutingTest {
    @Test
    fun listenerWinsThenHeadlessThenBuffer() {
        assertEquals(Route.LISTENER, FixRouter.route(hasListener = true, headlessRegistered = true))
        assertEquals(Route.LISTENER, FixRouter.route(hasListener = true, headlessRegistered = false))
        assertEquals(Route.HEADLESS, FixRouter.route(hasListener = false, headlessRegistered = true))
        assertEquals(Route.BUFFER, FixRouter.route(hasListener = false, headlessRegistered = false))
    }

    @Test
    fun boundedQueueKeepsTheNewest() {
        val q = BoundedQueue<Int>(3)
        (1..5).forEach { q.add(it) }
        assertEquals(3, q.size)
        assertEquals(listOf(3, 4, 5), q.drain())
        assertEquals(0, q.size)
    }

    @Test
    fun fixMapMatchesTheDartWireFormat() {
        val m = FixRecord(
            lat = 1.0, lon = 2.0, timeMs = 3L, accuracy = 4f, altitude = 5.0,
            speed = null, bearing = 6f, provider = "gps", isMocked = true,
        ).toMap()
        assertEquals(
            mapOf(
                "lat" to 1.0, "lon" to 2.0, "time" to 3L, "accuracy" to 4.0,
                "altitude" to 5.0, "bearing" to 6.0, "provider" to "gps", "isMocked" to true,
            ),
            m,
        )
    }
}

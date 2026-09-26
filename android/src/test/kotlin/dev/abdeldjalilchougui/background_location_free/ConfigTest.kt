package dev.abdeldjalilchougui.background_location_free

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal class ConfigTest {
    private fun notification(): MutableMap<String, Any?> = mutableMapOf(
        "title" to "Trip",
        "text" to "Recording",
        "iconResource" to "ic_stat_trip",
        "channelId" to "trips",
        "channelName" to "Trips",
        "color" to 0xFF2196F3L,
        "ongoing" to false,
    )

    private fun config(): MutableMap<String, Any?> = mutableMapOf(
        "intervalMs" to 10000,
        "fastestIntervalMs" to 2000,
        "minDistanceMeters" to 15.0,
        "priority" to "balanced",
        "stopOnTaskRemoved" to false,
        "restartOnBoot" to true,
        "bufferCapacity" to 50,
        "notification" to notification(),
    )

    @Test
    fun parsesEveryField() {
        val c = TrackingConfig.fromMap(config())
        assertEquals(10000L, c.intervalMs)
        assertEquals(2000L, c.fastestIntervalMs)
        assertEquals(15f, c.minDistanceMeters)
        assertEquals(Priority.BALANCED, c.priority)
        assertEquals(false, c.stopOnTaskRemoved)
        assertEquals(true, c.restartOnBoot)
        assertEquals(50, c.bufferCapacity)
        assertEquals("Trip", c.notification.title)
        assertEquals("Recording", c.notification.text)
        assertEquals("ic_stat_trip", c.notification.iconResource)
        assertEquals("trips", c.notification.channelId)
        assertEquals("Trips", c.notification.channelName)
        assertEquals(0xFF2196F3.toInt(), c.notification.color)
        assertEquals(false, c.notification.ongoing)
    }

    @Test
    fun acceptsLongsDoublesAndIntsInterchangeably() {
        val m = config()
        m["intervalMs"] = 10000L
        m["fastestIntervalMs"] = 2000.0
        m["minDistanceMeters"] = 15
        val c = TrackingConfig.fromMap(m)
        assertEquals(10000L, c.intervalMs)
        assertEquals(2000L, c.fastestIntervalMs)
        assertEquals(15f, c.minDistanceMeters)
    }

    @Test
    fun appliesDefaultsForOptionalFields() {
        val n = mutableMapOf<String, Any?>("title" to "T", "text" to "x")
        val c = TrackingConfig.fromMap(mapOf("intervalMs" to 5000, "notification" to n))
        assertEquals(5000L, c.fastestIntervalMs)
        assertEquals(0f, c.minDistanceMeters)
        assertEquals(Priority.HIGH_ACCURACY, c.priority)
        assertEquals(true, c.stopOnTaskRemoved)
        assertEquals(false, c.restartOnBoot)
        assertEquals(1000, c.bufferCapacity)
        assertEquals("background_location", c.notification.channelId)
        assertEquals("Background location", c.notification.channelName)
        assertNull(c.notification.iconResource)
        assertNull(c.notification.color)
        assertEquals(true, c.notification.ongoing)
    }

    @Test
    fun rejectsInvalidValues() {
        fun bad(mutate: (MutableMap<String, Any?>) -> Unit) {
            val m = config()
            mutate(m)
            assertFailsWith<IllegalArgumentException> { TrackingConfig.fromMap(m) }
        }
        bad { it.remove("intervalMs") }
        bad { it["intervalMs"] = 0 }
        bad { it["intervalMs"] = "5000" }
        bad { it["fastestIntervalMs"] = 20000 }
        bad { it["fastestIntervalMs"] = -1 }
        bad { it["minDistanceMeters"] = -0.5 }
        bad { it["minDistanceMeters"] = Double.NaN }
        bad { it["priority"] = "turbo" }
        bad { it["bufferCapacity"] = 0 }
        bad { it["bufferCapacity"] = 100001 }
        bad { it.remove("notification") }
        bad { (it["notification"] as MutableMap<String, Any?>)["title"] = "" }
        bad { (it["notification"] as MutableMap<String, Any?>)["channelId"] = "" }
        bad { (it["notification"] as MutableMap<String, Any?>)["color"] = 0x1FFFFFFFFL }
    }

    @Test
    fun roundTripsThroughToMap() {
        val c = TrackingConfig.fromMap(config())
        assertEquals(c, TrackingConfig.fromMap(c.toMap()))
    }

    @Test
    fun priorityWireNamesMatchDart() {
        assertEquals(
            listOf("highAccuracy", "balanced", "lowPower", "passive"),
            Priority.values().map { it.wireName },
        )
    }
}

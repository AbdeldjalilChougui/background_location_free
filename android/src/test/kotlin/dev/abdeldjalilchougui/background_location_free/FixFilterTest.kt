package dev.abdeldjalilchougui.background_location_free

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FixFilterTest {
    private fun at(lat: Double, lon: Double, t: Long) = FixRecord(
        lat = lat, lon = lon, timeMs = t, accuracy = null, altitude = null,
        speed = null, bearing = null, provider = "gps", isMocked = false,
    )

    @Test
    fun haversineMatchesAKnownDistance() {
        // One degree of latitude on a sphere of radius 6 371 008.8 m.
        val d = FixFilter.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(d - 111195.08) < 0.5, "got $d")
        assertEquals(0.0, FixFilter.distanceMeters(36.7, 3.0, 36.7, 3.0))
    }

    @Test
    fun firstFixIsAlwaysAccepted() {
        assertTrue(FixFilter(fastestIntervalMs = 60000, minDistanceMeters = 1000f).accept(at(0.0, 0.0, 0)))
    }

    @Test
    fun dropsFixesThatComeTooSoon() {
        val f = FixFilter(fastestIntervalMs = 2000, minDistanceMeters = 0f)
        assertTrue(f.accept(at(0.0, 0.0, 0)))
        assertFalse(f.accept(at(0.1, 0.0, 1999)))
        assertTrue(f.accept(at(0.1, 0.0, 2000)))
    }

    @Test
    fun dropsFixesThatMovedTooLittle() {
        val f = FixFilter(fastestIntervalMs = 0, minDistanceMeters = 50f)
        assertTrue(f.accept(at(0.0, 0.0, 0)))
        // ~11 m north.
        assertFalse(f.accept(at(0.0001, 0.0, 1000)))
        // ~56 m from the last accepted fix.
        assertTrue(f.accept(at(0.0005, 0.0, 2000)))
    }

    @Test
    fun comparesAgainstTheLastAcceptedFixNotTheLastSeen() {
        val f = FixFilter(fastestIntervalMs = 0, minDistanceMeters = 50f)
        f.accept(at(0.0, 0.0, 0))
        // Three 33 m steps: the third is 100 m from the anchor.
        assertFalse(f.accept(at(0.0003, 0.0, 1)))
        assertTrue(f.accept(at(0.0006, 0.0, 2)))
    }

    @Test
    fun clockGoingBackwardsDoesNotBlockForever() {
        val f = FixFilter(fastestIntervalMs = 5000, minDistanceMeters = 0f)
        assertTrue(f.accept(at(0.0, 0.0, 100000)))
        // A provider with an older timestamp is dropped as stale...
        assertFalse(f.accept(at(0.0, 0.0, 50000)))
        // ...and newer ones resume normally.
        assertTrue(f.accept(at(0.0, 0.0, 105000)))
    }

    @Test
    fun resetForgetsTheAnchor() {
        val f = FixFilter(fastestIntervalMs = 60000, minDistanceMeters = 0f)
        f.accept(at(0.0, 0.0, 0))
        f.reset()
        assertTrue(f.accept(at(0.0, 0.0, 1)))
    }
}

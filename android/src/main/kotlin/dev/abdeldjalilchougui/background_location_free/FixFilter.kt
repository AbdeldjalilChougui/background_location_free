package dev.abdeldjalilchougui.background_location_free

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drops fixes that arrive sooner than [fastestIntervalMs] or closer than
 * [minDistanceMeters] to the last accepted fix. `LocationManager` treats its
 * own interval and distance as hints, so the service applies this filter to
 * every fix it gets.
 */
internal class FixFilter(
    private val fastestIntervalMs: Long,
    private val minDistanceMeters: Float,
) {
    private var last: FixRecord? = null

    fun accept(fix: FixRecord): Boolean {
        val prev = last
        if (prev != null) {
            val dt = fix.timeMs - prev.timeMs
            if (dt < fastestIntervalMs) return false
            if (minDistanceMeters > 0 &&
                distanceMeters(prev.lat, prev.lon, fix.lat, fix.lon) < minDistanceMeters
            ) return false
        }
        last = fix
        return true
    }

    fun reset() {
        last = null
    }

    companion object {
        /** Mean Earth radius (IUGG R1), metres. */
        const val EARTH_RADIUS_METERS = 6371008.8

        /** Great-circle distance by the haversine formula. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val s1 = sin(dLat / 2)
            val s2 = sin(dLon / 2)
            val a = s1 * s1 + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * s2 * s2
            return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
        }
    }
}

package dev.abdeldjalilchougui.background_location_free

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ApiLevelsTest {
    @Test
    fun foregroundServiceTypeIsLocationFromApi29() {
        // ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION == 8.
        assertEquals(8, ApiLevels.FOREGROUND_SERVICE_TYPE_LOCATION)
        assertEquals(0, ApiLevels.foregroundServiceType(28))
        assertEquals(8, ApiLevels.foregroundServiceType(29))
        assertEquals(8, ApiLevels.foregroundServiceType(34))
        assertEquals(8, ApiLevels.foregroundServiceType(36))
    }

    @Test
    fun mockDetectionUsesIsMockFromApi31() {
        var modern = 0
        var legacy = 0
        val isMock = { modern++; true }
        val fromMock = { legacy++; false }
        assertTrue(ApiLevels.isMock(31, isMock, fromMock))
        assertTrue(ApiLevels.isMock(35, isMock, fromMock))
        assertFalse(ApiLevels.isMock(30, isMock, fromMock))
        assertFalse(ApiLevels.isMock(21, isMock, fromMock))
        assertEquals(2, modern)
        assertEquals(2, legacy)
    }

    @Test
    fun permissionApplicability() {
        assertEquals("notApplicable", ApiLevels.backgroundLocationState(28, granted = false))
        assertEquals("granted", ApiLevels.backgroundLocationState(29, granted = true))
        assertEquals("denied", ApiLevels.backgroundLocationState(34, granted = false))
        assertEquals("notApplicable", ApiLevels.batteryOptimizationState(22, exempt = false))
        assertEquals("denied", ApiLevels.batteryOptimizationState(23, exempt = false))
        assertEquals("granted", ApiLevels.batteryOptimizationState(33, exempt = true))
        assertFalse(ApiLevels.needsNotificationPermission(32))
        assertTrue(ApiLevels.needsNotificationPermission(33))
        assertFalse(ApiLevels.hasBackgroundStartLimits(30))
        assertTrue(ApiLevels.hasBackgroundStartLimits(31))
    }

    @Test
    fun bootRestartDecision() {
        fun d(sdk: Int, opted: Boolean = true, wasRunning: Boolean = true, fg: Boolean = true, bg: Boolean = true) =
            ApiLevels.bootRestart(sdk, opted, wasRunning, fg, bg)
        assertEquals(BootRestart.START, d(28, bg = false))
        assertEquals(BootRestart.START, d(34))
        assertEquals(BootRestart.SKIP_NOT_OPTED_IN, d(34, opted = false))
        assertEquals(BootRestart.SKIP_NOT_RUNNING, d(34, wasRunning = false))
        assertEquals(BootRestart.SKIP_NO_LOCATION_PERMISSION, d(34, fg = false))
        assertEquals(BootRestart.SKIP_NO_BACKGROUND_PERMISSION, d(29, bg = false))
        assertEquals(BootRestart.SKIP_NO_BACKGROUND_PERMISSION, d(35, bg = false))
    }

    @Test
    fun providerSelection() {
        val all = setOf("gps", "network", "passive")
        assertEquals("gps", ApiLevels.provider(Priority.HIGH_ACCURACY, all))
        assertEquals("network", ApiLevels.provider(Priority.HIGH_ACCURACY, setOf("network", "passive")))
        assertEquals("network", ApiLevels.provider(Priority.BALANCED, all))
        assertEquals("gps", ApiLevels.provider(Priority.BALANCED, setOf("gps", "passive")))
        assertEquals("network", ApiLevels.provider(Priority.LOW_POWER, all))
        assertEquals("passive", ApiLevels.provider(Priority.LOW_POWER, setOf("gps", "passive")))
        assertEquals("passive", ApiLevels.provider(Priority.PASSIVE, all))
        assertEquals(null, ApiLevels.provider(Priority.HIGH_ACCURACY, setOf("passive")))
        assertEquals(null, ApiLevels.provider(Priority.PASSIVE, emptySet()))
    }
}

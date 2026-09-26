package dev.abdeldjalilchougui.background_location_free

/** Outcome of the reboot check in [BootReceiver]. */
internal enum class BootRestart {
    START,
    SKIP_NOT_OPTED_IN,
    SKIP_NOT_RUNNING,
    SKIP_NO_LOCATION_PERMISSION,
    SKIP_NO_BACKGROUND_PERMISSION,
}

/** API-level branching, kept free of Android calls so it can be unit tested. */
internal object ApiLevels {
    /** `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`. */
    const val FOREGROUND_SERVICE_TYPE_LOCATION = 8

    /**
     * The type passed to `startForeground`. The typed overload exists from
     * API 29; from API 34 the type is mandatory and must match the manifest.
     */
    fun foregroundServiceType(sdk: Int): Int =
        if (sdk >= 29) FOREGROUND_SERVICE_TYPE_LOCATION else 0

    /**
     * `Location.isMock()` from API 31; the deprecated
     * `isFromMockProvider()` before.
     */
    fun isMock(sdk: Int, isMock: () -> Boolean, isFromMockProvider: () -> Boolean): Boolean =
        if (sdk >= 31) isMock() else isFromMockProvider()

    /** `ACCESS_BACKGROUND_LOCATION` exists from API 29. */
    fun backgroundLocationState(sdk: Int, granted: Boolean): String = when {
        sdk < 29 -> "notApplicable"
        granted -> "granted"
        else -> "denied"
    }

    /** Doze and battery optimisation exist from API 23. */
    fun batteryOptimizationState(sdk: Int, exempt: Boolean): String = when {
        sdk < 23 -> "notApplicable"
        exempt -> "granted"
        else -> "denied"
    }

    /** `POST_NOTIFICATIONS` is a runtime permission from API 33. */
    fun needsNotificationPermission(sdk: Int): Boolean = sdk >= 33

    /** From API 31 a foreground service cannot normally be started from the background. */
    fun hasBackgroundStartLimits(sdk: Int): Boolean = sdk >= 31

    /**
     * Whether to restart after `BOOT_COMPLETED`. `BOOT_COMPLETED` is exempt
     * from the API 31 background-start limit, and Android 15 does not block
     * the `location` type from boot receivers, but the app is not visible at
     * boot, so on API 29+ location only flows with the background grant (on
     * API 34+ `startForeground` throws without it).
     */
    fun bootRestart(
        sdk: Int,
        optedIn: Boolean,
        wasRunning: Boolean,
        hasLocationPermission: Boolean,
        hasBackgroundPermission: Boolean,
    ): BootRestart = when {
        !optedIn -> BootRestart.SKIP_NOT_OPTED_IN
        !wasRunning -> BootRestart.SKIP_NOT_RUNNING
        !hasLocationPermission -> BootRestart.SKIP_NO_LOCATION_PERMISSION
        sdk >= 29 && !hasBackgroundPermission -> BootRestart.SKIP_NO_BACKGROUND_PERMISSION
        else -> BootRestart.START
    }

    /**
     * Picks a `LocationManager` provider from the ones the device has
     * ([available], from `getAllProviders()`), or null when none fits.
     */
    fun provider(priority: Priority, available: Set<String>): String? {
        val order = when (priority) {
            Priority.HIGH_ACCURACY -> listOf(GPS, NETWORK)
            Priority.BALANCED -> listOf(NETWORK, GPS)
            Priority.LOW_POWER -> listOf(NETWORK, PASSIVE)
            Priority.PASSIVE -> listOf(PASSIVE)
        }
        return order.firstOrNull { it in available }
    }

    const val GPS = "gps"
    const val NETWORK = "network"
    const val PASSIVE = "passive"
}

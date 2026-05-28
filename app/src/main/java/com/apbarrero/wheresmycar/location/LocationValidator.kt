package com.apbarrero.wheresmycar.location

import android.location.Location

internal object LocationValidator {

    // Reject cached locations older than this. The FusedLocation cache can
    // return a fix hours old (e.g. from yesterday's drive), which would
    // silently overwrite a valid parking location on a phantom service restart.
    internal const val MAX_AGE_MS = 60_000L

    // Reject locations with poor accuracy. Cell-tower/Wi-Fi-only fixes can be
    // hundreds of metres off and would corrupt the saved parking position.
    internal const val MAX_INACCURACY_M = 100f

    /**
     * Returns true if [location] is recent enough and accurate enough to use
     * for saving a parking position. [currentTimeMs] is injectable so tests
     * can control the clock without mocking system time.
     */
    fun isUsable(location: Location?, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (location == null) return false
        if (currentTimeMs - location.time > MAX_AGE_MS) return false
        if (location.hasAccuracy() && location.accuracy > MAX_INACCURACY_M) return false
        return true
    }
}

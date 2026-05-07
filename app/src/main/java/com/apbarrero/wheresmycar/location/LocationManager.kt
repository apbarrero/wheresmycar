package com.apbarrero.wheresmycar.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages location-related operations using the Fused Location Provider.
 */
class LocationManager(private val context: Context) {

    companion object {
        // Reject cached locations older than this. The FusedLocation cache
        // can return a fix that's hours old (e.g. from yesterday's drive).
        // Saving such a fix at e.g. 3 AM during a phantom service restart
        // is the failure mode that overwrote valid parking locations.
        private const val MAX_LOCATION_AGE_MS = 60_000L  // 60 seconds

        // Reject locations with poor accuracy. Cell-tower / Wi-Fi-only
        // fixes can be hundreds of metres off and would silently overwrite
        // a valid parking location with junk.
        private const val MAX_LOCATION_INACCURACY_M = 100f
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Checks if location permissions are granted.
     *
     * @return true if either fine or coarse location permission is granted, false otherwise.
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Retrieves the current location using the Fused Location Provider.
     *
     * @return The current location if available, null otherwise.
     */
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermissions()) {
            throw SecurityException("Location permissions not granted")
        }
        
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
            
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * Retrieves the last known location using the Fused Location Provider.
     *
     * @return The last known location if available, null otherwise.
     */
    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermissions()) {
            throw SecurityException("Location permissions not granted")
        }
        
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * Returns true if [location] is fresh enough and accurate enough to
     * use for saving a parking position. The previous implementation
     * accepted whatever the FusedLocation cache returned, which is how
     * stale or inaccurate fixes ended up overwriting valid saves.
     */
    private fun isLocationUsable(location: Location?): Boolean {
        if (location == null) return false
        val age = System.currentTimeMillis() - location.time
        if (age > MAX_LOCATION_AGE_MS) return false
        if (location.hasAccuracy() && location.accuracy > MAX_LOCATION_INACCURACY_M) return false
        return true
    }

    /**
     * Returns a usable location, preferring a fresh fix and falling back
     * to the FusedLocation cache only if the cached value is itself
     * recent and accurate. Returns null if no acceptable location is
     * available — in that case the caller should skip saving rather
     * than persist a bad position.
     */
    suspend fun getLocationWithFallback(): Location? {
        val current = try { getCurrentLocation() } catch (e: Exception) { null }
        if (isLocationUsable(current)) return current

        val last = try { getLastKnownLocation() } catch (e: Exception) { null }
        if (isLocationUsable(last)) return last

        return null
    }
}

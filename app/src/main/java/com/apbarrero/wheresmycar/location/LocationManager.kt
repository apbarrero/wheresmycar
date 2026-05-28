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

class LocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
) {

    companion object {
        fun create(context: Context): LocationManager = LocationManager(
            context = context,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context),
        )
    }

    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermissions()) throw SecurityException("Location permissions not granted")

        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
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

    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermissions()) throw SecurityException("Location permissions not granted")

        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Returns a usable location, preferring a fresh GPS fix and falling back
     * to the FusedLocation cache only when that cached value is itself
     * recent and accurate. Returns null when no acceptable fix is available —
     * the caller should skip saving rather than persist a bad position.
     */
    suspend fun getLocationWithFallback(): Location? {
        val current = try { getCurrentLocation() } catch (e: Exception) { null }
        if (LocationValidator.isUsable(current)) return current

        val last = try { getLastKnownLocation() } catch (e: Exception) { null }
        if (LocationValidator.isUsable(last)) return last

        return null
    }
}

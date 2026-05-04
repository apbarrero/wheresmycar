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
     * Retrieves the current location with a fallback to the last known location.
     *
     * @return The current or last known location if available, null otherwise.
     */
    suspend fun getLocationWithFallback(): Location? {
        return try {
            getCurrentLocation() ?: getLastKnownLocation()
        } catch (e: Exception) {
            try {
                getLastKnownLocation()
            } catch (e: Exception) {
                null
            }
        }
    }
}

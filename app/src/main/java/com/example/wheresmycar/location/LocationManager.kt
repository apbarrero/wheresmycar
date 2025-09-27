package com.example.wheresmycar.location

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

class LocationManager(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Check if location permissions are granted
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
     * Get current location
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
     * Get last known location
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
     * Get location with fallback to last known location
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

package com.apbarrero.wheresmycar.data

import java.util.Date

/**
 * Represents a Bluetooth device that can be tracked
 */
data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val isSelected: Boolean = false
)

/**
 * Represents a parking location with timestamp
 */
data class ParkingLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Date,
    val deviceName: String,
    val address: String? = null // Human-readable address if available
)

/**
 * App settings and preferences
 */
data class AppSettings(
    val selectedDeviceAddress: String? = null,
    val selectedDeviceName: String? = null,
    val isTrackingEnabled: Boolean = false,
    val lastKnownLocation: ParkingLocation? = null
)
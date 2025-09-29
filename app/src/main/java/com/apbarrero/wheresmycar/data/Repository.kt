package com.apbarrero.wheresmycar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

// Create DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class Repository(private val context: Context) {
    
    private object PreferencesKeys {
        val SELECTED_DEVICE_ADDRESS = stringPreferencesKey("selected_device_address")
        val SELECTED_DEVICE_NAME = stringPreferencesKey("selected_device_name")
        val IS_TRACKING_ENABLED = booleanPreferencesKey("is_tracking_enabled")
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val LAST_TIMESTAMP = longPreferencesKey("last_timestamp")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val LAST_ADDRESS = stringPreferencesKey("last_address")
    }
    
    /**
     * Get app settings as a flow
     */
    val appSettings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val lastLocation = if (preferences[PreferencesKeys.LAST_LATITUDE] != null &&
            preferences[PreferencesKeys.LAST_LONGITUDE] != null &&
            preferences[PreferencesKeys.LAST_TIMESTAMP] != null &&
            preferences[PreferencesKeys.LAST_DEVICE_NAME] != null
        ) {
            ParkingLocation(
                latitude = preferences[PreferencesKeys.LAST_LATITUDE]!!,
                longitude = preferences[PreferencesKeys.LAST_LONGITUDE]!!,
                timestamp = Date(preferences[PreferencesKeys.LAST_TIMESTAMP]!!),
                deviceName = preferences[PreferencesKeys.LAST_DEVICE_NAME]!!,
                address = preferences[PreferencesKeys.LAST_ADDRESS]
            )
        } else null
        
        AppSettings(
            selectedDeviceAddress = preferences[PreferencesKeys.SELECTED_DEVICE_ADDRESS],
            selectedDeviceName = preferences[PreferencesKeys.SELECTED_DEVICE_NAME],
            isTrackingEnabled = preferences[PreferencesKeys.IS_TRACKING_ENABLED] ?: false,
            lastKnownLocation = lastLocation
        )
    }
    
    /**
     * Save selected Bluetooth device
     */
    suspend fun saveSelectedDevice(deviceInfo: BluetoothDeviceInfo) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_DEVICE_ADDRESS] = deviceInfo.address
            preferences[PreferencesKeys.SELECTED_DEVICE_NAME] = deviceInfo.name
        }
    }
    
    /**
     * Enable or disable tracking
     */
    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_TRACKING_ENABLED] = enabled
        }
    }
    
    /**
     * Save parking location
     */
    suspend fun saveParkingLocation(location: ParkingLocation) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_LATITUDE] = location.latitude
            preferences[PreferencesKeys.LAST_LONGITUDE] = location.longitude
            preferences[PreferencesKeys.LAST_TIMESTAMP] = location.timestamp.time
            preferences[PreferencesKeys.LAST_DEVICE_NAME] = location.deviceName
            location.address?.let { 
                preferences[PreferencesKeys.LAST_ADDRESS] = it 
            }
        }
    }
    
    /**
     * Clear all data
     */
    suspend fun clearAllData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
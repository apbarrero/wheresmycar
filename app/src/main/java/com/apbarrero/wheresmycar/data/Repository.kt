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
import java.time.Instant

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class Repository(private val dataStore: DataStore<Preferences>) {

    companion object {
        fun create(context: Context): Repository = Repository(context.dataStore)
    }

    private object Keys {
        val SELECTED_DEVICE_ADDRESS = stringPreferencesKey("selected_device_address")
        val SELECTED_DEVICE_NAME    = stringPreferencesKey("selected_device_name")
        val IS_TRACKING_ENABLED     = booleanPreferencesKey("is_tracking_enabled")
        val LAST_LATITUDE           = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE          = doublePreferencesKey("last_longitude")
        val LAST_TIMESTAMP          = longPreferencesKey("last_timestamp")
        val LAST_DEVICE_NAME        = stringPreferencesKey("last_device_name")
        val LAST_ADDRESS            = stringPreferencesKey("last_address")
    }

    val appSettings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val lastLocation = if (
            prefs[Keys.LAST_LATITUDE] != null &&
            prefs[Keys.LAST_LONGITUDE] != null &&
            prefs[Keys.LAST_TIMESTAMP] != null &&
            prefs[Keys.LAST_DEVICE_NAME] != null
        ) {
            ParkingLocation(
                latitude   = prefs[Keys.LAST_LATITUDE]!!,
                longitude  = prefs[Keys.LAST_LONGITUDE]!!,
                timestamp  = Instant.ofEpochMilli(prefs[Keys.LAST_TIMESTAMP]!!),
                deviceName = prefs[Keys.LAST_DEVICE_NAME]!!,
                address    = prefs[Keys.LAST_ADDRESS]
            )
        } else null

        AppSettings(
            selectedDeviceAddress = prefs[Keys.SELECTED_DEVICE_ADDRESS],
            selectedDeviceName    = prefs[Keys.SELECTED_DEVICE_NAME],
            isTrackingEnabled     = prefs[Keys.IS_TRACKING_ENABLED] ?: false,
            lastKnownLocation     = lastLocation
        )
    }

    suspend fun saveSelectedDevice(deviceInfo: BluetoothDeviceInfo) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_DEVICE_ADDRESS] = deviceInfo.address
            prefs[Keys.SELECTED_DEVICE_NAME]    = deviceInfo.name
        }
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun saveParkingLocation(location: ParkingLocation) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_LATITUDE]   = location.latitude
            prefs[Keys.LAST_LONGITUDE]  = location.longitude
            prefs[Keys.LAST_TIMESTAMP]  = location.timestamp.toEpochMilli()
            prefs[Keys.LAST_DEVICE_NAME] = location.deviceName
            // Explicitly remove stale address key when new location has none,
            // otherwise the old value bleeds through on the next read.
            if (location.address != null) {
                prefs[Keys.LAST_ADDRESS] = location.address
            } else {
                prefs.remove(Keys.LAST_ADDRESS)
            }
        }
    }

    suspend fun clearAllData() {
        dataStore.edit { it.clear() }
    }
}

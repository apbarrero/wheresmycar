package com.apbarrero.wheresmycar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.apbarrero.wheresmycar.data.Repository

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Check if we were previously tracking a device
                val repository = Repository(context)
                val preferences = context.getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
                val wasTracking = preferences.getBoolean("was_tracking", false)
                val deviceAddress = preferences.getString("tracked_device_address", null)
                val deviceName = preferences.getString("tracked_device_name", null)
                
                if (wasTracking && deviceAddress != null && deviceName != null) {
                    // Restart the tracking service
                    val serviceIntent = Intent(context, ParkingTrackingService::class.java).apply {
                        action = ParkingTrackingService.ACTION_START_TRACKING
                        putExtra(ParkingTrackingService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                        putExtra(ParkingTrackingService.EXTRA_DEVICE_NAME, deviceName)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
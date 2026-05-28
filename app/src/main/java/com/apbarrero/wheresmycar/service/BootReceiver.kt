package com.apbarrero.wheresmycar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.apbarrero.wheresmycar.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = Repository(context).appSettings.first()
                        val address = settings.selectedDeviceAddress
                        val name = settings.selectedDeviceName
                        if (settings.isTrackingEnabled && address != null && name != null) {
                            val serviceIntent = Intent(context, ParkingTrackingService::class.java).apply {
                                action = ParkingTrackingService.ACTION_START_TRACKING
                                putExtra(ParkingTrackingService.EXTRA_DEVICE_ADDRESS, address)
                                putExtra(ParkingTrackingService.EXTRA_DEVICE_NAME, name)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

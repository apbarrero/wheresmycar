package com.apbarrero.wheresmycar.service

import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.apbarrero.wheresmycar.R
import com.apbarrero.wheresmycar.data.ParkingLocation
import com.apbarrero.wheresmycar.data.Repository
import com.apbarrero.wheresmycar.location.LocationManager
import kotlinx.coroutines.*
import java.util.*

class ParkingTrackingService : Service() {
    
    companion object {
        const val ACTION_START_TRACKING = "com.apbarrero.wheresmycar.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.apbarrero.wheresmycar.STOP_TRACKING"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "parking_tracking_channel"
    }
    
    private lateinit var repository: Repository
    private lateinit var locationManager: LocationManager
    private var trackedDeviceAddress: String? = null
    private var trackedDeviceName: String? = null
    private var serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // Broadcast receiver for Bluetooth connection events
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == trackedDeviceAddress) {
                        // Device disconnected - save current location
                        saveCurrentLocation()
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        locationManager = LocationManager(this)
        
        createNotificationChannel()
        
        // Register for Bluetooth connection events
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(bluetoothReceiver, filter)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                trackedDeviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                trackedDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                startForegroundService()
            }
            ACTION_STOP_TRACKING -> {
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver might already be unregistered
        }
        serviceJob.cancel()
    }
    
    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun saveCurrentLocation() {
        serviceScope.launch {
            try {
                val location = locationManager.getLocationWithFallback()
                if (location != null && trackedDeviceName != null) {
                    val parkingLocation = ParkingLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = Date(),
                        deviceName = trackedDeviceName!!
                    )
                    
                    repository.saveParkingLocation(parkingLocation)
                    
                    // Update notification to show location saved
                    val updatedNotification = createNotification("Location saved!")
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }
            } catch (e: Exception) {
                // Log error or show notification about failure
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Parking Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking Bluetooth device for parking location"
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String = "Tracking parking location"): Notification {
        // Create intent to open main activity when notification is tapped
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Where's My Car")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}

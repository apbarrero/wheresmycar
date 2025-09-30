package com.apbarrero.wheresmycar.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.apbarrero.wheresmycar.R
import com.apbarrero.wheresmycar.data.ParkingLocation
import com.apbarrero.wheresmycar.data.Repository
import com.apbarrero.wheresmycar.location.LocationManager
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ParkingTrackingService : Service() {
    
    companion object {
        const val ACTION_START_TRACKING = "com.apbarrero.wheresmycar.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.apbarrero.wheresmycar.STOP_TRACKING"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "parking_tracking_channel"
        private const val CONNECTIVITY_CHECK_INTERVAL = 30000L // 30 seconds
        
        private val runningServices = ConcurrentHashMap<String, Boolean>()
        
        fun isServiceRunning(): Boolean {
            return runningServices.isNotEmpty()
        }
    }
    
    private lateinit var repository: Repository
    private lateinit var locationManager: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var trackedDeviceAddress: String? = null
    private var trackedDeviceName: String? = null
    private var serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var connectivityCheckJob: Job? = null
    private var isDeviceConnected = true
    private var lastDisconnectionTime: Long = 0
    
    // Broadcast receiver for Bluetooth connection events
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == trackedDeviceAddress) {
                        isDeviceConnected = false
                        lastDisconnectionTime = System.currentTimeMillis()
                        // Device disconnected - save current location
                        saveCurrentLocation()
                        
                        // Update notification
                        val updatedNotification = createNotification("Device disconnected - Location saved!")
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == trackedDeviceAddress) {
                        isDeviceConnected = true
                        // Update notification to show connected status
                        val updatedNotification = createNotification("Device connected - Tracking active")
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        // Bluetooth turned off - this might affect tracking
                        val updatedNotification = createNotification("Bluetooth off - Please enable Bluetooth")
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        locationManager = LocationManager(this)
        
        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        // Acquire wake lock to keep CPU awake
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WheresMyCarApp::ParkingTrackingWakeLock"
        )
        wakeLock.acquire(24 * 60 * 60 * 1000L) // 24 hours max
        
        createNotificationChannel()
        
        // Register for Bluetooth connection events
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
        
        // Request battery optimization exemption
        requestBatteryOptimizationExemption()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                trackedDeviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                trackedDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                
                if (trackedDeviceAddress != null && trackedDeviceName != null) {
                    // Save tracking state to preferences
                    saveTrackingState(true, trackedDeviceAddress!!, trackedDeviceName!!)
                    
                    // Add this service to running services map
                    runningServices[trackedDeviceAddress!!] = true
                    
                    startForegroundService()
                    startConnectivityMonitoring()
                    
                    // Schedule job to restart service if killed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ServiceRestartJob.scheduleJob(this)
                    }
                }
            }
            ACTION_STOP_TRACKING -> {
                saveTrackingState(false, "", "")
                runningServices.remove(trackedDeviceAddress)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ServiceRestartJob.cancelJob(this)
                }
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
        
        // Clean up connectivity monitoring
        connectivityCheckJob?.cancel()
        
        // Release wake lock
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        
        // Unregister receiver
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver might already be unregistered
        }
        
        // Remove from running services
        runningServices.remove(trackedDeviceAddress)
        
        serviceJob.cancel()
        
        // If we're being destroyed and still tracking, try to restart
        val preferences = getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
        val wasTracking = preferences.getBoolean("was_tracking", false)
        if (wasTracking && trackedDeviceAddress != null && trackedDeviceName != null) {
            // Schedule restart using AlarmManager as fallback
            scheduleServiceRestart()
        }
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
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tracking Bluetooth device for parking location"
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
            .setContentTitle("Where's My Car - Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    private fun saveTrackingState(isTracking: Boolean, deviceAddress: String, deviceName: String) {
        val preferences = getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
        preferences.edit()
            .putBoolean("was_tracking", isTracking)
            .putString("tracked_device_address", deviceAddress)
            .putString("tracked_device_name", deviceName)
            .apply()
    }
    
    private fun startConnectivityMonitoring() {
        connectivityCheckJob?.cancel()
        connectivityCheckJob = serviceScope.launch {
            while (isActive) {
                delay(CONNECTIVITY_CHECK_INTERVAL)
                checkBluetoothConnectivity()
            }
        }
    }
    
    private fun checkBluetoothConnectivity() {
        if (trackedDeviceAddress == null) return
        
        try {
            val bondedDevices = bluetoothAdapter.bondedDevices
            val trackedDevice = bondedDevices.find { it.address == trackedDeviceAddress }
            
            if (trackedDevice != null) {
                // Use reflection to check if device is connected (Android doesn't provide direct API)
                try {
                    val method = trackedDevice.javaClass.getMethod("isConnected")
                    val isConnected = method.invoke(trackedDevice) as Boolean
                    
                    if (isDeviceConnected && !isConnected) {
                        // Device just disconnected
                        isDeviceConnected = false
                        lastDisconnectionTime = System.currentTimeMillis()
                        saveCurrentLocation()
                    } else if (!isDeviceConnected && isConnected) {
                        // Device reconnected
                        isDeviceConnected = true
                    }
                } catch (e: Exception) {
                    // Fallback: if reflection fails, assume device is still connected
                    // This is not ideal but better than crashing
                }
            }
        } catch (e: SecurityException) {
            // Bluetooth permission might be revoked
        } catch (e: Exception) {
            // Handle other exceptions
        }
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Failed to open battery optimization settings
                }
            }
        }
    }
    
    private fun scheduleServiceRestart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ParkingTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_DEVICE_ADDRESS, trackedDeviceAddress)
                putExtra(EXTRA_DEVICE_NAME, trackedDeviceName)
            }
            
            val pendingIntent = PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val triggerTime = System.currentTimeMillis() + 5000 // Restart in 5 seconds
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // SCHEDULE_EXACT_ALARM permission not granted
            }
        }
    }
}

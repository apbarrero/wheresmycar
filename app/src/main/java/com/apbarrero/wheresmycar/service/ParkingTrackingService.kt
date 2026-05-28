package com.apbarrero.wheresmycar.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import com.apbarrero.wheresmycar.bluetooth.BluetoothConnectionMonitor
import com.apbarrero.wheresmycar.bluetooth.SystemBluetoothConnectionChecker
import androidx.core.content.IntentCompat
import timber.log.Timber
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.apbarrero.wheresmycar.R
import com.apbarrero.wheresmycar.data.ParkingLocation
import com.apbarrero.wheresmycar.data.Repository
import com.apbarrero.wheresmycar.location.LocationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Service responsible for tracking the parking location of a Bluetooth device.
 */
class ParkingTrackingService : Service() {
    
    companion object {
        const val ACTION_START_TRACKING = "com.apbarrero.wheresmycar.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.apbarrero.wheresmycar.STOP_TRACKING"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "parking_tracking_channel"
        private const val CONNECTIVITY_CHECK_INTERVAL = 30000L // 30 seconds
        private const val MIN_DISTANCE_THRESHOLD = 50.0 // meters
        
        private val runningServices = ConcurrentHashMap<String, Boolean>()
        
        /**
         * Checks if the parking tracking service is currently running.
         *
         * @return true if any instance of the service is running, false otherwise.
         */
        fun isServiceRunning(): Boolean {
            return runningServices.isNotEmpty()
        }
    }
    
    private lateinit var repository: Repository
    private lateinit var locationManager: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var connectionChecker: SystemBluetoothConnectionChecker
    private var trackedDeviceAddress: String? = null
    private var trackedDeviceName: String? = null
    private var serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var connectivityCheckJob: Job? = null
    private var lastDisconnectionTime: Long = 0

    private val connectionMonitor = BluetoothConnectionMonitor(onDisconnected = {
        lastDisconnectionTime = System.currentTimeMillis()
        saveCurrentLocation()
    })

    // Broadcast receiver for Bluetooth connection events
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    if (device?.address == trackedDeviceAddress) {
                        connectionMonitor.notifyDisconnected()

                        val updatedNotification = createNotification("Device disconnected - Location saved!")
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    if (device?.address == trackedDeviceAddress) {
                        connectionMonitor.notifyConnected()

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
        
        // Initialize Bluetooth adapter and connection checker
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        connectionChecker = SystemBluetoothConnectionChecker(bluetoothManager)
        
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
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val newAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val newName = intent.getStringExtra(EXTRA_DEVICE_NAME)

                // If the tracked device changed, reset connectivity state so
                // we don't compare the new device's state against the old
                // device's last-known status.
                if (newAddress != null && newAddress != trackedDeviceAddress) {
                    connectionMonitor.reset()
                }

                trackedDeviceAddress = newAddress
                trackedDeviceName = newName

                if (trackedDeviceAddress != null && trackedDeviceName != null) {
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
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Bluetooth receiver was already unregistered")
        }
        
        // Remove from running services
        runningServices.remove(trackedDeviceAddress)
        
        serviceJob.cancel()

        // If we're being destroyed mid-tracking, schedule an AlarmManager restart as fallback.
        // trackedDeviceAddress being non-null means we were actively tracking.
        if (trackedDeviceAddress != null && trackedDeviceName != null) {
            scheduleServiceRestart()
        }
    }
    
    /**
     * Starts the service in the foreground with a notification.
     */
    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * Saves the current location if it differs from the previously stored
     * parking location by more than [MIN_DISTANCE_THRESHOLD]. Compares
     * against the persisted location (not an in-memory cache) so the
     * distance guard survives service restarts — otherwise every restart
     * silently bypassed it.
     */
    private fun saveCurrentLocation() {
        serviceScope.launch {
            try {
                val location = locationManager.getLocationWithFallback() ?: return@launch
                val deviceName = trackedDeviceName ?: return@launch

                val previousLocation = repository.appSettings.first().lastKnownLocation
                if (previousLocation != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        previousLocation.latitude, previousLocation.longitude,
                        location.latitude, location.longitude,
                        results
                    )
                    if (results[0] <= MIN_DISTANCE_THRESHOLD) return@launch
                }

                val parkingLocation = ParkingLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = Date(),
                    deviceName = deviceName
                )
                repository.saveParkingLocation(parkingLocation)

                val updatedNotification = createNotification("Location saved!")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save parking location")
            }
        }
    }


    /**
     * Creates a notification channel for the service.
     */
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
    
    /**
     * Creates a notification for the service.
     *
     * @param contentText The text to display in the notification.
     * @return A notification object.
     */
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
    
    /**
     * Starts monitoring the Bluetooth connectivity of the tracked device.
     */
    private fun startConnectivityMonitoring() {
        connectivityCheckJob?.cancel()
        connectivityCheckJob = serviceScope.launch {
            while (isActive) {
                delay(CONNECTIVITY_CHECK_INTERVAL)
                checkBluetoothConnectivity()
            }
        }
    }
    
    /**
     * Polls the current Bluetooth connection state using public Android APIs and
     * forwards it to [connectionMonitor]. The monitor handles baseline establishment
     * and transition detection — see [BluetoothConnectionMonitor] for semantics.
     */
    private fun checkBluetoothConnectivity() {
        if (trackedDeviceAddress == null) return
        try {
            val isConnected = connectionChecker.isDeviceConnected(trackedDeviceAddress!!)
            connectionMonitor.checkState(isConnected)
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT permission was revoked at runtime — skip this poll.
        }
    }
    
    /**
     * Schedules the service to restart using AlarmManager.
     */
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
                Timber.w(e, "SCHEDULE_EXACT_ALARM permission not granted; service restart alarm skipped")
            }
        }
    }
}

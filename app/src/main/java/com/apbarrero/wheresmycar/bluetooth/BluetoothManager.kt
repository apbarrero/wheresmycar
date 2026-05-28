package com.apbarrero.wheresmycar.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import timber.log.Timber
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.apbarrero.wheresmycar.data.BluetoothDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Bluetooth operations such as device discovery and connection state monitoring.
 */
class BluetoothManager(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as SystemBluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    /**
     * A flow that emits a list of discovered Bluetooth devices.
     */
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    /**
     * A flow that indicates whether the device is currently scanning for other devices.
     */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    /**
     * A flow that emits the current connection state of the tracked Bluetooth device.
     */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unknown)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    /**
     * The address of the currently tracked Bluetooth device.
     */
    private var trackedDeviceAddress: String? = null
    
    /**
     * Enum representing the connection state of a Bluetooth device.
     */
    enum class ConnectionState {
        Connected,
        Disconnected,
        Unknown
    }
    
    // Broadcast receiver for device discovery
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { bluetoothDevice ->
                        if (hasBluetoothPermissions()) {
                            val deviceInfo = BluetoothDeviceInfo(
                                address = bluetoothDevice.address,
                                name = bluetoothDevice.name ?: "Unknown Device"
                            )
                            
                            // Add device if not already in list
                            val currentDevices = _discoveredDevices.value.toMutableList()
                            if (!currentDevices.any { it.address == deviceInfo.address }) {
                                currentDevices.add(deviceInfo)
                                _discoveredDevices.value = currentDevices
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }
    
    // Broadcast receiver for connection state changes
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == trackedDeviceAddress) {
                        _connectionState.value = ConnectionState.Connected
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == trackedDeviceAddress) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }
    }
    
    init {
        // Register receivers
        val discoveryFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, discoveryFilter)
        
        val connectionFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(connectionReceiver, connectionFilter)
    }
    
    /**
     * Checks if Bluetooth is available and enabled.
     *
     * @return true if Bluetooth is enabled, false otherwise.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Checks if the necessary Bluetooth permissions are granted.
     *
     * @return true if all required permissions are granted, false otherwise.
     */
    private fun hasBluetoothPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Starts discovering Bluetooth devices.
     *
     * @return true if discovery started successfully, false otherwise.
     */
    fun startDiscovery(): Boolean {
        if (!isBluetoothEnabled() || !hasBluetoothPermissions()) {
            return false
        }
        
        bluetoothAdapter?.let { adapter ->
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            
            // Clear previous results
            _discoveredDevices.value = emptyList()
            
            // Add paired devices first
            val pairedDevices = adapter.bondedDevices
            val pairedDeviceInfos = pairedDevices.map { device ->
                BluetoothDeviceInfo(
                    address = device.address,
                    name = device.name ?: "Unknown Device"
                )
            }
            _discoveredDevices.value = pairedDeviceInfos
            
            // Start discovery
            _isScanning.value = true
            return adapter.startDiscovery()
        }
        
        return false
    }
    
    /**
     * Stops device discovery.
     */
    fun stopDiscovery() {
        if (hasBluetoothPermissions()) {
            bluetoothAdapter?.cancelDiscovery()
        }
        _isScanning.value = false
    }
    
    /**
     * Sets the device to track for connections.
     *
     * @param deviceAddress The address of the Bluetooth device to track.
     */
    fun setTrackedDevice(deviceAddress: String) {
        trackedDeviceAddress = deviceAddress
        
        // Check current connection state
        if (hasBluetoothPermissions()) {
            bluetoothAdapter?.let { adapter ->
                val device = adapter.getRemoteDevice(deviceAddress)
                // Note: There's no direct way to check if a device is connected via Bluetooth Classic
                // The connection state will be updated via broadcast receivers
            }
        }
    }
    
    /**
     * Cleans up resources by stopping discovery and unregistering broadcast receivers.
     */
    fun cleanup() {
        try {
            stopDiscovery()
            context.unregisterReceiver(discoveryReceiver)
            context.unregisterReceiver(connectionReceiver)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Bluetooth receiver was already unregistered")
        }
    }
}

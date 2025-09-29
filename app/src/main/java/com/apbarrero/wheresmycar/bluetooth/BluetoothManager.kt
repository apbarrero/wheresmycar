package com.apbarrero.wheresmycar.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.apbarrero.wheresmycar.data.BluetoothDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothManager(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as SystemBluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unknown)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var trackedDeviceAddress: String? = null
    
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
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Check if we have the necessary Bluetooth permissions
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
     * Start discovering Bluetooth devices
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
     * Stop device discovery
     */
    fun stopDiscovery() {
        if (hasBluetoothPermissions()) {
            bluetoothAdapter?.cancelDiscovery()
        }
        _isScanning.value = false
    }
    
    /**
     * Set the device to track for connections
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
     * Clean up resources
     */
    fun cleanup() {
        try {
            stopDiscovery()
            context.unregisterReceiver(discoveryReceiver)
            context.unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            // Receiver might already be unregistered
        }
    }
}
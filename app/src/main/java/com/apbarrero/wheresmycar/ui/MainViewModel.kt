package com.apbarrero.wheresmycar.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apbarrero.wheresmycar.bluetooth.BluetoothManager
import com.apbarrero.wheresmycar.data.AppSettings
import timber.log.Timber
import com.apbarrero.wheresmycar.data.BluetoothDeviceInfo
import com.apbarrero.wheresmycar.data.Repository
import com.apbarrero.wheresmycar.service.ParkingTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the main UI screen.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = Repository(application)
    private val bluetoothManager = BluetoothManager(application)
    
    /**
     * A flow that emits the current UI state.
     */
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        // Observe app settings
        viewModelScope.launch {
            repository.appSettings.collect { settings ->
                _uiState.value = _uiState.value.copy(appSettings = settings)
            }
        }
        
        // Observe discovered devices
        viewModelScope.launch {
            bluetoothManager.discoveredDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(discoveredDevices = devices)
            }
        }
        
        // Observe scanning state
        viewModelScope.launch {
            bluetoothManager.isScanning.collect { isScanning ->
                _uiState.value = _uiState.value.copy(isScanning = isScanning)
            }
        }
        
        // Observe connection state
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connectionState ->
                _uiState.value = _uiState.value.copy(connectionState = connectionState)
            }
        }
    }
    
    /**
     * Data class representing the UI state.
     */
    data class UiState(
        val appSettings: AppSettings = AppSettings(),
        val discoveredDevices: List<BluetoothDeviceInfo> = emptyList(),
        val isScanning: Boolean = false,
        val connectionState: BluetoothManager.ConnectionState = BluetoothManager.ConnectionState.Unknown,
        val showDeviceSelection: Boolean = false,
        val errorMessage: String? = null
    )
    
    /**
     * Starts the discovery of Bluetooth devices.
     */
    fun startDeviceDiscovery() {
        if (!bluetoothManager.isBluetoothEnabled()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Bluetooth is not enabled. Please enable Bluetooth and try again."
            )
            return
        }
        
        val success = bluetoothManager.startDiscovery()
        if (!success) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to start device discovery. Please check Bluetooth permissions."
            )
        }
    }
    
    /**
     * Stops the discovery of Bluetooth devices.
     */
    fun stopDeviceDiscovery() {
        bluetoothManager.stopDiscovery()
    }
    
    /**
     * Selects a Bluetooth device for tracking.
     *
     * @param device The selected Bluetooth device information.
     */
    fun selectDevice(device: BluetoothDeviceInfo) {
        viewModelScope.launch {
            try {
                // Save selected device
                repository.saveSelectedDevice(device)
                
                // Set up tracking
                bluetoothManager.setTrackedDevice(device.address)
                
                // Enable tracking
                repository.setTrackingEnabled(true)
                
                // Start the tracking service
                startTrackingService(device)
                
                _uiState.value = _uiState.value.copy(showDeviceSelection = false)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to select device")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to select device: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Toggles the tracking state.
     */
    fun toggleTracking() {
        val currentSettings = _uiState.value.appSettings
        
        viewModelScope.launch {
            if (currentSettings.isTrackingEnabled) {
                // Stop tracking
                repository.setTrackingEnabled(false)
                stopTrackingService()
            } else {
                // Start tracking - need to select device first if none selected
                if (currentSettings.selectedDeviceAddress == null) {
                    _uiState.value = _uiState.value.copy(showDeviceSelection = true)
                } else {
                    repository.setTrackingEnabled(true)
                    val device = BluetoothDeviceInfo(
                        address = currentSettings.selectedDeviceAddress!!,
                        name = currentSettings.selectedDeviceName ?: "Unknown Device"
                    )
                    startTrackingService(device)
                }
            }
        }
    }
    
    /**
     * Shows the device selection dialog.
     */
    fun showDeviceSelection() {
        _uiState.value = _uiState.value.copy(showDeviceSelection = true)
    }
    
    /**
     * Hides the device selection dialog.
     */
    fun hideDeviceSelection() {
        _uiState.value = _uiState.value.copy(showDeviceSelection = false)
    }
    
    /**
     * Clears any error messages.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * Starts the parking tracking service.
     *
     * @param device The Bluetooth device to track.
     */
    private fun startTrackingService(device: BluetoothDeviceInfo) {
        val context = getApplication<Application>()
        val intent = Intent(context, ParkingTrackingService::class.java).apply {
            action = ParkingTrackingService.ACTION_START_TRACKING
            putExtra(ParkingTrackingService.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(ParkingTrackingService.EXTRA_DEVICE_NAME, device.name)
        }
        context.startForegroundService(intent)
    }
    
    /**
     * Stops the parking tracking service.
     */
    private fun stopTrackingService() {
        val context = getApplication<Application>()
        val intent = Intent(context, ParkingTrackingService::class.java).apply {
            action = ParkingTrackingService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
    }
    
    override fun onCleared() {
        super.onCleared()
        bluetoothManager.cleanup()
    }
}

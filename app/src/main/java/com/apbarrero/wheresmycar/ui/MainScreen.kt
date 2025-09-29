package com.apbarrero.wheresmycar.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apbarrero.wheresmycar.data.ParkingLocation
import com.google.accompanist.permissions.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Required permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    // Show error dialog if there's an error message
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Show device selection dialog if needed
    if (uiState.showDeviceSelection) {
        DeviceSelectionDialog(
            devices = uiState.discoveredDevices,
            isScanning = uiState.isScanning,
            onDeviceSelected = { device ->
                viewModel.selectDevice(device)
            },
            onStartScan = { viewModel.startDeviceDiscovery() },
            onStopScan = { viewModel.stopDeviceDiscovery() },
            onDismiss = { viewModel.hideDeviceSelection() }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Where's My Car") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!permissionsState.allPermissionsGranted) {
                PermissionContent(
                    permissionsState = permissionsState,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            } else {
                MainContent(
                    uiState = uiState,
                    onToggleTracking = { viewModel.toggleTracking() },
                    onChangeDevice = { viewModel.showDeviceSelection() }
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionContent(
    permissionsState: MultiplePermissionsState,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "This app needs Bluetooth and Location permissions to track your car's parking location when your Bluetooth device disconnects.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (permissionsState.shouldShowRationale) {
                Button(
                    onClick = { permissionsState.launchMultiplePermissionRequest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permissions")
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Settings")
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    uiState: MainViewModel.UiState,
    onToggleTracking: () -> Unit,
    onChangeDevice: () -> Unit
) {
    // Current device status
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tracking Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            val statusText = if (uiState.appSettings.isTrackingEnabled) {
                "✅ Tracking enabled"
            } else {
                "❌ Tracking disabled"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge
            )
            
            uiState.appSettings.selectedDeviceName?.let { deviceName ->
                Text(
                    text = "Device: $deviceName",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                val connectionStatus = when (uiState.connectionState) {
                    com.apbarrero.wheresmycar.bluetooth.BluetoothManager.ConnectionState.Connected -> "🔗 Connected"
                    com.apbarrero.wheresmycar.bluetooth.BluetoothManager.ConnectionState.Disconnected -> "📴 Disconnected"
                    com.apbarrero.wheresmycar.bluetooth.BluetoothManager.ConnectionState.Unknown -> "❓ Unknown"
                }
                
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.appSettings.isTrackingEnabled) "Stop Tracking" else "Start Tracking")
                }
                
                OutlinedButton(
                    onClick = onChangeDevice,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Change Device")
                }
            }
        }
    }
    
    // Last parking location
    uiState.appSettings.lastKnownLocation?.let { location ->
        LastParkingLocationCard(location = location)
    } ?: run {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚗",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No parking location saved yet",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Start tracking to automatically save your parking location when your device disconnects",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LastParkingLocationCard(location: ParkingLocation) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault()) }
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🅿️ Last Parking Location",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Device: ${location.deviceName}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Time: ${dateFormat.format(location.timestamp)}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Coordinates: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            location.address?.let { address ->
                Text(
                    text = "Address: $address",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Button(
                onClick = {
                    val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(My Car)")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open in Maps")
            }
        }
    }
}
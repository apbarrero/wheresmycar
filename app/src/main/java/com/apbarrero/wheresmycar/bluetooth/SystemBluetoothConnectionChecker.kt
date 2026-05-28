package com.apbarrero.wheresmycar.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile

class SystemBluetoothConnectionChecker(
    private val bluetoothManager: BluetoothManager,
) : BluetoothConnectionChecker {

    // Profiles a tracked device is likely to use:
    // A2DP = car audio / headphones, HEADSET = HFP hands-free, GATT = BLE peripherals.
    private val profilesToCheck = listOf(
        BluetoothProfile.A2DP,
        BluetoothProfile.HEADSET,
        BluetoothProfile.GATT,
    )

    override fun isDeviceConnected(deviceAddress: String): Boolean =
        profilesToCheck.any { profile ->
            bluetoothManager.getConnectedDevices(profile).any { it.address == deviceAddress }
        }
}

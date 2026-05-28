package com.apbarrero.wheresmycar.bluetooth

interface BluetoothConnectionChecker {
    fun isDeviceConnected(deviceAddress: String): Boolean
}

package com.apbarrero.wheresmycar.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemBluetoothConnectionCheckerTest {

    private val TARGET = "AA:BB:CC:DD:EE:FF"
    private val OTHER  = "11:22:33:44:55:66"

    private lateinit var btManager: BluetoothManager
    private lateinit var checker: SystemBluetoothConnectionChecker

    @Before
    fun setUp() {
        btManager = mockk()
        checker = SystemBluetoothConnectionChecker(btManager)
    }

    private fun device(address: String): BluetoothDevice = mockk<BluetoothDevice>().also {
        every { it.address } returns address
    }

    private fun noDevices() {
        every { btManager.getConnectedDevices(any()) } returns emptyList()
    }

    @Test
    fun `returns true when target device is connected via A2DP`() {
        every { btManager.getConnectedDevices(BluetoothProfile.A2DP) } returns listOf(device(TARGET))
        every { btManager.getConnectedDevices(BluetoothProfile.HEADSET) } returns emptyList()
        every { btManager.getConnectedDevices(BluetoothProfile.GATT) } returns emptyList()

        assertTrue(checker.isDeviceConnected(TARGET))
    }

    @Test
    fun `returns true when target device is connected via HEADSET`() {
        every { btManager.getConnectedDevices(BluetoothProfile.A2DP) } returns emptyList()
        every { btManager.getConnectedDevices(BluetoothProfile.HEADSET) } returns listOf(device(TARGET))
        every { btManager.getConnectedDevices(BluetoothProfile.GATT) } returns emptyList()

        assertTrue(checker.isDeviceConnected(TARGET))
    }

    @Test
    fun `returns true when target device is connected via GATT`() {
        every { btManager.getConnectedDevices(BluetoothProfile.A2DP) } returns emptyList()
        every { btManager.getConnectedDevices(BluetoothProfile.HEADSET) } returns emptyList()
        every { btManager.getConnectedDevices(BluetoothProfile.GATT) } returns listOf(device(TARGET))

        assertTrue(checker.isDeviceConnected(TARGET))
    }

    @Test
    fun `returns false when target device is not connected via any profile`() {
        noDevices()
        assertFalse(checker.isDeviceConnected(TARGET))
    }

    @Test
    fun `returns false when a different device is connected`() {
        every { btManager.getConnectedDevices(BluetoothProfile.A2DP) } returns listOf(device(OTHER))
        every { btManager.getConnectedDevices(BluetoothProfile.HEADSET) } returns emptyList()
        every { btManager.getConnectedDevices(BluetoothProfile.GATT) } returns emptyList()

        assertFalse(checker.isDeviceConnected(TARGET))
    }

    @Test
    fun `returns true when target is among multiple connected devices`() {
        every { btManager.getConnectedDevices(BluetoothProfile.A2DP) } returns listOf(device(OTHER), device(TARGET))
        every { btManager.getConnectedDevices(BluetoothProfile.HEADSET) } returns emptyList()
        every { btManager.getConnectedDevices(BluetoothProfile.GATT) } returns emptyList()

        assertTrue(checker.isDeviceConnected(TARGET))
    }
}

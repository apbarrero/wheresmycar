package com.apbarrero.wheresmycar.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BluetoothConnectionMonitorTest {

    private var disconnectCallCount = 0
    private lateinit var monitor: BluetoothConnectionMonitor

    @Before
    fun setUp() {
        disconnectCallCount = 0
        monitor = BluetoothConnectionMonitor(onDisconnected = { disconnectCallCount++ })
    }

    // --- notifyConnected / notifyDisconnected (broadcast receiver path) ---

    @Test
    fun `notifyConnected sets state to connected and establishes baseline`() {
        monitor.notifyConnected()
        assertTrue(monitor.connected)
        assertEquals(0, disconnectCallCount)
    }

    @Test
    fun `notifyDisconnected fires callback immediately regardless of baseline`() {
        monitor.notifyDisconnected()
        assertEquals(1, disconnectCallCount)
        assertFalse(monitor.connected)
    }

    @Test
    fun `notifyDisconnected establishes baseline so subsequent poll does not re-fire`() {
        monitor.notifyDisconnected()           // broadcast: device disconnected
        monitor.checkState(false)              // poll confirms disconnected — no new callback
        assertEquals(1, disconnectCallCount)
    }

    @Test
    fun `notifyConnected then notifyDisconnected fires callback once`() {
        monitor.notifyConnected()
        monitor.notifyDisconnected()
        assertEquals(1, disconnectCallCount)
    }

    // --- checkState (polling path) ---

    @Test
    fun `first checkState connected establishes baseline without callback`() {
        monitor.checkState(true)
        assertTrue(monitor.connected)
        assertEquals(0, disconnectCallCount)
    }

    @Test
    fun `first checkState disconnected establishes baseline without callback`() {
        monitor.checkState(false)
        assertFalse(monitor.connected)
        assertEquals(0, disconnectCallCount)
    }

    @Test
    fun `checkState connected to disconnected fires callback`() {
        monitor.checkState(true)
        monitor.checkState(false)
        assertEquals(1, disconnectCallCount)
        assertFalse(monitor.connected)
    }

    @Test
    fun `checkState disconnected to connected does not fire callback`() {
        monitor.checkState(false)
        monitor.checkState(true)
        assertEquals(0, disconnectCallCount)
        assertTrue(monitor.connected)
    }

    @Test
    fun `checkState staying connected does not fire callback`() {
        monitor.checkState(true)
        monitor.checkState(true)
        assertEquals(0, disconnectCallCount)
    }

    @Test
    fun `checkState staying disconnected does not fire callback`() {
        monitor.checkState(false)
        monitor.checkState(false)
        assertEquals(0, disconnectCallCount)
    }

    @Test
    fun `callback fires once per disconnect transition not on subsequent disconnected polls`() {
        monitor.checkState(true)
        monitor.checkState(false) // transition → callback
        monitor.checkState(false) // already disconnected → no callback
        assertEquals(1, disconnectCallCount)
    }

    @Test
    fun `reconnect then disconnect fires callback again`() {
        monitor.checkState(true)
        monitor.checkState(false) // first disconnect
        monitor.checkState(true)  // reconnect
        monitor.checkState(false) // second disconnect
        assertEquals(2, disconnectCallCount)
    }

    // --- reset ---

    @Test
    fun `reset clears baseline so next checkState is treated as first observation`() {
        monitor.checkState(true) // baseline: connected
        monitor.reset()
        monitor.checkState(false) // new baseline: disconnected — no callback
        assertEquals(0, disconnectCallCount)
        assertFalse(monitor.connected)
    }

    @Test
    fun `after reset disconnect transition fires callback again`() {
        monitor.checkState(true)
        monitor.checkState(false) // first disconnect (callback)
        monitor.reset()
        monitor.checkState(true)  // new baseline: connected
        monitor.checkState(false) // second disconnect (callback)
        assertEquals(2, disconnectCallCount)
    }

    @Test
    fun `reset after notifyConnected clears baseline`() {
        monitor.notifyConnected()
        monitor.reset()
        monitor.checkState(true) // new baseline — no callback
        assertEquals(0, disconnectCallCount)
    }
}

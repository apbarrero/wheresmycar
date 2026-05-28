package com.apbarrero.wheresmycar.bluetooth

/**
 * Tracks Bluetooth connection-state transitions for a single device and fires
 * [onDisconnected] when a connected→disconnected transition is observed.
 *
 * Two input paths exist because reliable signals (broadcast receivers) and
 * polling observations behave differently with respect to baseline establishment:
 *
 * - [notifyConnected] / [notifyDisconnected]: the caller knows with certainty that
 *   the device just connected/disconnected (e.g. ACL broadcast). These always update
 *   state and [notifyDisconnected] always fires the callback — no baseline guard.
 *
 * - [checkState]: the caller is polling and cannot distinguish "device was already
 *   disconnected before we started" from "device just disconnected". The first call
 *   silently establishes the baseline; only subsequent transitions fire the callback.
 *
 * Call [reset] when the tracked device changes so the next observation is treated
 * as a fresh baseline.
 *
 * Thread-safe: polling runs on a background thread; broadcasts arrive on the main thread.
 */
class BluetoothConnectionMonitor(private val onDisconnected: () -> Unit) {

    @Volatile private var isConnected = false
    @Volatile private var hasBaseline = false

    val connected: Boolean get() = isConnected

    /** Called when a broadcast receiver confirms the device connected. */
    @Synchronized
    fun notifyConnected() {
        isConnected = true
        hasBaseline = true
    }

    /** Called when a broadcast receiver confirms the device disconnected. */
    @Synchronized
    fun notifyDisconnected() {
        isConnected = false
        hasBaseline = true
        onDisconnected()
    }

    /**
     * Called with the result of a periodic poll.
     * The first call establishes the baseline without firing the callback;
     * subsequent calls fire [onDisconnected] on a connected→disconnected transition.
     */
    @Synchronized
    fun checkState(currentlyConnected: Boolean) {
        if (!hasBaseline) {
            isConnected = currentlyConnected
            hasBaseline = true
            return
        }
        if (isConnected && !currentlyConnected) {
            isConnected = false
            onDisconnected()
        } else if (!isConnected && currentlyConnected) {
            isConnected = true
        }
    }

    /** Clears baseline and state. Call when the tracked device address changes. */
    @Synchronized
    fun reset() {
        isConnected = false
        hasBaseline = false
    }
}

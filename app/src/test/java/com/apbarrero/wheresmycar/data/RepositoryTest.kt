package com.apbarrero.wheresmycar.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testScope = TestScope()
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        repository = Repository(dataStore)
    }

    // --- appSettings defaults ---

    @Test
    fun `appSettings emits defaults when store is empty`() = testScope.runTest {
        val settings = repository.appSettings.first()
        assertNull(settings.selectedDeviceAddress)
        assertNull(settings.selectedDeviceName)
        assertFalse(settings.isTrackingEnabled)
        assertNull(settings.lastKnownLocation)
    }

    // --- saveSelectedDevice ---

    @Test
    fun `saveSelectedDevice persists address and name`() = testScope.runTest {
        repository.saveSelectedDevice(BluetoothDeviceInfo("AA:BB:CC:DD:EE:FF", "My Car"))
        val settings = repository.appSettings.first()
        assertEquals("AA:BB:CC:DD:EE:FF", settings.selectedDeviceAddress)
        assertEquals("My Car", settings.selectedDeviceName)
    }

    @Test
    fun `saveSelectedDevice overwrites previously saved device`() = testScope.runTest {
        repository.saveSelectedDevice(BluetoothDeviceInfo("AA:BB:CC", "Old Car"))
        repository.saveSelectedDevice(BluetoothDeviceInfo("11:22:33", "New Car"))
        val settings = repository.appSettings.first()
        assertEquals("11:22:33", settings.selectedDeviceAddress)
        assertEquals("New Car", settings.selectedDeviceName)
    }

    // --- setTrackingEnabled ---

    @Test
    fun `setTrackingEnabled true persists enabled state`() = testScope.runTest {
        repository.setTrackingEnabled(true)
        assertTrue(repository.appSettings.first().isTrackingEnabled)
    }

    @Test
    fun `setTrackingEnabled false persists disabled state`() = testScope.runTest {
        repository.setTrackingEnabled(true)
        repository.setTrackingEnabled(false)
        assertFalse(repository.appSettings.first().isTrackingEnabled)
    }

    // --- saveParkingLocation ---

    @Test
    fun `saveParkingLocation persists all required fields`() = testScope.runTest {
        val ts = Instant.ofEpochMilli(1_700_000_000_000)
        repository.saveParkingLocation(ParkingLocation(51.5074, -0.1278, ts, "My Car"))
        val saved = repository.appSettings.first().lastKnownLocation
        assertNotNull(saved)
        assertEquals(51.5074, saved!!.latitude, 1e-9)
        assertEquals(-0.1278, saved.longitude, 1e-9)
        assertEquals(ts.toEpochMilli(), saved.timestamp.toEpochMilli())
        assertEquals("My Car", saved.deviceName)
        assertNull(saved.address)
    }

    @Test
    fun `saveParkingLocation persists optional address`() = testScope.runTest {
        repository.saveParkingLocation(ParkingLocation(51.5, -0.1, Instant.now(), "My Car", "10 Downing St"))
        assertEquals("10 Downing St", repository.appSettings.first().lastKnownLocation?.address)
    }

    @Test
    fun `saveParkingLocation without address clears previously stored address`() = testScope.runTest {
        repository.saveParkingLocation(ParkingLocation(1.0, 2.0, Instant.now(), "Car", "Old Address"))
        repository.saveParkingLocation(ParkingLocation(3.0, 4.0, Instant.now(), "Car"))
        assertNull(repository.appSettings.first().lastKnownLocation?.address)
    }

    @Test
    fun `lastKnownLocation is null when no parking location has been saved`() = testScope.runTest {
        repository.saveSelectedDevice(BluetoothDeviceInfo("AA:BB", "Car"))
        repository.setTrackingEnabled(true)
        assertNull(repository.appSettings.first().lastKnownLocation)
    }

    // --- clearAllData ---

    @Test
    fun `clearAllData resets all fields to defaults`() = testScope.runTest {
        repository.saveSelectedDevice(BluetoothDeviceInfo("AA:BB:CC", "My Car"))
        repository.setTrackingEnabled(true)
        repository.saveParkingLocation(ParkingLocation(51.5, -0.1, Instant.now(), "My Car", "Addr"))

        repository.clearAllData()

        val settings = repository.appSettings.first()
        assertNull(settings.selectedDeviceAddress)
        assertNull(settings.selectedDeviceName)
        assertFalse(settings.isTrackingEnabled)
        assertNull(settings.lastKnownLocation)
    }
}

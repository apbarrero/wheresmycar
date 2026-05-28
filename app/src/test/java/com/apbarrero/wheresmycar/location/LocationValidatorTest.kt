package com.apbarrero.wheresmycar.location

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationValidatorTest {

    private val NOW = 1_700_000_000_000L

    private fun location(ageMs: Long = 0L, accuracy: Float? = 10f): Location =
        mockk<Location>().also {
            every { it.time } returns (NOW - ageMs)
            if (accuracy != null) {
                every { it.hasAccuracy() } returns true
                every { it.accuracy } returns accuracy
            } else {
                every { it.hasAccuracy() } returns false
            }
        }

    // --- null ---

    @Test
    fun `null location is not usable`() {
        assertFalse(LocationValidator.isUsable(null, NOW))
    }

    // --- age ---

    @Test
    fun `fresh location is usable`() {
        assertTrue(LocationValidator.isUsable(location(ageMs = 0), NOW))
    }

    @Test
    fun `location just within age limit is usable`() {
        assertTrue(LocationValidator.isUsable(location(ageMs = LocationValidator.MAX_AGE_MS - 1), NOW))
    }

    @Test
    fun `location exactly at age limit is usable`() {
        // age == MAX_AGE_MS: condition is age > MAX so this still passes
        assertTrue(LocationValidator.isUsable(location(ageMs = LocationValidator.MAX_AGE_MS), NOW))
    }

    @Test
    fun `location one millisecond over age limit is not usable`() {
        assertFalse(LocationValidator.isUsable(location(ageMs = LocationValidator.MAX_AGE_MS + 1), NOW))
    }

    @Test
    fun `stale location is not usable`() {
        assertFalse(LocationValidator.isUsable(location(ageMs = 5 * 60_000L), NOW))
    }

    // --- accuracy ---

    @Test
    fun `location without accuracy data is usable`() {
        assertTrue(LocationValidator.isUsable(location(accuracy = null), NOW))
    }

    @Test
    fun `location with good accuracy is usable`() {
        assertTrue(LocationValidator.isUsable(location(accuracy = 10f), NOW))
    }

    @Test
    fun `location with accuracy exactly at limit is usable`() {
        // accuracy == MAX_INACCURACY_M: condition is accuracy > MAX so this still passes
        assertTrue(LocationValidator.isUsable(location(accuracy = LocationValidator.MAX_INACCURACY_M), NOW))
    }

    @Test
    fun `location with accuracy one unit over limit is not usable`() {
        assertFalse(LocationValidator.isUsable(location(accuracy = LocationValidator.MAX_INACCURACY_M + 0.001f), NOW))
    }

    @Test
    fun `location with poor accuracy is not usable`() {
        assertFalse(LocationValidator.isUsable(location(accuracy = 500f), NOW))
    }

    // --- combined ---

    @Test
    fun `stale location with good accuracy is not usable`() {
        assertFalse(LocationValidator.isUsable(location(ageMs = 120_000L, accuracy = 5f), NOW))
    }

    @Test
    fun `fresh location with poor accuracy is not usable`() {
        assertFalse(LocationValidator.isUsable(location(ageMs = 0, accuracy = 200f), NOW))
    }
}

package com.vague.crewtally.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Headless JVM unit tests for [Converters] — runnable with `./gradlew testDebugUnitTest`, no
 * device required. DAO-level tests need Room's SQLite runtime and live in `androidTest`
 * ([CrewTallyDatabaseTest]); they require a connected device or emulator.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun localDate_roundTrips_throughIsoString() {
        // Arrange
        val date = LocalDate.of(2026, 8, 1)

        // Act
        val stored = converters.fromLocalDate(date)
        val restored = converters.toLocalDate(stored)

        // Assert
        assertEquals("2026-08-01", stored)
        assertEquals(date, restored)
    }

    @Test
    fun localDate_null_roundTripsAsNull() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun projectStatus_roundTrips_byName() {
        ProjectStatus.entries.forEach { status ->
            val stored = converters.fromProjectStatus(status)
            assertEquals(status.name, stored)
            assertEquals(status, converters.toProjectStatus(stored))
        }
    }

    @Test
    fun projectStatus_null_roundTripsAsNull() {
        assertNull(converters.fromProjectStatus(null))
        assertNull(converters.toProjectStatus(null))
    }
}

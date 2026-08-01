package com.vague.crewtally.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

    @Test
    fun `blank query matches everything`() {
        // Arrange
        val query = "   "

        // Act
        val result = matchesSearch(query, "Jad Awad", "+961 3 123456")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `query matches a field case-insensitively`() {
        // Arrange
        val query = "jad"

        // Act
        val result = matchesSearch(query, "Jad Awad", "+961 3 123456")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `query matches a middle substring of a field`() {
        // Arrange
        val query = "wad"

        // Act
        val result = matchesSearch(query, "Jad Awad", "")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `query matches phone digits`() {
        // Arrange
        val query = "123456"

        // Act
        val result = matchesSearch(query, "Jad Awad", "+961 3 123456")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `query matching no field returns false`() {
        // Arrange
        val query = "zzz"

        // Act
        val result = matchesSearch(query, "Jad Awad", "+961 3 123456")

        // Assert
        assertFalse(result)
    }

    @Test
    fun `leading and trailing whitespace in query is trimmed`() {
        // Arrange
        val query = "  Awad  "

        // Act
        val result = matchesSearch(query, "Jad Awad", "")

        // Assert
        assertTrue(result)
    }
}

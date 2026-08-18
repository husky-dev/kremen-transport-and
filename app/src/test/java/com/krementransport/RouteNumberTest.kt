package com.krementransport

import com.krementransport.domain.model.RouteNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNumberTest {

    @Test
    fun `strips the trolleybus prefix, hyphens and spaces`() {
        assertEquals("1", RouteNumber.badge("1"))
        assertEquals("1+", RouteNumber.badge("Т 1+"))
        assertEquals("3Б", RouteNumber.badge("3-б"))
        assertEquals("15Б", RouteNumber.badge("T15Б"))
        assertEquals("10A", RouteNumber.badge("10A"))
        assertEquals("117", RouteNumber.badge("117"))
        assertEquals("2В", RouteNumber.badge("2-в"))
    }

    @Test
    fun `a fully alphabetic number keeps its prefix rather than vanishing`() {
        assertEquals("TT", RouteNumber.badge("TT"))
    }

    @Test
    fun `sorts numerically, then by suffix`() {
        val input = listOf("117", "10A", "3-а", "2-в", "15-б", "15", "Т 1")
        val sorted = input.sortedWith(
            compareBy({ RouteNumber.sortKey(it).first }, { RouteNumber.sortKey(it).second }),
        )
        assertEquals(listOf("Т 1", "2-в", "3-а", "10A", "15", "15-б", "117"), sorted)
    }

    @Test
    fun `search tolerates the Cyrillic and Latin T mix-up`() {
        // A Latin keyboard has to be able to find the Cyrillic routes, and vice versa.
        assertTrue(RouteNumber.matches("Т 1", "T1"))
        assertTrue(RouteNumber.matches("T15Б", "т15б"))
        assertTrue(RouteNumber.matches("3-б", "3б"))
        assertTrue(RouteNumber.matches("117", "17"))
        assertFalse(RouteNumber.matches("117", "9"))
    }

    @Test
    fun `an empty query matches everything`() {
        assertTrue(RouteNumber.matches("117", ""))
        assertTrue(RouteNumber.matches("117", "   "))
    }
}

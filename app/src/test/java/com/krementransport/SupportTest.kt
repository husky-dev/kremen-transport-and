package com.krementransport

import androidx.compose.ui.graphics.Color
import com.krementransport.domain.model.Coordinate
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.TransitKind
import com.krementransport.domain.model.collapseSpaces
import com.krementransport.domain.model.distanceTo
import com.krementransport.util.DistanceLabel
import com.krementransport.util.EtaLabel
import com.krementransport.util.PolylineSimplifier
import com.krementransport.util.contrastingLabel
import com.krementransport.util.distanceLabel
import com.krementransport.util.etaLabel
import com.krementransport.util.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `an arrival inside five seconds reads as now`() {
        assertEquals(EtaLabel.Now, etaLabel(0))
        assertEquals(EtaLabel.Now, etaLabel(5))
        assertEquals(EtaLabel.Seconds(6), etaLabel(6))
        assertEquals(EtaLabel.Seconds(59), etaLabel(59))
    }

    @Test
    fun `minutes round up, never down`() {
        // 61 seconds is two minutes. Rounding down reads as a bus you can still catch.
        assertEquals(EtaLabel.Minutes(1), etaLabel(60))
        assertEquals(EtaLabel.Minutes(2), etaLabel(61))
        assertEquals(EtaLabel.Minutes(8), etaLabel(430))
    }

    @Test
    fun `distance switches to kilometres at a thousand metres`() {
        assertEquals(DistanceLabel.Meters(999), distanceLabel(999))
        assertEquals(DistanceLabel.Kilometers("1.0"), distanceLabel(1000))
        assertEquals(DistanceLabel.Kilometers("3.3"), distanceLabel(3347))
    }
}

class ColorHexTest {

    @Test
    fun `parses the six digit form in either case`() {
        assertEquals(Color(0xFF, 0x7E, 0x23), parseHexColor("#FF7E23"))
        assertEquals(Color(0xE2, 0x84, 0x78), parseHexColor("#e28478"))
        assertEquals(Color(0x3E, 0x7F, 0xE8), parseHexColor("3E7FE8"))
    }

    @Test
    fun `rejects anything that is not a colour`() {
        assertNull(parseHexColor(null))
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GGGGGG"))
    }

    @Test
    fun `label colour follows luminance`() {
        assertEquals(Color.White, Color(0xFF2961DC).contrastingLabel())
        assertEquals(Color.Black, Color(0xFFF0C40B).contrastingLabel())
    }
}

class PolylineSimplifierTest {

    @Test
    fun `a straight line collapses to its endpoints`() {
        val points = (0..20).map { Coordinate(49.0 + it * 0.001, 33.0) }
        val simplified = PolylineSimplifier.simplify(points, toleranceMeters = 12.0)
        assertEquals(2, simplified.size)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun `a real detour survives the tolerance`() {
        val points = listOf(
            Coordinate(49.000, 33.000),
            Coordinate(49.005, 33.010),
            Coordinate(49.010, 33.000),
        )
        assertEquals(3, PolylineSimplifier.simplify(points, toleranceMeters = 12.0).size)
    }

    @Test
    fun `endpoints are always kept and order is preserved`() {
        val points = (0..50).map { Coordinate(49.0 + it * 0.0005, 33.0 + (it % 3) * 0.0001) }
        val simplified = PolylineSimplifier.simplify(points, toleranceMeters = 60.0)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue(simplified.size <= points.size)
        assertTrue(simplified.zipWithNext().all { (a, b) -> a.latitude <= b.latitude })
    }

    @Test
    fun `distance is metres, not degrees`() {
        // One thousandth of a degree of latitude is about 111 m anywhere on earth.
        val metres = Coordinate(49.0, 33.0).distanceTo(Coordinate(49.001, 33.0))
        assertEquals(111.0, metres, 1.0)
    }
}

class RouteTest {

    private fun route(name: String) = Route(
        rid = 1,
        name = name,
        number = "1",
        type = TransitKind.Bus,
        active = 1,
        color = null,
        path = emptyList(),
        stations = emptyList(),
    )

    @Test
    fun `splits a name on any of the dashes upstream uses`() {
        assertEquals("Річковий вокзал" to "Укртатнафта", route("Річковий вокзал – Укртатнафта").endpoints)
        assertEquals("A" to "B", route("A - B").endpoints)
        assertEquals("A" to "B", route("A—B").endpoints)
    }

    @Test
    fun `destination follows the direction of travel`() {
        val value = route("Річковий вокзал – Укртатнафта")
        assertEquals("Укртатнафта", value.destination(reverse = false))
        assertEquals("Річковий вокзал", value.destination(reverse = true))
    }

    @Test
    fun `a name with no separator still yields a destination`() {
        assertNull(route("Центральний ринок").endpoints)
        assertEquals("Центральний ринок", route("Центральний ринок").destination(reverse = false))
    }

    @Test
    fun `doubled spaces are collapsed`() {
        assertEquals("Центральний ринок", "Центральний  ринок".collapseSpaces())
        assertEquals("A B", "  A   B  ".collapseSpaces())
    }
}

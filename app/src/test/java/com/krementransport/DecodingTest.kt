package com.krementransport

import com.krementransport.data.api.PredictionDto
import com.krementransport.data.api.RouteDto
import com.krementransport.data.api.TransportJson
import com.krementransport.data.api.VehicleDto
import com.krementransport.domain.model.Stop
import com.krementransport.domain.model.TransitKind
import com.krementransport.domain.model.VehicleFix
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Asserted against the actual shape of live payloads, so an upstream change fails here rather
 * than on a user's map. Refresh a fixture by re-fetching the endpoint and updating the counts.
 */
class DecodingTest {

    private val routes by lazy {
        TransportJson.decodeFromString(ListSerializer(RouteDto.serializer()), Fixtures.read("routes"))
            .map { it.toDomain() }
    }

    @Test
    fun `decodes the full route payload`() {
        assertEquals(38, routes.size)
        assertEquals(22, routes.count { it.type == TransitKind.Bus })
        assertEquals(16, routes.count { it.type == TransitKind.Trolleybus })
        assertTrue(routes.all { it.path.isNotEmpty() })
        assertEquals(640, routes.maxOf { it.path.size })
    }

    @Test
    fun `path is lat lng, latitude first`() {
        // Kremenchuk sits near 49 N, 33 E. Reading the pair the other way round would put every
        // route in the Indian Ocean, which is exactly the bug this guards.
        val point = routes.first { it.path.isNotEmpty() }.path.first()
        assertTrue("latitude ${point.latitude}", point.latitude in 48.0..50.0)
        assertTrue("longitude ${point.longitude}", point.longitude in 32.0..35.0)
    }

    @Test
    fun `stations fold to unique physical stops`() {
        assertEquals(1994, routes.sumOf { it.stations.size })
        val stops = Stop.fold(routes)
        assertEquals(433, stops.size)
        // The fold is only lossless because a sid maps 1:1 to a coordinate.
        assertEquals(433, stops.map { it.latitude to it.longitude }.toSet().size)
        assertTrue(stops.all { it.routeIds.isNotEmpty() })
    }

    @Test
    fun `a malformed coordinate pair is dropped, not fatal`() {
        val payload = """
            [{"rid":1,"number":"1","type":"B","name":"A - B",
              "path":[[49.1,33.4],[null,33.4],[49.2],"nope",[49.3,33.5],[999,33.5]],
              "stations":[]}]
        """.trimIndent()
        val decoded = TransportJson
            .decodeFromString(ListSerializer(RouteDto.serializer()), payload)
            .map { it.toDomain() }
        assertEquals(1, decoded.size)
        assertEquals(2, decoded[0].path.size)
        assertEquals(49.1, decoded[0].path[0].latitude, 1e-9)
        assertEquals(49.3, decoded[0].path[1].latitude, 1e-9)
    }

    @Test
    fun `decodes the vehicle roster`() {
        val vehicles = TransportJson
            .decodeFromString(ListSerializer(VehicleDto.serializer()), Fixtures.read("buses"))
            .map { it.toDomain() }
        assertEquals(319, vehicles.size)
        assertEquals(117, vehicles.count { it.offline })
        assertEquals(319, vehicles.map { it.tid }.toSet().size)
        assertNotNull(vehicles.first().updatedAt)
    }

    @Test
    fun `updated_at parses with and without fractional seconds`() {
        val withFraction = VehicleDto(tid = "a", updatedAt = "2026-08-13T13:29:39.584987Z").toDomain()
        val plain = VehicleDto(tid = "b", updatedAt = "2026-08-13T13:29:39Z").toDomain()
        assertEquals(Instant.parse("2026-08-13T13:29:39.584987Z"), withFraction.updatedAt)
        assertEquals(Instant.parse("2026-08-13T13:29:39Z"), plain.updatedAt)
        assertNull(VehicleDto(tid = "c", updatedAt = "not a date").toDomain().updatedAt)
    }

    @Test
    fun `speed of -1 means unknown`() {
        assertNull(VehicleDto(tid = "a", speed = -1).toDomain().knownSpeed)
        assertEquals(0, VehicleDto(tid = "a", speed = 0).toDomain().knownSpeed)
        assertEquals(45, VehicleDto(tid = "a", speed = 45).toDomain().knownSpeed)
    }

    @Test
    fun `decodes the sparse locations map`() {
        val raw = TransportJson.decodeFromString(
            MapSerializer(String.serializer(), ListSerializer(Double.serializer())),
            Fixtures.read("locations"),
        )
        assertEquals(319, raw.size)
        val fixes = raw.mapNotNull { (tid, values) -> VehicleFix.orNull(values)?.let { tid to it } }
        assertEquals(319, fixes.size)
        val (_, first) = fixes.first()
        assertTrue(first.latitude in 48.0..50.0)
        assertTrue(first.longitude in 32.0..35.0)
    }

    @Test
    fun `a short or out of range location tuple is rejected`() {
        assertNull(VehicleFix.orNull(listOf(49.0, 33.0, 180.0)))
        assertNull(VehicleFix.orNull(listOf(91.0, 33.0, 180.0, 20.0)))
        assertNull(VehicleFix.orNull(listOf(49.0, 200.0, 180.0, 20.0)))
        assertNotNull(VehicleFix.orNull(listOf(49.0, 33.0, 180.0, 20.0)))
    }

    @Test
    fun `decodes stop predictions`() {
        val predictions = TransportJson
            .decodeFromString(ListSerializer(PredictionDto.serializer()), Fixtures.read("prediction"))
            .map { it.toDomain() }
        assertEquals(4, predictions.size)
        assertTrue(predictions.all { it.sid == 305 })
        assertTrue(predictions.all { it.seconds > 0 })
        // Every arrival in this capture runs the reverse direction. The stop itself carries no
        // usable direction, so grouping has to come from this flag.
        assertTrue(predictions.all { it.reverse })
    }

    @Test
    fun `unknown transit type decodes as a bus rather than failing`() {
        assertEquals(TransitKind.Bus, TransitKind.from("B"))
        assertEquals(TransitKind.Trolleybus, TransitKind.from("T"))
        assertEquals(TransitKind.Bus, TransitKind.from("Z"))
        assertEquals(TransitKind.Bus, TransitKind.from(null))
    }
}

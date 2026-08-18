package com.krementransport.data.api

import com.krementransport.domain.model.Coordinate
import com.krementransport.domain.model.Prediction
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.Station
import com.krementransport.domain.model.TransitKind
import com.krementransport.domain.model.Vehicle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

/**
 * Wire shapes. Every field but the identifiers carries a default so a null or an absent key
 * degrades one record instead of failing the 565 KB payload; see [TransportJson].
 */
@Serializable
data class RouteDto(
    val rid: Int,
    val name: String = "",
    val number: String = "",
    val type: String = "B",
    val active: Int = 0,
    val color: String? = null,
    /**
     * `[[lat, lng], ...]` — latitude first. Kept as raw JSON so one malformed pair can be
     * dropped rather than throwing; a single bad entry must not cost the whole route layer.
     */
    val path: JsonArray = JsonArray(emptyList()),
    val stations: List<StationDto> = emptyList(),
) {
    fun toDomain(): Route = Route(
        rid = rid,
        name = name,
        number = number,
        type = TransitKind.from(type),
        active = active,
        color = color,
        path = path.mapNotNull(::coordinateOrNull),
        stations = stations.map { it.toDomain() },
    )

    private fun coordinateOrNull(element: JsonElement): Coordinate? {
        val pair = element as? JsonArray ?: return null
        if (pair.size < 2) return null
        val lat = (pair[0] as? JsonPrimitive)?.doubleOrNull
        val lng = (pair[1] as? JsonPrimitive)?.doubleOrNull
        return Coordinate.orNull(lat, lng)
    }
}

@Serializable
data class StationDto(
    val sid: Int,
    val rid: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val name: String = "",
    val sequenceNumber: Int = 0,
    val directionForward: Boolean = true,
) {
    fun toDomain(): Station = Station(sid, rid, lat, lng, name, sequenceNumber, directionForward)
}

@Serializable
data class VehicleDto(
    val tid: String,
    val rid: Int = 0,
    val name: String = "",
    val type: String = "B",
    val offline: Boolean = false,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val direction: Double = 0.0,
    val speed: Int = -1,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain(): Vehicle = Vehicle(
        tid = tid,
        rid = rid,
        name = name,
        type = TransitKind.from(type),
        offline = offline,
        latitude = lat,
        longitude = lng,
        direction = direction,
        speed = speed,
        // Six fractional digits on most records, none on some. `Instant.parse` takes both.
        updatedAt = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}

@Serializable
data class PredictionDto(
    val rid: Int = 0,
    val sid: Int = 0,
    val tid: String = "",
    val prediction: Int = 0,
    val distance: Int = 0,
    val reverse: Boolean = false,
    val avgSpeed: Int = -1,
    val speed: Int = -1,
    val mainPrediction: Boolean = false,
) {
    fun toDomain(): Prediction =
        Prediction(rid, sid, tid, prediction, distance, reverse, avgSpeed, speed, mainPrediction)
}

/** The backend's error body: `{ "code": "...", "message": "..." }`. */
@Serializable
data class ApiErrorBody(val code: String = "UNKNOWN", val message: String? = null)

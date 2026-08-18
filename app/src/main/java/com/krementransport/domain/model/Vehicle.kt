package com.krementransport.domain.model

import java.time.Instant

/** A vehicle as returned by `transport/buses`. `tid` is the GPS device id and is stable. */
data class Vehicle(
    val tid: String,
    val rid: Int,
    val name: String,
    val type: TransitKind,
    val offline: Boolean,
    val latitude: Double,
    val longitude: Double,
    /** Heading in degrees, 0 = north. */
    val direction: Double,
    /** km/h. The backend sends `-1` when it does not know. */
    val speed: Int,
    val updatedAt: Instant?,
) {
    val knownSpeed: Int? get() = speed.takeIf { it >= 0 }

    fun apply(fix: VehicleFix): Vehicle = copy(
        latitude = fix.latitude,
        longitude = fix.longitude,
        direction = fix.direction,
        speed = fix.speed,
    )
}

/** One entry of `transport/buses/locations`: `{ tid: [lat, lng, direction, speed] }`. */
data class VehicleFix(
    val latitude: Double,
    val longitude: Double,
    val direction: Double,
    val speed: Int,
) {
    companion object {
        fun orNull(values: List<Double>): VehicleFix? {
            if (values.size < 4) return null
            if (values[0] !in -90.0..90.0 || values[1] !in -180.0..180.0) return null
            return VehicleFix(values[0], values[1], values[2], values[3].toInt())
        }
    }
}

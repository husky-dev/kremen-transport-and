package com.krementransport.domain.model

import kotlin.math.cos
import kotlin.math.hypot

/** A WGS84 point as the API delivers it: a bare two-element `[lat, lng]` array, latitude first. */
data class Coordinate(val latitude: Double, val longitude: Double) {

    companion object {
        fun orNull(latitude: Double?, longitude: Double?): Coordinate? {
            if (latitude == null || longitude == null) return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return Coordinate(latitude, longitude)
        }
    }
}

/**
 * Metres between two points using an equirectangular approximation. Good to a fraction of a
 * percent across a city and far cheaper than haversine, which matters because the polyline
 * simplifier calls this tens of thousands of times per route.
 */
fun Coordinate.distanceTo(other: Coordinate): Double {
    val meanLatRad = Math.toRadians((latitude + other.latitude) / 2)
    val dx = Math.toRadians(other.longitude - longitude) * cos(meanLatRad)
    val dy = Math.toRadians(other.latitude - latitude)
    return hypot(dx, dy) * EarthRadiusMeters
}

private const val EarthRadiusMeters = 6_371_008.8

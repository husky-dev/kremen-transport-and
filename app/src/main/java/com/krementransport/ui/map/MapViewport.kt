package com.krementransport.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.krementransport.domain.model.Stop
import com.krementransport.domain.model.Vehicle
import kotlin.math.abs

/**
 * Decides what is actually handed to the map. Selecting every route would mean 38 polylines,
 * 433 stops and ~200 vehicles; culling to the visible rect is what keeps the marker count inside
 * what the Maps SDK draws smoothly.
 */
data class MapViewport(
    val bounds: LatLngBounds? = null,
    val center: LatLng = MapGeometry.CityCenter,
    val detail: MapDetail = MapDetail.Routes,
) {
    fun visibleStops(stops: List<Stop>, selectedRouteIds: Set<Int>): List<Stop> {
        if (!detail.showsStops || selectedRouteIds.isEmpty()) return emptyList()
        return stops.asSequence()
            .filter { it.routeIds.any(selectedRouteIds::contains) }
            .filter { contains(it.latitude, it.longitude) }
            .take(StopLimit)
            .toList()
    }

    fun visibleVehicles(vehicles: List<Vehicle>): List<Vehicle> {
        val inside = vehicles.filter { contains(it.latitude, it.longitude) }
        if (inside.size <= VehicleLimit) return inside
        // Over budget: keep the ones closest to where the user is actually looking.
        return inside.sortedBy { squaredDistanceToCenter(it.latitude, it.longitude) }.take(VehicleLimit)
    }

    private fun contains(latitude: Double, longitude: Double): Boolean {
        val bounds = bounds ?: return true
        return padded(bounds).contains(LatLng(latitude, longitude))
    }

    private fun squaredDistanceToCenter(latitude: Double, longitude: Double): Double {
        val dLat = latitude - center.latitude
        val dLng = longitude - center.longitude
        return dLat * dLat + dLng * dLng
    }

    /** Grown on every side so markers just off screen stay mounted and do not pop in on a pan. */
    private fun padded(bounds: LatLngBounds): LatLngBounds {
        val latPad = abs(bounds.northeast.latitude - bounds.southwest.latitude) * PadFraction
        val lngPad = abs(bounds.northeast.longitude - bounds.southwest.longitude) * PadFraction
        return LatLngBounds(
            LatLng(bounds.southwest.latitude - latPad, bounds.southwest.longitude - lngPad),
            LatLng(bounds.northeast.latitude + latPad, bounds.northeast.longitude + lngPad),
        )
    }

    private companion object {
        /** Hard ceilings — past these the map is unreadable anyway, so drawing more is waste. */
        const val VehicleLimit = 150
        const val StopLimit = 120
        const val PadFraction = 0.25
    }
}

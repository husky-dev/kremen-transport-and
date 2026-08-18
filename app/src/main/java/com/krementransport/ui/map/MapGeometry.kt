package com.krementransport.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

object MapGeometry {
    /** Kremenchuk, matching the centre the web, 1.4 and iOS apps all open on. */
    val CityCenter = LatLng(49.07041247214882, 33.42281959697266)

    /** The bounding box of the live route data, padded a little. */
    val CityBounds: LatLngBounds = LatLngBounds(
        LatLng(49.0884 - 0.11, 33.4779 - 0.14),
        LatLng(49.0884 + 0.11, 33.4779 + 0.14),
    )

    /** Roughly the iOS camera's 8 000 m eye height, and the web app's Google zoom. */
    const val DefaultZoom = 14f
    const val MinZoom = 10f
    const val MaxZoom = 18f

    /** Where "locate me" stops zooming in — close enough to read stops, not so close it disorients. */
    const val LocateMaxZoom = 16f
}

/**
 * How much detail the map is showing, derived from zoom. Thresholds carry hysteresis so a marker
 * style does not strobe when the camera sits right on a boundary.
 */
enum class MapDetail {
    City,
    Routes,
    Stops;

    val showsStops: Boolean get() = this == Stops
    val showsVehicleLabels: Boolean get() = this != City

    companion object {
        fun from(zoom: Float, previous: MapDetail): MapDetail {
            val stopsIn = 15.0f
            val stopsOut = 14.7f
            val routesIn = 13.8f
            val routesOut = 13.5f

            if (zoom >= if (previous == Stops) stopsOut else stopsIn) return Stops
            if (zoom >= if (previous == City) routesIn else routesOut) return Routes
            return City
        }
    }
}

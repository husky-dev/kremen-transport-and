package com.krementransport.ui.map

import com.google.android.gms.maps.model.LatLng
import com.krementransport.domain.model.Route
import com.krementransport.util.PolylineSimplifier

/**
 * Douglas–Peucker output, memoised per route per detail level. Without this, panning at city
 * zoom would re-simplify 38 paths of up to 664 points on every camera settle; with it each
 * (route, detail) pair is computed once for the life of the process.
 */
object RoutePathCache {
    private val cache = HashMap<String, List<LatLng>>()

    fun path(route: Route, detail: MapDetail): List<LatLng> {
        val key = "${route.rid}|${detail.ordinal}"
        cache[key]?.let { return it }

        val tolerance = if (detail == MapDetail.City) CityToleranceMeters else CloseToleranceMeters
        val simplified = PolylineSimplifier.simplify(route.path, tolerance)
            .map { LatLng(it.latitude, it.longitude) }
        cache[key] = simplified
        return simplified
    }

    fun clear() = cache.clear()

    private const val CityToleranceMeters = 60.0
    private const val CloseToleranceMeters = 12.0
}

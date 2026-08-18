package com.krementransport.domain.model

/**
 * One stop *as listed inside a route*. The same physical stop appears once per
 * (route, direction), which is why the map works with [Stop] instead.
 */
data class Station(
    val sid: Int,
    val rid: Int,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val sequenceNumber: Int,
    val directionForward: Boolean,
)

/**
 * A physical stop, deduplicated across routes by `sid`. In the live data `sid` maps 1:1 to a
 * coordinate (1994 station entries fold to 433 stops), so this fold is lossless.
 *
 * Note the API returns the same `sid` for *both* travel directions, so a stop carries no usable
 * direction of its own — arrivals are split on each prediction's `reverse` flag instead.
 */
data class Stop(
    val sid: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val routeIds: Set<Int>,
) {
    companion object {
        fun fold(routes: List<Route>): List<Stop> {
            val names = HashMap<Int, String>()
            val points = HashMap<Int, Coordinate>()
            val served = HashMap<Int, MutableSet<Int>>()

            for (route in routes) {
                for (station in route.stations) {
                    names[station.sid] = station.name.collapseSpaces()
                    points[station.sid] = Coordinate(station.latitude, station.longitude)
                    served.getOrPut(station.sid) { mutableSetOf() }.add(route.rid)
                }
            }

            return points.mapNotNull { (sid, point) ->
                val name = names[sid] ?: return@mapNotNull null
                Stop(sid, name, point.latitude, point.longitude, served[sid].orEmpty())
            }.sortedBy { it.sid }
        }
    }
}

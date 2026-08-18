package com.krementransport.util

import com.krementransport.domain.model.Coordinate
import com.krementransport.domain.model.distanceTo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Ramer–Douglas–Peucker. Selecting every route means 38 polylines of up to 664 points; dropping
 * the points the eye cannot resolve at the current zoom is what keeps the map layer cheap.
 * Results are memoised per route per detail level by `RoutePathCache`, so this runs rarely.
 */
object PolylineSimplifier {

    fun simplify(points: List<Coordinate>, toleranceMeters: Double): List<Coordinate> {
        if (points.size < 3 || toleranceMeters <= 0) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        simplifySegment(points, 0, points.lastIndex, toleranceMeters, keep)
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun simplifySegment(
        points: List<Coordinate>,
        first: Int,
        last: Int,
        tolerance: Double,
        keep: BooleanArray,
    ) {
        if (last <= first + 1) return

        var farthest = -1
        var maxDistance = 0.0
        for (i in first + 1 until last) {
            val distance = perpendicularDistance(points[i], points[first], points[last])
            if (distance > maxDistance) {
                maxDistance = distance
                farthest = i
            }
        }

        if (maxDistance <= tolerance || farthest < 0) return
        keep[farthest] = true
        simplifySegment(points, first, farthest, tolerance, keep)
        simplifySegment(points, farthest, last, tolerance, keep)
    }

    /**
     * Distance in metres from [point] to the segment [start]..[end], in a local planar frame.
     * Longitudes are scaled by cos(latitude) so a degree east is worth what it is on the ground.
     */
    private fun perpendicularDistance(point: Coordinate, start: Coordinate, end: Coordinate): Double {
        if (start == end) return point.distanceTo(start)

        val latScale = MetersPerDegreeLat
        val lngScale = MetersPerDegreeLat * cos(Math.toRadians(start.latitude))

        val px = (point.longitude - start.longitude) * lngScale
        val py = (point.latitude - start.latitude) * latScale
        val ex = (end.longitude - start.longitude) * lngScale
        val ey = (end.latitude - start.latitude) * latScale

        val lengthSquared = ex * ex + ey * ey
        if (lengthSquared == 0.0) return hypot(px, py)

        val t = ((px * ex + py * ey) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - t * ex, py - t * ey).let { abs(it) }
    }

    private const val MetersPerDegreeLat = 111_195.0
}

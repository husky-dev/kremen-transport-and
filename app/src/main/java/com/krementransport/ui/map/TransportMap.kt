package com.krementransport.ui.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.krementransport.R
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.RouteNumber
import com.krementransport.domain.model.Stop
import com.krementransport.domain.model.Vehicle
import com.krementransport.ui.map.marker.MarkerBitmaps
import com.krementransport.ui.theme.BrandBlue
import com.krementransport.util.parseHexColor

/**
 * The only file that knows how the map is drawn. It takes items, a selection and camera state;
 * if the Maps SDK ever has to be swapped out, nothing above this line changes.
 *
 * Three mechanisms keep a full selection — 38 polylines of up to 664 points, 433 stops and ~200
 * vehicles — bounded, and a change here must preserve all three:
 * [MapViewport] culls to the padded visible bounds and caps the counts, [MapDetail] gates stops
 * and vehicle labels by zoom with hysteresis, and [RoutePathCache] memoises simplified paths.
 */
@Composable
fun TransportMap(
    routes: List<Route>,
    stops: List<Stop>,
    vehicles: List<Vehicle>,
    routesById: Map<Int, Route>,
    detail: MapDetail,
    highlightedRouteId: Int?,
    isDarkTheme: Boolean,
    isMyLocationEnabled: Boolean,
    cameraPositionState: CameraPositionState,
    contentPadding: PaddingValues,
    onTargetSelected: (MapTarget) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val bitmaps = remember(density) { MarkerBitmaps(density) }
    val polylineWidth = remember(density) { 5f * density }

    val stopFill = if (isDarkTheme) Color(0xFF1A1B1F) else Color.White
    val stopRing = if (isDarkTheme) Color(0xFF8F9099) else Color(0xFF757780)

    // A JSON style rather than `mapColorScheme`: hiding points of interest and the operator's
    // own transit layer needs styling anyway, and the SDK ignores the colour scheme once a style
    // is applied — so both themes have to come from here or they would fight.
    val mapStyle = remember(isDarkTheme) {
        MapStyleOptions.loadRawResourceStyle(
            context,
            if (isDarkTheme) R.raw.map_style_dark else R.raw.map_style_light,
        )
    }

    val properties = remember(isMyLocationEnabled, mapStyle) {
        MapProperties(
            isMyLocationEnabled = isMyLocationEnabled,
            minZoomPreference = MapGeometry.MinZoom,
            maxZoomPreference = MapGeometry.MaxZoom,
            latLngBoundsForCameraTarget = MapGeometry.CityBounds,
            mapStyleOptions = mapStyle,
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        properties = properties,
        uiSettings = uiSettings,
        onMapClick = { onMapClick() },
    ) {
        for (route in routes) {
            key(route.rid) {
                val tint = parseHexColor(route.color) ?: BrandBlue
                Polyline(
                    points = RoutePathCache.path(route, detail),
                    color = tint.copy(alpha = polylineAlpha(route.rid, highlightedRouteId)),
                    width = polylineWidth,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = if (route.rid == highlightedRouteId) 2f else 1f,
                )
            }
        }

        for (stop in stops) {
            key(stop.sid) {
                Marker(
                    state = rememberUpdatedMarkerState(LatLng(stop.latitude, stop.longitude)),
                    icon = bitmaps.stop(stopFill.toArgb(), stopRing.toArgb(), selected = false),
                    anchor = MarkerCenter,
                    zIndex = 3f,
                    onClick = {
                        onTargetSelected(MapTarget.Stop(stop.sid))
                        true
                    },
                )
            }
        }

        for (vehicle in vehicles) {
            key(vehicle.tid) {
                val route = routesById[vehicle.rid]
                val tint = parseHexColor(route?.color) ?: BrandBlue
                Marker(
                    state = rememberUpdatedMarkerState(LatLng(vehicle.latitude, vehicle.longitude)),
                    icon = bitmaps.vehicle(
                        // The route's number and kind, never the vehicle's: a vehicle's `type`
                        // is derived server-side from a free-text name and is unreliable, and
                        // its `name` is a fleet label ("02 Рута BI6227IM"), not a route number.
                        badge = RouteNumber.badge(route?.number.orEmpty()),
                        kind = route?.type ?: vehicle.type,
                        tint = tint.toArgb(),
                        headingDegrees = vehicle.direction,
                        offline = vehicle.offline,
                        labelled = detail.showsVehicleLabels,
                    ),
                    anchor = MarkerCenter,
                    zIndex = 4f,
                    onClick = {
                        onTargetSelected(MapTarget.Vehicle(vehicle.tid))
                        true
                    },
                )
            }
        }
    }
}

/** Both marker sprites are drawn around their centre, not pinned by a tip. */
private val MarkerCenter = Offset(0.5f, 0.5f)

/**
 * A highlighted route reads at full strength while the rest dim rather than disappear — losing
 * them entirely would make "show route" feel like the map had been cleared.
 */
private fun polylineAlpha(rid: Int, highlightedRouteId: Int?): Float = when {
    highlightedRouteId == null -> 0.55f
    rid == highlightedRouteId -> 0.95f
    else -> 0.15f
}

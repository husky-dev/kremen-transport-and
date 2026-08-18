package com.krementransport.ui.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import com.krementransport.R
import com.krementransport.data.prefs.AppLocale
import com.krementransport.ui.routes.RoutePickerContent
import com.krementransport.ui.settings.SettingsContent
import com.krementransport.ui.station.StationContent
import com.krementransport.ui.station.StationViewModel
import com.krementransport.ui.vehicle.VehicleContent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * The one screen. A full-bleed map with floating chrome; everything else is a sheet on a phone
 * and, past [ExpandedWidthDp], a permanent pane beside the map.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    stationViewModel: StationViewModel,
    locationProvider: LocationProvider,
    isDarkTheme: Boolean,
    widthDp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val highlightedRouteId by viewModel.highlightedRouteId.collectAsStateWithLifecycle()
    val stationState by stationViewModel.state.collectAsStateWithLifecycle()

    val isExpanded = widthDp >= ExpandedWidthDp

    var isPickerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isLocationDeniedShown by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(locationProvider.hasPermission) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(MapGeometry.CityCenter, MapGeometry.DefaultZoom)
    }

    // Polling lives and dies with the resumed lifecycle: backgrounding the app cancels the scope,
    // which is the entire shutdown mechanism.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch { viewModel.runPolling() }
        onPauseOrDispose { job.cancel() }
    }

    // Culling is recomputed only once the camera settles. Doing it per frame would defeat it.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position }
            .debounce(120)
            .collectLatest { (isMoving, position) ->
                if (isMoving) return@collectLatest
                viewModel.onCameraSettled(
                    MapViewport(
                        bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds,
                        center = position.target,
                        detail = MapDetail.from(position.zoom, viewport.detail),
                    ),
                )
            }
    }

    // First launch with nothing chosen: open the picker rather than showing an empty map.
    LaunchedEffect(state.selection.isFirstLaunch, state.transport.sortedRoutes.isEmpty()) {
        if (state.selection.isFirstLaunch && state.selection.routeIds.isEmpty()) isPickerOpen = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasLocationPermission = granted.values.any { it }
        if (hasLocationPermission) {
            scope.launch { recenter(locationProvider, cameraPositionState) { isLocating = it } }
        } else {
            isLocationDeniedShown = true
        }
    }

    val onLocateTapped: () -> Unit = {
        if (locationProvider.hasPermission) {
            hasLocationPermission = true
            scope.launch { recenter(locationProvider, cameraPositionState) { isLocating = it } }
        } else {
            // Asked lazily, on the first tap — never at launch.
            permissionLauncher.launch(LocationProvider.Permissions)
        }
    }

    val stops = remember(viewport, state.transport.stops, state.selection.routeIds) {
        viewport.visibleStops(state.transport.stops, state.selection.routeIds)
    }
    val vehicles = remember(viewport, state.vehicles, state.selection) {
        viewport.visibleVehicles(state.liveVehicles())
    }

    val isOnline = remember(state.vehicles) {
        val last = state.vehicles.lastUpdate
        !state.vehicles.isFailing && last != null &&
            System.currentTimeMillis() - last <= StaleAfterMillis
    }

    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            TransportMap(
                routes = state.selectedRoutes,
                stops = stops,
                vehicles = vehicles,
                routesById = state.transport.routesById,
                detail = viewport.detail,
                highlightedRouteId = highlightedRouteId,
                isDarkTheme = isDarkTheme,
                isMyLocationEnabled = hasLocationPermission,
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(bottom = 88.dp),
                onTargetSelected = viewModel::select,
                onMapClick = {
                    viewModel.select(null)
                    viewModel.clearHighlight()
                },
                modifier = Modifier.fillMaxSize(),
            )

            MapChrome(
                loadState = state.loadState,
                hasRoutes = state.transport.sortedRoutes.isNotEmpty(),
                hasSelection = state.selection.routeIds.isNotEmpty(),
                onRetry = { viewModel.retryLoad() },
                selectedCount = state.selection.routeIds.size,
                isOnline = isOnline,
                isDarkTheme = isDarkTheme,
                isLocating = isLocating,
                showRoutesFab = !isExpanded,
                onSettings = { isSettingsOpen = true },
                onRoutes = { isPickerOpen = true },
                onLocate = onLocateTapped,
                onZoomIn = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) } },
                onZoomOut = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) } },
            )
        }

        // Past the expanded breakpoint the picker stops being a modal and becomes a pane, so the
        // map and the list are usable at the same time — which is the whole point of the space.
        if (isExpanded) {
            Surface(
                modifier = Modifier.width(360.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                RoutePickerContent(
                    routes = state.transport.sortedRoutes,
                    selectedRouteIds = state.selection.routeIds,
                    showOffline = state.selection.showOffline,
                    onToggleRoute = { viewModel.toggleRoute(it) },
                    onSelectAll = { viewModel.selectAllRoutes() },
                    onClear = { viewModel.clearRoutes() },
                    onSetShowOffline = { viewModel.setShowOffline(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 8.dp),
                )
            }
        }
    }

    if (isPickerOpen && !isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isPickerOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            RoutePickerContent(
                routes = state.transport.sortedRoutes,
                selectedRouteIds = state.selection.routeIds,
                showOffline = state.selection.showOffline,
                onToggleRoute = { viewModel.toggleRoute(it) },
                onSelectAll = { viewModel.selectAllRoutes() },
                onClear = { viewModel.clearRoutes() },
                onSetShowOffline = { viewModel.setShowOffline(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (isSettingsOpen) {
        ModalBottomSheet(onDismissRequest = { isSettingsOpen = false }) {
            SettingsContent(
                appearance = state.settings.appearance,
                language = state.settings.language,
                onAppearanceChange = { viewModel.setAppearance(it) },
                onLanguageChange = {
                    viewModel.setLanguage(it)
                    // Applied straight from the tap, not reactively — see AppLocale.
                    AppLocale.apply(it)
                },
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            )
        }
    }

    val selectedStop = (target as? MapTarget.Stop)?.let { state.stop(it.sid) }
    LaunchedEffect(selectedStop?.sid) {
        val sid = selectedStop?.sid
        if (sid == null) stationViewModel.close() else stationViewModel.open(sid)
    }

    if (selectedStop != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.select(null) }) {
            StationContent(
                stop = selectedStop,
                predictions = stationState.predictions,
                hasLoaded = stationState.hasLoaded,
                error = stationState.error,
                routesById = state.transport.routesById,
                selectedRouteIds = state.selection.routeIds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val selectedVehicle = (target as? MapTarget.Vehicle)?.let { state.vehicle(it.tid) }
    if (selectedVehicle != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.select(null) }) {
            VehicleContent(
                vehicle = selectedVehicle,
                route = state.route(selectedVehicle.rid),
                onShowRoute = { viewModel.highlightRoute(selectedVehicle.rid) },
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }

    if (isLocationDeniedShown) {
        AlertDialog(
            onDismissRequest = { isLocationDeniedShown = false },
            title = { Text(stringResource(R.string.location_denied_title)) },
            text = { Text(stringResource(R.string.location_denied_message)) },
            confirmButton = {
                TextButton(onClick = {
                    isLocationDeniedShown = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }) { Text(stringResource(R.string.location_denied_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { isLocationDeniedShown = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun MapChrome(
    loadState: com.krementransport.data.repo.TransportRepository.LoadState,
    hasRoutes: Boolean,
    hasSelection: Boolean,
    onRetry: () -> Unit,
    selectedCount: Int,
    isOnline: Boolean,
    isDarkTheme: Boolean,
    isLocating: Boolean,
    showRoutesFab: Boolean,
    onSettings: () -> Unit,
    onRoutes: () -> Unit,
    onLocate: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
    ) {
        SettingsButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopStart))

        MapStatus(
            loadState = loadState,
            hasRoutes = hasRoutes,
            hasSelection = hasSelection,
            onRetry = onRetry,
            modifier = Modifier.align(Alignment.Center),
        )

        ConnectionChip(
            isOnline = isOnline,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ZoomStepper(onZoomIn = onZoomIn, onZoomOut = onZoomOut)
            LocateButton(isBusy = isLocating, onClick = onLocate)
        }

        if (showRoutesFab) {
            RoutesFab(
                count = selectedCount,
                onClick = onRoutes,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

private suspend fun recenter(
    provider: LocationProvider,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    setBusy: (Boolean) -> Unit,
) {
    setBusy(true)
    try {
        val fix = provider.currentLocation() ?: return
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(fix.latitude, fix.longitude),
                // Zoom in to at least street level, but never back out if the user was closer.
                maxOf(cameraPositionState.position.zoom, MapGeometry.LocateMaxZoom),
            ),
        )
    } finally {
        setBusy(false)
    }
}

/** The Material breakpoint where a list stops being a modal and becomes a pane. */
private const val ExpandedWidthDp = 840

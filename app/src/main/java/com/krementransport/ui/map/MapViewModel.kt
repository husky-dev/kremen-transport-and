package com.krementransport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.krementransport.AppContainer
import com.krementransport.data.prefs.AppSettings
import com.krementransport.data.prefs.AppearancePreference
import com.krementransport.data.prefs.LanguagePreference
import com.krementransport.data.prefs.Selection
import com.krementransport.data.prefs.SelectionRepository
import com.krementransport.data.prefs.SettingsRepository
import com.krementransport.data.repo.TransportRepository
import com.krementransport.data.repo.VehicleRepository
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.Stop
import com.krementransport.domain.model.Vehicle
import com.krementransport.util.poll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Everything the map draws in one frame. The three sources are combined here rather than
 * collected separately in the composable so a 5-second position tick and an hourly route refresh
 * cannot recompose each other's layer independently and tear.
 */
data class MapUiState(
    val transport: TransportRepository.Snapshot = TransportRepository.Snapshot(),
    val loadState: TransportRepository.LoadState = TransportRepository.LoadState.Idle,
    val vehicles: VehicleRepository.Snapshot = VehicleRepository.Snapshot(),
    val selection: Selection = Selection(),
    val settings: AppSettings = AppSettings(),
) {
    val selectedRoutes: List<Route>
        get() = transport.sortedRoutes.filter { it.rid in selection.routeIds }

    fun route(rid: Int): Route? = transport.routesById[rid]

    fun stop(sid: Int): Stop? = transport.stopsById[sid]

    fun vehicle(tid: String): Vehicle? = vehicles.vehicles[tid]

    fun liveVehicles(): List<Vehicle> = vehicles.vehicles.values.filter {
        it.rid in selection.routeIds && (selection.showOffline || !it.offline)
    }
}

class MapViewModel(
    private val transport: TransportRepository,
    private val vehicles: VehicleRepository,
    private val selectionRepo: SelectionRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<MapUiState> = combine(
        transport.snapshot,
        transport.state,
        vehicles.snapshot,
        selectionRepo.selection,
        settingsRepo.settings,
    ) { snapshot, loadState, vehicleSnapshot, selection, settings ->
        MapUiState(snapshot, loadState, vehicleSnapshot, selection, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private val _viewport = MutableStateFlow(MapViewport())
    val viewport: StateFlow<MapViewport> = _viewport.asStateFlow()

    private val _target = MutableStateFlow<MapTarget?>(null)
    val target: StateFlow<MapTarget?> = _target.asStateFlow()

    private val _highlightedRouteId = MutableStateFlow<Int?>(null)
    val highlightedRouteId: StateFlow<Int?> = _highlightedRouteId.asStateFlow()

    init {
        viewModelScope.launch {
            transport.load()
            selectionRepo.reconcile(transport.snapshot.value.knownRouteIds)
        }
    }

    /**
     * Runs for as long as the caller's scope lives. The screen calls this inside
     * `repeatOnLifecycle(STARTED)`, so backgrounding the app cancels every poll — that
     * cancellation *is* the shutdown mechanism, there is nothing else to stop.
     */
    suspend fun runPolling(): Unit = coroutineScope {
        poll(every = PositionsInterval) { vehicles.loadPositions() }
        poll(every = RosterInterval) { vehicles.loadRoster(force = true) }
    }

    fun onCameraSettled(viewport: MapViewport) {
        _viewport.value = viewport
    }

    fun select(target: MapTarget?) {
        _target.value = target
    }

    fun toggleRoute(rid: Int) = viewModelScope.launch { selectionRepo.toggle(rid) }
    fun selectAllRoutes() = viewModelScope.launch {
        selectionRepo.replace(transport.snapshot.value.knownRouteIds)
    }

    fun clearRoutes() = viewModelScope.launch { selectionRepo.clear() }
    fun setShowOffline(value: Boolean) = viewModelScope.launch { selectionRepo.setShowOffline(value) }
    fun setAppearance(value: AppearancePreference) = viewModelScope.launch { settingsRepo.setAppearance(value) }
    fun setLanguage(value: LanguagePreference) = viewModelScope.launch { settingsRepo.setLanguage(value) }

    fun retryLoad() = viewModelScope.launch { transport.refresh() }

    /** "Show route" from the vehicle sheet: select it, then highlight it. */
    fun highlightRoute(rid: Int) {
        viewModelScope.launch {
            if (rid !in uiState.value.selection.routeIds) selectionRepo.toggle(rid)
            _highlightedRouteId.value = rid
            _target.value = null
        }
    }

    fun clearHighlight() {
        _highlightedRouteId.value = null
    }

    companion object {
        /** The positions endpoint is 14 KB and the backend refreshes it every 10 s. */
        private val PositionsInterval = 5.seconds

        /** The full roster is 78 KB; it only needs to catch vehicles that newly appeared. */
        private val RosterInterval = 60.seconds

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MapViewModel(
                    container.transport,
                    container.vehicles,
                    container.selection,
                    container.settings,
                ) as T
            }
    }
}

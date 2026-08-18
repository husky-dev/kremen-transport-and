package com.krementransport.data.repo

import android.util.Log
import com.krementransport.data.api.ApiClient
import com.krementransport.data.api.CachedPayload
import com.krementransport.data.cache.RouteCache
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.RouteNumber
import com.krementransport.domain.model.Stop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Slow-moving reference data: routes, their paths and the deduplicated stops. Deliberately kept
 * apart from [VehicleRepository] so a 5-second position tick never invalidates the polyline
 * layer or the route picker.
 */
class TransportRepository(
    private val api: ApiClient,
    private val cache: RouteCache,
) {
    sealed interface LoadState {
        data object Idle : LoadState
        data object Loading : LoadState
        data object Loaded : LoadState
        data class Failed(val message: String) : LoadState
    }

    data class Snapshot(
        val routesById: Map<Int, Route> = emptyMap(),
        val sortedRoutes: List<Route> = emptyList(),
        val stopsById: Map<Int, Stop> = emptyMap(),
        val stops: List<Stop> = emptyList(),
    ) {
        val knownRouteIds: Set<Int> get() = routesById.keys
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state.asStateFlow()

    private val loadMutex = Mutex()

    /** Cache first so the map paints immediately, then refresh over the network. */
    suspend fun load() = loadMutex.withLock {
        if (_snapshot.value.routesById.isEmpty()) _state.value = LoadState.Loading

        val cached = cache.load()
        if (cached != null) {
            val routes = runCatching { api.decodeRoutes(cached.payload.bytes) }.getOrNull()
            if (routes != null && routes.isNotEmpty()) {
                apply(routes)
                if (cached.ageMillis < MaxCacheAgeMillis) return@withLock
            }
        }

        refresh(cached?.payload)
    }

    suspend fun refresh(cached: CachedPayload? = null) {
        try {
            val payload = api.routesIfChanged(cached)
            if (payload == null) {
                cache.touch()
                _state.value = LoadState.Loaded
                return
            }
            apply(api.decodeRoutes(payload.bytes))
            cache.store(payload)
        } catch (error: Exception) {
            Log.e(Tag, "routes refresh failed", error)
            if (_snapshot.value.routesById.isEmpty()) {
                _state.value = LoadState.Failed(error.message ?: "unknown")
            }
        }
    }

    private fun apply(routes: List<Route>) {
        // associateBy rather than toMap: the backend has never repeated a rid, but last-wins is
        // a survivable outcome and a crash on launch is not.
        val byId = routes.associateBy { it.rid }
        val sorted = routes.sortedWith(
            compareBy({ RouteNumber.sortKey(it.number).first }, { RouteNumber.sortKey(it.number).second }, { it.rid }),
        )
        val folded = Stop.fold(routes)
        _snapshot.value = Snapshot(
            routesById = byId,
            sortedRoutes = sorted,
            stopsById = folded.associateBy { it.sid },
            stops = folded,
        )
        _state.value = LoadState.Loaded
    }

    private companion object {
        const val Tag = "TransportRepository"

        /** The backend only rebuilds routes hourly; refetching sooner is pure waste. */
        const val MaxCacheAgeMillis = 60L * 60L * 1000L
    }
}

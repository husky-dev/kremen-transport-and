package com.krementransport.data.repo

import android.util.Log
import com.krementransport.data.api.ApiClient
import com.krementransport.domain.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Live vehicle positions. `transport/buses` supplies the metadata (name, route, offline) and
 * `transport/buses/locations` — 14 KB against 78 KB — supplies the movement.
 */
class VehicleRepository(private val api: ApiClient) {

    data class Snapshot(
        val vehicles: Map<String, Vehicle> = emptyMap(),
        val lastUpdate: Long? = null,
        val isFailing: Boolean = false,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var lastRosterFetch = 0L
    private val rosterMutex = Mutex()

    fun vehiclesOn(routeIds: Set<Int>, includeOffline: Boolean): List<Vehicle> =
        _snapshot.value.vehicles.values.filter { it.rid in routeIds && (includeOffline || !it.offline) }

    suspend fun loadRoster(force: Boolean = false) = rosterMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - lastRosterFetch < RosterCooldownMillis) return@withLock
        try {
            val list = api.buses()
            lastRosterFetch = now
            val known = _snapshot.value.vehicles
            // The roster's own coordinates can lag the last positions tick, so keep ours.
            val merged = list.associate { fresh ->
                val existing = known[fresh.tid]
                fresh.tid to if (existing == null) fresh else fresh.copy(
                    latitude = existing.latitude,
                    longitude = existing.longitude,
                    direction = existing.direction,
                    speed = existing.speed,
                )
            }
            _snapshot.value = Snapshot(merged, now, isFailing = false)
        } catch (error: Exception) {
            Log.e(Tag, "roster fetch failed", error)
            _snapshot.value = _snapshot.value.copy(isFailing = true)
        }
    }

    suspend fun loadPositions() {
        try {
            val fixes = api.locations()
            val current = _snapshot.value.vehicles
            var sawUnknown = false
            val updated = HashMap<String, Vehicle>(current)
            for ((tid, fix) in fixes) {
                val vehicle = current[tid]
                if (vehicle == null) sawUnknown = true else updated[tid] = vehicle.apply(fix)
            }
            _snapshot.value = Snapshot(updated, System.currentTimeMillis(), isFailing = false)
            // The locations endpoint only *moves* vehicles it already knows about. One that
            // appears mid-session shows up as an unknown tid, and only a roster fetch can name it.
            if (sawUnknown) loadRoster()
        } catch (error: Exception) {
            Log.e(Tag, "positions fetch failed", error)
            _snapshot.value = _snapshot.value.copy(isFailing = true)
        }
    }

    private companion object {
        const val Tag = "VehicleRepository"
        const val RosterCooldownMillis = 30_000L
    }
}

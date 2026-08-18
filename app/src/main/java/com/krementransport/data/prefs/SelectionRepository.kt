package com.krementransport.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Selection(
    val routeIds: Set<Int> = DefaultRouteIds,
    val showOffline: Boolean = false,
    /** Has the user ever opened the picker? Drives the first-run prompt. */
    val isFirstLaunch: Boolean = true,
)

/**
 * Routes «3-б», «15», «17», «15-б» — four busy lines, so a first launch is never an empty map.
 * These are ids in the *current* numbering; the 1.4 app's defaults are meaningless here.
 */
val DefaultRouteIds: Set<Int> = setOf(16, 7, 2, 10)

/**
 * Which routes the user wants on the map. Persisted immediately on every change — the picker
 * has no Cancel, so nothing is ever lost by dismissing it.
 */
class SelectionRepository(private val store: DataStore<Preferences>) {

    val selection: Flow<Selection> = store.data.map { prefs ->
        val stored = prefs[PreferenceKeys.RouteIds]
        Selection(
            routeIds = stored?.mapNotNull(String::toIntOrNull)?.toSet() ?: DefaultRouteIds,
            showOffline = prefs[PreferenceKeys.ShowOffline] ?: false,
            isFirstLaunch = stored == null,
        )
    }

    suspend fun toggle(rid: Int) {
        store.edit { prefs ->
            val current = prefs[PreferenceKeys.RouteIds]?.mapNotNull(String::toIntOrNull)?.toSet()
                ?: DefaultRouteIds
            prefs[PreferenceKeys.RouteIds] =
                (if (rid in current) current - rid else current + rid).mapTo(mutableSetOf(), Int::toString)
        }
    }

    suspend fun replace(ids: Set<Int>) {
        store.edit { it[PreferenceKeys.RouteIds] = ids.mapTo(mutableSetOf(), Int::toString) }
    }

    suspend fun clear() = replace(emptySet())

    suspend fun setShowOffline(value: Boolean) {
        store.edit { it[PreferenceKeys.ShowOffline] = value }
    }

    /**
     * Drops ids that no longer exist upstream — but only once real routes have loaded, or a
     * slow first fetch would wipe the user's selection.
     */
    suspend fun reconcile(known: Set<Int>) {
        if (known.isEmpty()) return
        val current = selection.first()
        if (current.isFirstLaunch) return
        val filtered = current.routeIds intersect known
        if (filtered != current.routeIds) replace(filtered)
    }
}

package com.krementransport.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferenceKeys {
    val Appearance = stringPreferencesKey("settings.appearance.v1")
    val Language = stringPreferencesKey("settings.language.v1")

    /** `v2` because the backend renumbered every route; ids saved by the 1.4 app are meaningless. */
    val RouteIds = stringSetPreferencesKey("selection.routeIDs.v2")
    val ShowOffline = booleanPreferencesKey("selection.showOffline.v1")
}

suspend fun DataStore<Preferences>.put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
    edit(block)
}

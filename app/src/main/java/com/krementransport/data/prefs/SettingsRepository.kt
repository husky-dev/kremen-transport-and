package com.krementransport.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Light / dark / follow-the-system. `System` is the default because every colour in the app is
 * semantic already — the override exists for people whose device-wide choice is not what they
 * want on a map. If the preference is ever unreadable the app falls back to [Dark], which is the
 * safer look for a map at night.
 */
enum class AppearancePreference {
    System,
    Light,
    Dark;

    companion object {
        fun from(raw: String?): AppearancePreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: System
    }
}

/**
 * Unlike iOS, Android lets an app set its own locale at runtime, so this is a real preference
 * rather than a deep link into system settings.
 */
enum class LanguagePreference(val tag: String?) {
    System(null),
    Ukrainian("uk"),
    English("en");

    companion object {
        fun from(raw: String?): LanguagePreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: System
    }
}

data class AppSettings(
    val appearance: AppearancePreference = AppearancePreference.System,
    val language: LanguagePreference = LanguagePreference.System,
)

/** Written through immediately on every change — the settings sheet has no "apply" step. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            appearance = AppearancePreference.from(prefs[PreferenceKeys.Appearance]),
            language = LanguagePreference.from(prefs[PreferenceKeys.Language]),
        )
    }

    suspend fun setAppearance(value: AppearancePreference) {
        store.edit { it[PreferenceKeys.Appearance] = value.name }
    }

    suspend fun setLanguage(value: LanguagePreference) {
        store.edit { it[PreferenceKeys.Language] = value.name }
    }
}

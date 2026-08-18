package com.krementransport.data.prefs

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the stored language to the process.
 *
 * This must **never** be driven reactively from composition. `setApplicationLocales` recreates
 * the activity, so a `LaunchedEffect` keyed on the preference re-fires on the recreated activity
 * and, if the platform has not persisted the value yet, recreates it again — an endless loop that
 * also stops the map from ever finishing its first draw.
 *
 * It also cannot run from `Application.onCreate` — AppCompat is not initialised yet there and
 * the call is silently dropped. So there are exactly two call sites, both one-shot:
 * [applyOnce], guarded per process from the activity, and the moment the user picks a language.
 */
object AppLocale {

    private var applied = false

    /**
     * First activity of the process only. The guard is what breaks the recreation cycle:
     * `setApplicationLocales` restarts the activity, and the restarted one must not re-apply.
     */
    fun applyOnce(preference: LanguagePreference) {
        if (applied) return
        applied = true
        apply(preference)
    }

    fun apply(preference: LanguagePreference) {
        applied = true
        val tag = preference.tag
        val desired = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        if (AppCompatDelegate.getApplicationLocales() != desired) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}

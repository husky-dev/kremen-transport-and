package com.krementransport

import android.content.Context
import com.krementransport.data.api.ApiClient
import com.krementransport.data.cache.RouteCache
import com.krementransport.data.prefs.SelectionRepository
import com.krementransport.data.prefs.SettingsRepository
import com.krementransport.data.prefs.preferencesStore
import com.krementransport.data.repo.PredictionRepository
import com.krementransport.data.repo.TransportRepository
import com.krementransport.data.repo.VehicleRepository

/**
 * Hand-rolled DI. A dependency-injection framework would earn its keep across feature modules;
 * this app is one screen and four singletons, so a container avoids the annotation processor
 * entirely.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val api: ApiClient by lazy { ApiClient() }
    val transport: TransportRepository by lazy { TransportRepository(api, RouteCache(appContext)) }
    val vehicles: VehicleRepository by lazy { VehicleRepository(api) }
    val predictions: PredictionRepository by lazy { PredictionRepository(api) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext.preferencesStore) }
    val selection: SelectionRepository by lazy { SelectionRepository(appContext.preferencesStore) }
}

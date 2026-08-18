package com.krementransport.screenshot

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.krementransport.AppContainer
import com.krementransport.data.prefs.AppearancePreference
import com.krementransport.data.prefs.AppLocale
import com.krementransport.data.prefs.LanguagePreference
import com.krementransport.data.prefs.PreferenceKeys
import com.krementransport.data.prefs.preferencesStore
import com.krementransport.ui.map.LocationProvider
import com.krementransport.ui.map.MapGeometry
import com.krementransport.ui.map.MapScreen
import com.krementransport.ui.map.MapTarget
import com.krementransport.ui.map.MapViewModel
import com.krementransport.ui.station.StationViewModel
import com.krementransport.ui.theme.KremenTransportTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * The entry point `scripts/screenshots.sh` launches to produce the Play listing images.
 *
 * It is deliberately *not* the launcher activity and lives only in the debug source set: the
 * normal debug app still starts `MainActivity` against the live API. Everything a screenshot needs
 * to be reproducible is forced here rather than tapped through the UI, because a driver that taps
 * coordinates breaks the moment a control moves:
 *
 *  - the API comes from [FixtureApi], so vehicles and arrivals never move between runs
 *  - the camera is passed in, because `MapGeometry.DefaultZoom` (14f) is below the 15f that
 *    `MapDetail.from` needs before it will draw stops at all
 *  - the sheet is opened by setting view-model state, not by tapping a marker
 *
 * Preferences are seeded *before* `super.onCreate`, so `AppLocale.applyOnce` sees the final
 * language on its first pass. Applying it later would recreate the activity mid-capture — the
 * recreation loop CLAUDE.md warns about.
 */
class ScreenshotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val screen = intent.getStringExtra("screen") ?: "map"
        val language = when (intent.getStringExtra("lang")) {
            "uk" -> LanguagePreference.Ukrainian
            "en" -> LanguagePreference.English
            else -> LanguagePreference.System
        }
        val routeIds = intent.getStringExtra("routes")
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: DefaultScreenshotRoutes
        val sid = intent.getIntExtra("sid", DefaultStopId)
        val zoom = intent.getFloatExtra("zoom", DefaultScreenshotZoom)
        val latitude = intent.getDoubleExtra("lat", MapGeometry.CityCenter.latitude)
        val longitude = intent.getDoubleExtra("lng", MapGeometry.CityCenter.longitude)

        runBlocking {
            applicationContext.preferencesStore.edit { prefs ->
                prefs[PreferenceKeys.Appearance] = AppearancePreference.Light.name
                prefs[PreferenceKeys.Language] = language.name
                // Writing this also clears `isFirstLaunch`, which is what stops the picker from
                // auto-opening over the map shot.
                prefs[PreferenceKeys.RouteIds] = routeIds.mapTo(mutableSetOf(), Int::toString)
                prefs[PreferenceKeys.ShowOffline] = false
            }
        }
        super.onCreate(savedInstanceState)
        // After super.onCreate, exactly as MainActivity does it: AppCompat's delegate does not
        // exist before that and the call is silently dropped. It recreates the activity once;
        // AppLocale's own guard is what stops that becoming a loop.
        AppLocale.applyOnce(language)
        enableEdgeToEdge()

        val container = AppContainer(this, FixtureApi.apiClient(assets))
        val locationProvider = LocationProvider(this)

        setContent {
            KremenTransportTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val mapViewModel: MapViewModel =
                        viewModel(factory = MapViewModel.factory(container))
                    val stationViewModel: StationViewModel =
                        viewModel(factory = StationViewModel.factory(container))

                    val state by mapViewModel.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(screen) {
                        if (screen == "stop") mapViewModel.select(MapTarget.Stop(sid))
                    }

                    // The driver waits for this line. Data first, then a pause for the Maps SDK
                    // to finish fetching tiles — it exposes no "drawn" callback to wait on.
                    LaunchedEffect(state.transport.sortedRoutes.size, state.vehicles.vehicles.size) {
                        if (state.transport.sortedRoutes.isEmpty()) return@LaunchedEffect
                        if (state.vehicles.vehicles.isEmpty()) return@LaunchedEffect
                        delay(TileSettleMillis)
                        Log.i(Tag, ReadyMarker)
                    }

                    MapScreen(
                        viewModel = mapViewModel,
                        stationViewModel = stationViewModel,
                        locationProvider = locationProvider,
                        isDarkTheme = false,
                        widthDp = with(LocalDensity.current) {
                            LocalWindowInfo.current.containerSize.width.toDp().value.toInt()
                        },
                        initialCamera = CameraPosition.fromLatLngZoom(
                            LatLng(latitude, longitude),
                            zoom,
                        ),
                        initialPickerOpen = screen == "routes",
                    )
                }
            }
        }
    }

    private companion object {
        const val Tag = "Screenshot"
        const val ReadyMarker = "SCREENSHOT_READY"

        /** Long enough for map tiles at the sizes the driver uses; verified by eye, not guessed. */
        const val TileSettleMillis = 3_500L

        /**
         * Just above `MapDetail`'s 15f threshold, so stops and vehicle labels are both drawn — at
         * the app's own default of 14f the map shows bare polylines. Higher than this and the
         * frame empties out; this is the widest view that still renders stops.
         */
        const val DefaultScreenshotZoom = 15.1f

        /** Four busy central lines: a legible map rather than 200 markers. */
        val DefaultScreenshotRoutes = setOf(16, 7, 2, 10)

        /** Overridden per run; the driver picks a central stop served by several of the above. */
        const val DefaultStopId = 305
    }
}

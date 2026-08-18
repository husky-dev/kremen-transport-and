package com.krementransport

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krementransport.data.prefs.AppLocale
import com.krementransport.data.prefs.AppSettings
import com.krementransport.ui.map.LocationProvider
import com.krementransport.ui.map.MapScreen
import com.krementransport.ui.map.MapViewModel
import com.krementransport.ui.station.StationViewModel
import com.krementransport.ui.theme.KremenTransportTheme
import com.krementransport.ui.theme.isDark
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * `AppCompatActivity`, not `ComponentActivity`: the per-app language API is native only from
 * API 33, and AppCompat is what backports it (together with the `autoStoreLocales` service in
 * the manifest) down to this app's minSdk.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TransportApp).container

        // Read synchronously and apply before the first frame: a locale applied later would
        // repaint every string in front of the user. One small DataStore file, behind the splash.
        AppLocale.applyOnce(runBlocking { container.settings.settings.first().language })
        val settings: StateFlow<AppSettings> = container.settings.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppSettings())
        val locationProvider = LocationProvider(this)

        setContent {
            val appSettings by settings.collectAsStateWithLifecycle()
            val darkTheme = appSettings.appearance.isDark()

            KremenTransportTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val mapViewModel: MapViewModel =
                        viewModel(factory = MapViewModel.factory(container))
                    val stationViewModel: StationViewModel =
                        viewModel(factory = StationViewModel.factory(container))

                    MapScreen(
                        viewModel = mapViewModel,
                        stationViewModel = stationViewModel,
                        locationProvider = locationProvider,
                        isDarkTheme = darkTheme,
                        widthDp = with(LocalDensity.current) {
                            LocalWindowInfo.current.containerSize.width.toDp().value.toInt()
                        },
                    )
                }
            }
        }
    }
}

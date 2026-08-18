package com.krementransport.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.krementransport.R
import java.util.Locale
import kotlin.math.ceil

/**
 * The numeric half of formatting, kept free of `Context` so it is unit-testable. The `@Composable`
 * wrappers below turn a bucket into localised text.
 */
sealed interface EtaLabel {
    data object Now : EtaLabel
    data class Seconds(val value: Int) : EtaLabel
    data class Minutes(val value: Int) : EtaLabel
}

sealed interface DistanceLabel {
    data class Meters(val value: Int) : DistanceLabel
    data class Kilometers(val value: String) : DistanceLabel
}

/**
 * Minutes round **up**: a bus 61 seconds out is "2 хв", never "1 хв". Rounding down reads as a
 * bus you can still catch when you cannot.
 */
fun etaLabel(seconds: Int): EtaLabel = when {
    seconds <= 5 -> EtaLabel.Now
    seconds < 60 -> EtaLabel.Seconds(seconds)
    else -> EtaLabel.Minutes(ceil(seconds / 60.0).toInt())
}

fun distanceLabel(meters: Int): DistanceLabel = when {
    meters < 1000 -> DistanceLabel.Meters(meters)
    else -> DistanceLabel.Kilometers(String.format(Locale.US, "%.1f", meters / 1000.0))
}

@Composable
fun etaText(seconds: Int): String = when (val label = etaLabel(seconds)) {
    EtaLabel.Now -> stringResource(R.string.eta_now)
    is EtaLabel.Seconds -> stringResource(R.string.eta_seconds, label.value)
    is EtaLabel.Minutes -> stringResource(R.string.eta_minutes, label.value)
}

@Composable
fun distanceText(meters: Int): String = when (val label = distanceLabel(meters)) {
    is DistanceLabel.Meters -> stringResource(R.string.unit_meters, label.value)
    is DistanceLabel.Kilometers -> stringResource(R.string.unit_km, label.value)
}

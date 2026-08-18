package com.krementransport.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krementransport.R
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.Vehicle
import com.krementransport.domain.model.collapseSpaces
import com.krementransport.ui.common.BadgeSize
import com.krementransport.ui.common.RouteBadge
import com.krementransport.ui.theme.BrandBlue
import com.krementransport.util.parseHexColor
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** One vehicle: what it is, how fast, which way, and a way to see its whole route. */
@Composable
fun VehicleContent(
    vehicle: Vehicle,
    route: Route?,
    onShowRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RouteBadge(
                number = route?.number.orEmpty(),
                kind = route?.type ?: vehicle.type,
                tint = parseHexColor(route?.color) ?: BrandBlue,
                size = BadgeSize.Large,
                isMuted = vehicle.offline,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = route?.name?.collapseSpaces() ?: stringResource(R.string.vehicle_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = vehicle.name.collapseSpaces(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val speed = vehicle.knownSpeed
            if (speed != null) {
                DetailRow(stringResource(R.string.vehicle_speed), stringResource(R.string.unit_speed, speed))
            }
            DetailRow(
                label = stringResource(R.string.vehicle_heading),
                value = stringArrayResource(R.array.compass_points)[compassIndex(vehicle.direction)],
            )
            vehicle.updatedAt?.let {
                DetailRow(stringResource(R.string.vehicle_last_seen), agoText(it))
            }
            if (vehicle.offline) {
                Text(
                    text = stringResource(R.string.vehicle_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (route != null) {
            FilledTonalButton(onClick = onShowRoute, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Timeline, contentDescription = null)
                Text(
                    text = stringResource(R.string.vehicle_show_route),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A heading in degrees means nothing to a passenger; a compass point does. */
private fun compassIndex(degrees: Double): Int {
    val normalised = ((degrees % 360.0) + 360.0) % 360.0
    return ((normalised / 45.0).roundToInt()) % 8
}

@Composable
private fun agoText(instant: Instant): String {
    val seconds = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0).toInt()
    return if (seconds < 60) {
        stringResource(R.string.eta_seconds, seconds)
    } else {
        stringResource(R.string.eta_minutes, seconds / 60)
    }
}

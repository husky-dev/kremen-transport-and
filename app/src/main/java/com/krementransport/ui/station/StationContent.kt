package com.krementransport.ui.station

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krementransport.R
import com.krementransport.domain.model.Prediction
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.Stop
import com.krementransport.ui.common.BadgeSize
import com.krementransport.ui.common.RouteBadge
import com.krementransport.ui.theme.BrandBlue
import com.krementransport.util.distanceText
import com.krementransport.util.etaText
import com.krementransport.util.parseHexColor

/**
 * Arrivals at one stop.
 *
 * The API returns the same `sid` for both travel directions, so the stop itself carries no
 * usable direction — the rows are grouped on each *prediction's* `reverse` flag instead. The web
 * app filters on the station's `directionForward`, which silently drops about half the real
 * arrivals here.
 */
@Composable
fun StationContent(
    stop: Stop,
    predictions: List<Prediction>,
    hasLoaded: Boolean,
    error: String?,
    routesById: Map<Int, Route>,
    selectedRouteIds: Set<Int>,
    modifier: Modifier = Modifier,
) {
    var showAll by remember { mutableStateOf(false) }

    val visible = remember(predictions, showAll, selectedRouteIds) {
        if (showAll) predictions else predictions.filter { it.rid in selectedRouteIds }
    }
    val groups = remember(visible) {
        listOf(false, true).mapNotNull { reverse ->
            visible.filter { it.reverse == reverse }.takeIf { it.isNotEmpty() }?.let { reverse to it }
        }
    }

    Column(modifier = modifier) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.station_routes_count,
                    stop.routeIds.size,
                    stop.routeIds.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            SegmentedButton(
                selected = !showAll,
                onClick = { showAll = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.station_filter_selected)) }
            SegmentedButton(
                selected = showAll,
                onClick = { showAll = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.station_filter_all)) }
        }

        when {
            error != null && !hasLoaded -> Message(
                title = stringResource(R.string.station_error_title),
                body = stringResource(R.string.error_load_message),
            )

            hasLoaded && visible.isEmpty() -> Message(
                title = stringResource(R.string.station_empty_title),
                body = stringResource(R.string.station_empty_message),
            )

            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                for ((reverse, rows) in groups) {
                    item(key = "header-$reverse") {
                        Text(
                            text = stringResource(
                                if (reverse) {
                                    R.string.station_direction_reverse
                                } else {
                                    R.string.station_direction_forward
                                },
                            ),
                            modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(rows.size, key = { rows[it].id }) { index ->
                        ArrivalRow(rows[index], routesById[rows[index].rid])
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalRow(prediction: Prediction, route: Route?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            RouteBadge(
                number = route?.number.orEmpty(),
                kind = route?.type ?: com.krementransport.domain.model.TransitKind.Bus,
                tint = parseHexColor(route?.color) ?: BrandBlue,
                size = BadgeSize.Regular,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = route?.destination(prediction.reverse).orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = distanceText(prediction.distance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = etaText(prediction.seconds),
            style = MaterialTheme.typography.titleMedium,
            // Anything inside a minute is the one you can still run for.
            color = if (prediction.seconds <= 60) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

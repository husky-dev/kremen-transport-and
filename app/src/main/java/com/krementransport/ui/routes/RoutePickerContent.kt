package com.krementransport.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.RouteNumber
import com.krementransport.domain.model.TransitKind
import com.krementransport.domain.model.collapseSpaces
import com.krementransport.ui.common.BadgeSize
import com.krementransport.ui.common.RouteBadge
import com.krementransport.ui.theme.BrandBlue
import com.krementransport.util.parseHexColor

private enum class RouteFilter(val kind: TransitKind?) {
    All(null),
    Bus(TransitKind.Bus),
    Trolleybus(TransitKind.Trolleybus),
}

/**
 * Choosing what to watch. Every tap writes through to storage immediately — there is no Cancel,
 * so nothing is ever lost by dismissing the sheet.
 *
 * Extracted from any particular container so the same content can be a modal bottom sheet on a
 * phone and a permanent side pane on a tablet.
 */
@Composable
fun RoutePickerContent(
    routes: List<Route>,
    selectedRouteIds: Set<Int>,
    showOffline: Boolean,
    onToggleRoute: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onSetShowOffline: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RouteFilter.All) }

    val filtered = remember(routes, query, filter) {
        routes.filter { route ->
            (filter.kind == null || route.type == filter.kind) &&
                (
                    query.isBlank() ||
                        RouteNumber.matches(route.number, query) ||
                        route.name.contains(query.trim(), ignoreCase = true)
                    )
        }
    }

    val sections = remember(filtered, selectedRouteIds, query) {
        buildList {
            val selected = filtered.filter { it.rid in selectedRouteIds }
            if (selected.isNotEmpty() && query.isBlank()) add(R.string.routes_section_selected to selected)
            filtered.filter { it.type == TransitKind.Bus }
                .takeIf { it.isNotEmpty() }?.let { add(R.string.routes_section_bus to it) }
            filtered.filter { it.type == TransitKind.Trolleybus }
                .takeIf { it.isNotEmpty() }?.let { add(R.string.routes_section_trolley to it) }
        }
    }

    Column(modifier = modifier) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.routes_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.routes_selected_count,
                    selectedRouteIds.size,
                    selectedRouteIds.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.routes_search_prompt)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) }
                }
            },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in RouteFilter.entries) {
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.routes_select_all)) }
            TextButton(onClick = onClear) { Text(stringResource(R.string.routes_clear)) }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.routes_show_offline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = showOffline,
                onCheckedChange = onSetShowOffline,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (filtered.isEmpty()) {
            EmptyRoutes(Modifier.fillMaxWidth().padding(32.dp))
            return@Column
        }

        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            for ((titleRes, group) in sections) {
                item(key = "header-$titleRes") {
                    Text(
                        text = stringResource(titleRes),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(group, key = { "$titleRes-${it.rid}" }) { route ->
                    RouteRow(
                        route = route,
                        isSelected = route.rid in selectedRouteIds,
                        onClick = { onToggleRoute(route.rid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteRow(route: Route, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            RouteBadge(
                number = route.number,
                kind = route.type,
                tint = parseHexColor(route.color) ?: BrandBlue,
                size = BadgeSize.Regular,
                isMuted = route.active == 0,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = route.name.collapseSpaces(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (route.active == 0) {
                    stringResource(R.string.routes_not_running)
                } else {
                    pluralStringResource(R.plurals.routes_on_route, route.active, route.active)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
    }
}

@Composable
private fun EmptyRoutes(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.routes_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.routes_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun RouteFilter.labelRes(): Int = when (this) {
    RouteFilter.All -> R.string.routes_filter_all
    RouteFilter.Bus -> R.string.routes_filter_bus
    RouteFilter.Trolleybus -> R.string.routes_filter_trolley
}

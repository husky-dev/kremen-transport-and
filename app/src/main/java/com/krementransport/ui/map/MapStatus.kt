package com.krementransport.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krementransport.R
import com.krementransport.data.repo.TransportRepository

/**
 * What the map says when it has nothing to draw.
 *
 * Without this the three failure modes are indistinguishable from each other and from a working
 * map in a quiet part of town: a cold launch with no cache, a failed routes fetch, and a
 * selection the user has emptied all render as bare streets.
 */
@Composable
fun MapStatus(
    loadState: TransportRepository.LoadState,
    hasRoutes: Boolean,
    hasSelection: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loadState is TransportRepository.LoadState.Loading && !hasRoutes ->
            StatusCard(modifier) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.map_loading_routes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

        loadState is TransportRepository.LoadState.Failed && !hasRoutes ->
            StatusCard(modifier) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.error_load_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.error_load_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }

        hasRoutes && !hasSelection ->
            StatusCard(modifier) {
                Text(
                    text = stringResource(R.string.map_no_routes_selected),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
    }
}

@Composable
private fun StatusCard(modifier: Modifier, content: @Composable () -> Unit) {
    MapSurface(modifier = modifier.widthIn(max = 320.dp), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

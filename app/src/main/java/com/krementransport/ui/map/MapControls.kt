package com.krementransport.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.krementransport.R
import com.krementransport.ui.theme.OfflineRed
import com.krementransport.ui.theme.OfflineRedDark
import com.krementransport.ui.theme.OnlineGreen
import com.krementransport.ui.theme.OnlineGreenDark

/**
 * Map chrome, laid out to Android convention rather than the iOS app's: a settings affordance
 * top-left, liveness top-right, the zoom pair against the trailing edge with my-location as its
 * own FAB below it, and the primary action as an extended FAB along the bottom.
 */

/** A floating control surface, consistent across every piece of chrome on the map. */
@Composable
fun MapSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        content = { content() },
    )
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    MapSurface(modifier = modifier) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.map_control_settings),
            )
        }
    }
}

/**
 * Zoom reads as one control, not two more buttons — hence a single surface split by a divider,
 * which is also how the platform's own map surfaces group a stepper.
 */
@Composable
fun ZoomStepper(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapSurface(modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onZoomIn, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, stringResource(R.string.map_control_zoom_in))
            }
            HorizontalDivider(
                modifier = Modifier.width(28.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            IconButton(onClick = onZoomOut, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, stringResource(R.string.map_control_zoom_out))
            }
        }
    }
}

/**
 * My-location is its own FAB rather than a third button in the zoom stack: it is an action, not
 * a camera adjustment, and Android users expect to find it as a floating button.
 */
@Composable
fun LocateButton(
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A pulse while we are waiting on the first fix: the button has to look like it heard the
    // tap, and a fix can take several seconds on a cold GPS.
    val transition = rememberInfiniteTransition(label = "locate-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "locate-alpha",
    )

    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = stringResource(R.string.map_control_locate),
            modifier = Modifier.alpha(if (isBusy) pulse else 1f),
        )
    }
}

/**
 * The primary action is labelled, not an anonymous circle: it says what it opens and how many
 * routes you are watching.
 */
@Composable
fun RoutesFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Filled.DirectionsBus, contentDescription = null)
        Text(
            text = stringResource(R.string.map_routes_button),
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(count.toString())
        }
    }
}

/**
 * A binary liveness indicator: green while data is arriving, red once it stops — so "nothing is
 * moving" is never ambiguous. The exact age of the last poll is detail nobody asked for.
 */
@Composable
fun ConnectionChip(
    isOnline: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val dot = when {
        isOnline && isDarkTheme -> OnlineGreenDark
        isOnline -> OnlineGreen
        isDarkTheme -> OfflineRedDark
        else -> OfflineRed
    }

    MapSurface(modifier = modifier, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(
                text = stringResource(
                    if (isOnline) R.string.map_status_online else R.string.map_status_disconnected,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Positions poll every 5 s; three missed ticks is a stall worth showing. */
const val StaleAfterMillis = 15_000L

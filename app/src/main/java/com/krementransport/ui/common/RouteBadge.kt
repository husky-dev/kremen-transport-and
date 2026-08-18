package com.krementransport.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krementransport.domain.model.RouteNumber
import com.krementransport.domain.model.TransitKind
import com.krementransport.util.contrastingLabel

enum class BadgeSize(val height: Dp, val fontSize: Int) {
    Small(22.dp, 11),
    Regular(30.dp, 14),
    Large(44.dp, 19),
}

/**
 * The route's identity, used identically on the map, in the picker and in the arrivals sheet —
 * so the list teaches you how to read the map.
 *
 * Bus and trolleybus differ by *silhouette*, not just by an icon: a capsule against a squared-off
 * tag. That reads at marker size, where a glyph would not.
 */
@Composable
fun RouteBadge(
    number: String,
    kind: TransitKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.Regular,
    isMuted: Boolean = false,
) {
    val fill = if (isMuted) MaterialTheme.colorScheme.outline else tint
    val shape = badgeShape(kind, size.height)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size.height * 1.3f, minHeight = size.height)
            .background(fill, shape)
            .padding(horizontal = size.height * 0.28f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = RouteNumber.badge(number),
            color = fill.contrastingLabel(),
            fontSize = size.fontSize.sp,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

fun badgeShape(kind: TransitKind, height: Dp): Shape = when (kind) {
    TransitKind.Bus -> RoundedCornerShape(percent = 50)
    TransitKind.Trolleybus -> RoundedCornerShape(height * 0.2f)
}

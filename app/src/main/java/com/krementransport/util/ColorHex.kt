package com.krementransport.util

import androidx.compose.ui.graphics.Color

/**
 * Parses the `#RRGGBB` strings the API assigns to routes. Live data mixes upper and lower case
 * (`#E67E23` next to `#e28478`), so parsing is case-insensitive; three-digit forms are accepted
 * defensively even though none have been observed.
 */
fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#").orEmpty()
    if (raw.length != 6 && raw.length != 3) return null
    val value = raw.toLongOrNull(16) ?: return null

    return if (raw.length == 3) {
        val r = ((value shr 8) and 0xF).toInt()
        val g = ((value shr 4) and 0xF).toInt()
        val b = (value and 0xF).toInt()
        Color(r * 17, g * 17, b * 17)
    } else {
        Color(
            red = ((value shr 16) and 0xFF).toInt(),
            green = ((value shr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
        )
    }
}

/**
 * Black or white, whichever stays readable on top of this colour. The threshold is tuned so the
 * mid-tone route colours (`#8FB9A8`, `#B2C253`) take black while `#7277D5` keeps white.
 */
fun Color.contrastingLabel(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.62f) Color.Black else Color.White
}

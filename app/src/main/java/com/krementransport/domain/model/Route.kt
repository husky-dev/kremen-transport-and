package com.krementransport.domain.model

/**
 * A route as returned by `transport/routes`, the only endpoint that carries usable stop data —
 * `transport/stations?rids=` and `transport/routes/{rid}/stations` are broken upstream and
 * return one station per route.
 */
data class Route(
    val rid: Int,
    val name: String,
    val number: String,
    val type: TransitKind,
    /** How many vehicles the backend currently counts on this route. */
    val active: Int,
    val color: String?,
    val path: List<Coordinate>,
    val stations: List<Station>,
) {
    val badge: String get() = RouteNumber.badge(number)

    /**
     * `"Річковий вокзал – Укртатнафта"` split into its two endpoints. The upstream data mixes
     * en-dashes, em-dashes, hyphens and stray double spaces.
     */
    val endpoints: Pair<String, String>? by lazy {
        for (separator in Separators) {
            val parts = name.split(separator)
            if (parts.size >= 2) {
                val from = parts.first().collapseSpaces()
                val to = parts.drop(1).joinToString(separator).collapseSpaces()
                if (from.isNotEmpty() && to.isNotEmpty()) return@lazy from to to
            }
        }
        null
    }

    /** Destination shown for an arrival, given the direction the vehicle is running. */
    fun destination(reverse: Boolean): String {
        val ends = endpoints ?: return name.collapseSpaces()
        return if (reverse) ends.first else ends.second
    }

    private companion object {
        val Separators = listOf(" – ", " — ", " - ", "–", "—")
    }
}

/** Upstream names contain doubled spaces; collapse them while trimming. */
fun String.collapseSpaces(): String = split(' ').filter { it.isNotEmpty() }.joinToString(" ")

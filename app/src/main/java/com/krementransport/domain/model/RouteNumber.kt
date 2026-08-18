package com.krementransport.domain.model

/**
 * Route numbers arrive unnormalised: `"1"`, `"3-б"`, `"10A"`, `"117"`, `"Т 1+"`, `"T15Б"`.
 * The leading letter is a Cyrillic *or* Latin T marking a trolleybus, so it carries no
 * information once the route's [TransitKind] is known — strip it for badges.
 */
object RouteNumber {
    private val TrolleyPrefixes = setOf('Т', 'т', 'T', 't')

    /** Compact label for a marker or badge: `"Т 1+"` -> `"1+"`, `"3-б"` -> `"3Б"`. */
    fun badge(raw: String): String {
        var value = raw.trim()
        while (value.isNotEmpty() && (value[0] in TrolleyPrefixes || value[0] == ' ')) {
            value = value.substring(1)
        }
        // A fully-alphabetic number (never seen live, but be safe) keeps its prefix.
        if (value.isEmpty()) value = raw
        return value.replace("-", "").replace(" ", "").uppercase()
    }

    /** Natural ordering: numeric part first, then any suffix. `2-в` < `3-а` < `10A` < `117`. */
    fun sortKey(raw: String): Pair<Int, String> {
        val badge = badge(raw)
        val digits = badge.takeWhile { it.isDigit() }
        return (digits.toIntOrNull() ?: Int.MAX_VALUE) to badge.drop(digits.length)
    }

    /**
     * Free-text match. Deliberately tolerates the Cyrillic/Latin `Т` mix-up in both directions,
     * so a Latin keyboard typing `T1` still finds the Cyrillic `Т 1`.
     */
    fun matches(raw: String, query: String): Boolean {
        val needle = badge(query)
        if (needle.isEmpty()) return true
        return badge(raw).contains(needle)
    }
}

val RouteNumberComparator: Comparator<String> =
    compareBy({ RouteNumber.sortKey(it).first }, { RouteNumber.sortKey(it).second })

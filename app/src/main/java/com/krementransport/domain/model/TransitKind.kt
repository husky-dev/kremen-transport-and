package com.krementransport.domain.model

/**
 * The API's `type` field: `"B"` bus, `"T"` trolleybus.
 *
 * Always group by the *route's* kind. A vehicle's own `type` is derived server-side from a
 * free-text name and is unreliable, and the number cannot be string-matched either — route
 * numbers mix a Cyrillic and a Latin `T`.
 */
enum class TransitKind {
    Bus,
    Trolleybus;

    companion object {
        /** Unknown values fall back to [Bus] rather than failing the whole payload. */
        fun from(raw: String?): TransitKind = if (raw == "T") Trolleybus else Bus
    }
}

package com.krementransport.data.api

import kotlinx.serialization.json.Json

/**
 * `ignoreUnknownKeys` because the wire carries fields no client uses (`cityId`, `filling`,
 * `invalidAdapted`, `generatedTime`), and `coerceInputValues` so an explicit `null` on a field
 * with a default degrades that field instead of the payload.
 */
val TransportJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

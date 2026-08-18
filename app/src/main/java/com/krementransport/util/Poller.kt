package com.krementransport.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * A cancellation-aware repeat loop. Cancelling the scope is the whole shutdown mechanism: the
 * map collects these inside `repeatOnLifecycle(STARTED)`, so backgrounding the app stops every
 * poll without any explicit teardown.
 *
 * Intervals are tied to the backend's own cadence — it rebuilds vehicles every 10 s and routes
 * hourly — so polling faster than the callers do buys nothing.
 */
fun CoroutineScope.poll(
    every: Duration,
    immediate: Boolean = true,
    body: suspend () -> Unit,
): Job = launch {
    if (immediate) body()
    while (isActive) {
        delay(every)
        if (!isActive) return@launch
        body()
    }
}

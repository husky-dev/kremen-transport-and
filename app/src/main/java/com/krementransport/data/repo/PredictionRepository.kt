package com.krementransport.data.repo

import android.util.Log
import com.krementransport.data.api.ApiClient
import com.krementransport.domain.model.Prediction

/**
 * One stop's arrivals. Created *with* the sheet and thrown away with it, so its 5-second poll can
 * never outlive the UI that asked for it.
 */
class PredictionRepository(private val api: ApiClient) {

    data class Snapshot(
        val predictions: List<Prediction> = emptyList(),
        val hasLoaded: Boolean = false,
        val error: String? = null,
    )

    suspend fun load(sid: Int, previous: Snapshot): Snapshot = try {
        Snapshot(api.prediction(sid), hasLoaded = true, error = null)
    } catch (error: Exception) {
        Log.e("PredictionRepository", "prediction fetch failed for $sid", error)
        // A failed refresh must not blank rows that are still roughly right.
        previous.copy(error = error.message ?: "unknown")
    }
}

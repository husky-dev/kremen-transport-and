package com.krementransport.domain.model

/** An arrival forecast for one vehicle at one stop. */
data class Prediction(
    val rid: Int,
    val sid: Int,
    val tid: String,
    /** Seconds until arrival. */
    val seconds: Int,
    /** Metres away. */
    val distance: Int,
    /**
     * Which way the vehicle is running. The stop itself carries no reliable direction (the same
     * `sid` is listed both ways), so this flag is what splits the arrivals sheet.
     */
    val reverse: Boolean,
    val avgSpeed: Int,
    val speed: Int,
    val mainPrediction: Boolean,
) {
    val id: String get() = "$tid-$rid-$reverse"
}

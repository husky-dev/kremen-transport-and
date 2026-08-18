package com.krementransport.ui.map

/** What the user has tapped on the map. One selection, one sheet. */
sealed interface MapTarget {
    data class Stop(val sid: Int) : MapTarget
    data class Vehicle(val tid: String) : MapTarget
}

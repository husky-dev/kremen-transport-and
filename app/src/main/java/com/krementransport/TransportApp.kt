package com.krementransport

import android.app.Application

class TransportApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

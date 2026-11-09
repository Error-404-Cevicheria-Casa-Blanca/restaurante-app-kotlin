package com.lacasablanca.mesero

import android.app.Application
import com.lacasablanca.mesero.core.AppContainer
import com.lacasablanca.mesero.data.local.offline.SyncScheduler

class MeseroApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.programar(this)
    }
}

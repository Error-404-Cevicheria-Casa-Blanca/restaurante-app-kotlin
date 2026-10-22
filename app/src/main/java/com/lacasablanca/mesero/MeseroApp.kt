package com.lacasablanca.mesero

import android.app.Application
import com.lacasablanca.mesero.core.AppContainer

class MeseroApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

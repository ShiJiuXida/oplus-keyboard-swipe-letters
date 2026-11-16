package com.kbswipe.module

import android.app.Application
import com.kbswipe.module.ui.ServiceState

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceState.start()
    }
}

package com.kbswipe.module

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceState.start()
    }
}

package com.apbarrero.wheresmycar

import android.app.Application
import timber.log.Timber

class WheresMyCarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

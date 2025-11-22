package com.lifecompanion

import android.app.Application
import timber.log.Timber

/**
 * Application class for Life Companion
 */
class LifeCompanionApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("Life Companion App started")
    }
}

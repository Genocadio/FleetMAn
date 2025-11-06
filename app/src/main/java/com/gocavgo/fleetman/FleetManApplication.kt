package com.gocavgo.fleetman

import android.app.Application
import com.gocavgo.fleetman.ui.components.ActivityTracker

class FleetManApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register activity tracker to track current activity
        ActivityTracker.register(this)
    }
}


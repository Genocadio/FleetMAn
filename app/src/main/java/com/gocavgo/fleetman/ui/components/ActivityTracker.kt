package com.gocavgo.fleetman.ui.components

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks the current foreground activity
 */
object ActivityTracker {
    private var currentActivity: Activity? = null

    fun getCurrentActivity(): Activity? = currentActivity

    fun setCurrentActivity(activity: Activity?) {
        currentActivity = activity
    }

    /**
     * Register activity lifecycle callbacks to track current activity
     * Uses onActivityResumed/onActivityPaused for accurate foreground/background detection
     */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Don't set here - wait for resumed
            }

            override fun onActivityStarted(activity: Activity) {
                // Don't set here - wait for resumed
            }

            override fun onActivityResumed(activity: Activity) {
                // Activity is now in foreground
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                // Activity is now in background
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }

            override fun onActivityStopped(activity: Activity) {
                // Keep cleared if this was the current activity
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
        })
    }
}


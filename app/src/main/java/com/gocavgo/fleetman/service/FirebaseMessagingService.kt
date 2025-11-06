package com.gocavgo.fleetman.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gocavgo.fleetman.R
import com.gocavgo.fleetman.auth.CredentialsManager
import com.gocavgo.fleetman.trips.CreateTripActivity
import com.gocavgo.fleetman.ui.components.ActivityTracker
import com.gocavgo.fleetman.ui.components.ToastConfig
import com.gocavgo.fleetman.ui.components.ToastManager
import com.gocavgo.fleetman.ui.components.ToastType
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMessagingService"
        private const val CHANNEL_ID = "trip_notifications"
        private const val CHANNEL_NAME = "Trip Notifications"
        private const val NOTIFICATION_ID = 1001
        private const val GLOBAL_TOPIC = "tripsupdates"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // Subscribe to topics after token refresh
        subscribeToTopics()

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        // TODO: Implement this method to send token to your app server.
        Log.d(TAG, "sendRegistrationTokenToServer($token)")
    }

    /**
     * Subscribe to FCM topics: global "tripsupdates" and company-specific "tripsupdates_$companyId"
     * Note: FCM topic names must match [a-zA-Z0-9-_.~%]{1,900}, so we use underscore instead of slash
     */
    private fun subscribeToTopics() {
        // Subscribe to global topic
        FirebaseMessaging.getInstance().subscribeToTopic(GLOBAL_TOPIC)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to topic: $GLOBAL_TOPIC")
                } else {
                    Log.e(TAG, "Failed to subscribe to topic: $GLOBAL_TOPIC", task.exception)
                }
            }

        // Subscribe to company-specific topic
        val credentialsManager = CredentialsManager(applicationContext)
        val companyId = credentialsManager.getCompanyId()
        
        if (companyId > 0) {
            val companyTopic = "${GLOBAL_TOPIC}_$companyId"
            FirebaseMessaging.getInstance().subscribeToTopic(companyTopic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to topic: $companyTopic")
                    } else {
                        Log.e(TAG, "Failed to subscribe to topic: $companyTopic", task.exception)
                    }
                }
        } else {
            Log.d(TAG, "Company ID not available, skipping company-specific topic subscription")
        }
    }

    /**
     * Create notification channel for trip notifications
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for trip updates"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message ID: ${remoteMessage.messageId}")

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            // For data-only messages, onMessageReceived is only called when app is in foreground
            // So we can directly show the toast notification
            handleTripNotification(remoteMessage.data)
        } else {
            Log.d(TAG, "Message has no data payload")
        }

        // Check if message contains a notification payload (for background notifications)
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            Log.d(TAG, "Message Notification Title: ${it.title}")
        }
    }

    /**
     * Handle trip notification data
     * Note: For data-only FCM messages, onMessageReceived is only called when app is in foreground.
     * If app is in background, FCM handles it automatically (but we're using data-only, so this won't happen).
     */
    private fun handleTripNotification(data: Map<String, String>) {
        val type = data["type"] ?: run {
            Log.w(TAG, "Notification missing 'type' field")
            return
        }

        val tripId = data["trip_id"] ?: run {
            Log.w(TAG, "Notification missing 'trip_id' field")
            return
        }

        val carId = data["car_id"] ?: run {
            Log.w(TAG, "Notification missing 'car_id' field")
            return
        }

        val plate = data["plate"] ?: run {
            Log.w(TAG, "Notification missing 'plate' field")
            return
        }

        val message = data["message"] ?: run {
            Log.w(TAG, "Notification missing 'message' field")
            return
        }

        Log.d(TAG, "Processing trip notification - Type: $type, Trip ID: $tripId, Car ID: $carId, Plate: $plate")

        // Check if app is in foreground using ActivityTracker (fast, no blocking)
        val currentActivity = ActivityTracker.getCurrentActivity()
        val isForeground = currentActivity != null
        
        Log.d(TAG, "App state - isForeground: $isForeground, currentActivity: ${currentActivity?.javaClass?.simpleName}")
        
        if (isForeground) {
            // App is in foreground - show floating toast notification only
            Log.d(TAG, "Showing foreground toast notification")
            showForegroundNotification(type, message, carId, plate)
        } else {
            // App is in background - show system notification
            Log.d(TAG, "Showing background system notification")
            showBackgroundNotification(type, message, carId, plate, tripId)
        }
    }

    /**
     * Show floating toast notification for foreground
     * Posts to main thread handler to avoid blocking the service
     */
    private fun showForegroundNotification(type: String, message: String, carId: String, plate: String) {
        val toastType = when (type) {
            "about_to_complete" -> ToastType.WARNING
            "completed" -> ToastType.SUCCESS
            else -> ToastType.INFO
        }

        val title = when (type) {
            "about_to_complete" -> "Trip About to Complete"
            "completed" -> "Trip Completed"
            else -> "Trip Update"
        }

        val intent = createTripActivityIntent(carId, plate)
        
        val config = ToastConfig(
            type = toastType,
            title = title,
            message = message,
            duration = 6000L,
            dismissible = true,
            onClick = {
                // Make entire toast clickable to open CreateTripActivity
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        )

        // Post to main thread handler to avoid blocking the service
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                ToastManager.show(applicationContext, config)
                Log.d(TAG, "Foreground toast notification shown")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show toast notification", e)
            }
        }
    }

    /**
     * Show system notification for background
     */
    private fun showBackgroundNotification(
        type: String,
        message: String,
        carId: String,
        plate: String,
        tripId: String
    ) {
        val title = when (type) {
            "about_to_complete" -> "Trip About to Complete"
            "completed" -> "Trip Completed"
            else -> "Trip Update"
        }

        val intent = createTripActivityIntent(carId, plate)
        val pendingIntent = PendingIntent.getActivity(
            this,
            tripId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(tripId.hashCode(), notification)
        Log.d(TAG, "Background system notification shown")
    }

    /**
     * Create Intent to open CreateTripActivity with car_id and plate
     */
    private fun createTripActivityIntent(carId: String, plate: String): Intent {
        return Intent(this, CreateTripActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(CreateTripActivity.EXTRA_CAR_ID, carId)
            putExtra(CreateTripActivity.EXTRA_LICENSE_PLATE, plate)
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.d(TAG, "onDeletedMessages: FCM has deleted pending messages. App should perform a full sync with server.")
    }
}
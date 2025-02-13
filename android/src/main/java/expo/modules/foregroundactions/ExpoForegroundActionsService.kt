package expo.modules.foregroundactions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import android.util.Log as AndroidLog
import android.app.PendingIntent

class ExpoForegroundActionsService : HeadlessJsTaskService() {
    companion object {
        private const val CHANNEL_ID = "ExpoForegroundActionChannel"
        private const val RECEIVER_KEY = "receiver"
        private const val NOTIFICATION_ID_KEY = "notificationId"
        private const val RESULT_CODE_OK = 0
        private const val LOG_TAG = "ExpoForegroundActions"  // Use same tag as module

        fun buildNotification(
                context: Context,
                notificationTitle: String,
                notificationDesc: String,
                notificationColor: Int,
                notificationIconInt: Int,
                notificationProgress: Int,
                notificationMaxProgress: Int,
                notificationIndeterminate: Boolean,
                linkingURI: String
        ): Notification {

            val notificationIntent: Intent = if (linkingURI.isNotEmpty()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(linkingURI))
            } else {
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val contentIntent: PendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationDesc)
                    .setSmallIcon(notificationIconInt)
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setSilent(true)
                    .setProgress(notificationMaxProgress, notificationProgress, notificationIndeterminate)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setColor(notificationColor)
            return builder.build()
        }
    }

    // Cache extras from onStartCommand
    private var extras: Bundle? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        extras = intent?.extras
        val localExtras = extras
        requireNotNull(localExtras) { "Extras cannot be null" }

        val notificationId = localExtras.getInt("notificationId")
        AndroidLog.d(LOG_TAG, "Starting service with ID: $notificationId (action: ${intent?.action})")

        val notificationTitle: String = localExtras.getString("notificationTitle")!!
        val notificationDesc: String = localExtras.getString("notificationDesc")!!
        val notificationColor: Int = Color.parseColor(localExtras.getString("notificationColor"))
        val notificationIconInt: Int = localExtras.getInt("notificationIconInt")
        val notificationProgress: Int = localExtras.getInt("notificationProgress")
        val notificationMaxProgress: Int = localExtras.getInt("notificationMaxProgress")
        val notificationIndeterminate: Boolean = localExtras.getBoolean("notificationIndeterminate")
        val linkingURI: String = localExtras.getString("linkingURI")!!

        AndroidLog.d(LOG_TAG, "Creating notification channel and building notification")
        createNotificationChannel()

        val notification: Notification = buildNotification(
            this,
            notificationTitle,
            notificationDesc,
            notificationColor,
            notificationIconInt,
            notificationProgress,
            notificationMaxProgress,
            notificationIndeterminate,
            linkingURI
        )

        AndroidLog.d(LOG_TAG, "Starting foreground service")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
        AndroidLog.d(LOG_TAG, "Service started successfully")

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        AndroidLog.d(LOG_TAG, "createNotificationChannel")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    override fun getTaskConfig(intent: Intent): HeadlessJsTaskConfig? {
        AndroidLog.d(LOG_TAG, "getTaskConfig called")
        return intent.extras?.let { originalExtras ->
            // Create a new Bundle with only the supported data
            val taskData = Bundle().apply {
                // Copy only the necessary data
                putString("headlessTaskName", originalExtras.getString("headlessTaskName"))
                putString("notificationTitle", originalExtras.getString("notificationTitle"))
                putString("notificationDesc", originalExtras.getString("notificationDesc"))
                putString("notificationColor", originalExtras.getString("notificationColor"))
                putInt("notificationIconInt", originalExtras.getInt("notificationIconInt"))
                putInt("notificationProgress", originalExtras.getInt("notificationProgress"))
                putInt("notificationMaxProgress", originalExtras.getInt("notificationMaxProgress"))
                putBoolean("notificationIndeterminate", originalExtras.getBoolean("notificationIndeterminate"))
                putString("linkingURI", originalExtras.getString("linkingURI"))
                putInt("notificationId", originalExtras.getInt("notificationId"))
                // Exclude the ResultReceiver
            }

            HeadlessJsTaskConfig(
                originalExtras.getString("headlessTaskName")!!,
                Arguments.fromBundle(taskData), // Use the new Bundle
                0, // timeout for the task
                true // optional: defines whether or not the task is allowed in foreground.
                // Default is false
            )
        }
    }

    override fun onDestroy() {
        val notificationId = extras?.getInt("notificationId")
        AndroidLog.d(LOG_TAG, "Service onDestroy called for ID: $notificationId")

        val receiver = extras?.get(RECEIVER_KEY) as? ResultReceiver
        if (notificationId != null && receiver != null) {
            AndroidLog.d(LOG_TAG, "Sending result back to module for ID: $notificationId")
            val resultData = Bundle().apply {
                putInt(NOTIFICATION_ID_KEY, notificationId)
            }
            receiver.send(RESULT_CODE_OK, resultData)
        }

        stopSelf()
        AndroidLog.d(LOG_TAG, "Service instance destroyed for ID: $notificationId")
        super.onDestroy()
    }
}

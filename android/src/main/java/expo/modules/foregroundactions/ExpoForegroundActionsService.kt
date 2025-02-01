package expo.modules.foregroundactions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.ReactApplication
import expo.modules.core.ModulesProvider

class ExpoForegroundActionsService : HeadlessJsTaskService() {
    companion object {
        private const val CHANNEL_ID = "ExpoForegroundActionChannel"
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
            val contentIntent: PendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
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

    private fun sendExpirationEvent(notificationId: Int) {
        try {
            val reactApplication = applicationContext as ReactApplication
            val reactContext = reactApplication.reactNativeHost.reactInstanceManager.currentReactContext
                ?: return // Return if no React context

            // Get the module through Expo's ModulesProvider
            val modulesProvider = reactContext.getNativeModule(expo.modules.core.ModulesProvider::class.java)
                ?: return

            val module = modulesProvider.getModule(ExpoForegroundActionsModule::class.java)
            module?.emitExpirationEvent(notificationId, 0.0)

        } catch (e: Exception) {
            println("Failed to emit expiration event: ${e.message}")
        }
    }

    override fun onDestroy() {
        // Send event before calling super.onDestroy(), similar to iOS timing
        val notificationId = extras?.getInt("notificationId")
        if (notificationId != null) {
            sendExpirationEvent(notificationId)
        }
        super.onDestroy()
    }

    // Cache extras from onStartCommand
    private var extras: Bundle? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        extras = intent?.extras  // Store extras for later use
        val localExtras = extras
        requireNotNull(localExtras) { "Extras cannot be null" }

        val notificationTitle: String = localExtras.getString("notificationTitle")!!;
        val notificationDesc: String = localExtras.getString("notificationDesc")!!;
        val notificationColor: Int = Color.parseColor(localExtras.getString("notificationColor"))
        val notificationIconInt: Int = localExtras.getInt("notificationIconInt");
        val notificationProgress: Int = localExtras.getInt("notificationProgress");
        val notificationMaxProgress: Int = localExtras.getInt("notificationMaxProgress");
        val notificationIndeterminate: Boolean = localExtras.getBoolean("notificationIndeterminate");
        val notificationId: Int = localExtras.getInt("notificationId");
        val linkingURI: String = localExtras.getString("linkingURI")!!;

        println("notificationIconInt");
        println(notificationIconInt);
        println("On create door dion")
        println("onStartCommand")
        createNotificationChannel() // Necessary creating channel for API 26+
        println("After createNotificationChannel")

        println("buildNotification")
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
        println("Starting foreground")

        startForeground(notificationId, notification)
        println("After foreground")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        println("createNotificationChannel")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    override fun getTaskConfig(intent: Intent): HeadlessJsTaskConfig? {
        return intent.extras?.let {
            HeadlessJsTaskConfig(
                    intent.extras?.getString("headlessTaskName")!!,
                    Arguments.fromBundle(it),
                    0, // timeout for the task
                    true // optional: defines whether or not the task is allowed in foreground.
                    // Default is false
            )
        }
    }
}

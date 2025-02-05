package expo.modules.foregroundactions

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.exception.toCodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.util.Log as AndroidLog


const val ON_EXPIRATION_EVENT = "onExpirationEvent"

class ExpoForegroundActionsModule : Module() {
    companion object {
        private const val RECEIVER_KEY = "receiver"
        private const val NOTIFICATION_ID_KEY = "notificationId"
        private const val RESULT_CODE_OK = 0
        private const val BASE_ACTION = "expo.modules.foregroundactions.ACTION_FOREGROUND_SERVICE"
        private const val LOG_TAG = "ExpoForegroundActions"
    }

    private data class ServiceInfo(
        val intent: Intent,
        val startTime: Long = System.currentTimeMillis()
    )

    private var currentTask: ServiceInfo? = null
    private var currentId: Int = 0

    // Each module class must implement the definition function. The definition consists of components
    // that describes the module's functionality and behavior.
    // See https://docs.expo.dev/modules/module-api for more details about available components.
    @SuppressLint("DiscouragedApi")
    override fun definition() = ModuleDefinition {
        Events(ON_EXPIRATION_EVENT)

        // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
        // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
        // The module will be accessible from `requireNativeModule('ExpoForegroundActions')` in JavaScript.
        Name("ExpoForegroundActions")


        AsyncFunction("startForegroundAction") { options: ExpoForegroundOptions, promise: Promise ->
            try {
                // If a task is already running, reject the new request.
                if (currentTask != null) {
                    AndroidLog.e(LOG_TAG, "Attempted to start a new foreground action while one is already running")
                    throw Exception("A foreground action is already running. Please stop it before starting a new one.")
                }
                currentId++

                val intent = Intent(context, ExpoForegroundActionsService::class.java).apply {
                    action = "$BASE_ACTION.$currentId"
                }

                // Add all the extras
                intent.putExtra("notificationId", currentId)  // Use this as the identifier
                intent.putExtra("headlessTaskName", options.headlessTaskName)
                intent.putExtra("notificationTitle", options.notificationTitle)
                intent.putExtra("notificationDesc", options.notificationDesc)
                intent.putExtra("notificationColor", options.notificationColor)
                val notificationIconInt: Int = context.resources.getIdentifier(options.notificationIconName, options.notificationIconType, context.packageName)
                intent.putExtra("notificationIconInt", notificationIconInt)
                intent.putExtra("notificationProgress", options.notificationProgress)
                intent.putExtra("notificationMaxProgress", options.notificationMaxProgress)
                intent.putExtra("notificationIndeterminate", options.notificationIndeterminate)
                intent.putExtra("linkingURI", options.linkingURI)

                // Add ResultReceiver so that onDestroy in the service fires the expiration event
                intent.putExtra(RECEIVER_KEY, object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == RESULT_CODE_OK) {
                            val notificationId = resultData?.getInt(NOTIFICATION_ID_KEY)
                            sendEvent(ON_EXPIRATION_EVENT, mapOf(
                                "identifier" to notificationId,
                                "remaining" to 0.0
                            ))
                        }
                    }
                })

                // Store the current task.
                currentTask = ServiceInfo(intent.clone() as Intent)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                promise.resolve(currentId)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error starting foreground action: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("stopForegroundAction") { identifier: Int, isAutomatic: Boolean, promise: Promise ->
            try {
                // Check if the running task matches the provided identifier.
                val activeId = currentTask?.intent?.getIntExtra("notificationId", -1)
                if (activeId == identifier) {
                    stopCurrentTask()
                    AndroidLog.d(LOG_TAG, "Successfully stopped task with identifier $identifier (${if (isAutomatic) "automatic" else "manual"})")
                } else {
                    AndroidLog.w(LOG_TAG, "Task with identifier $identifier is not the current task (${if (isAutomatic) "automatic" else "manual"})")
                }

                promise.resolve(null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error stopping task with identifier $identifier: ${e.message} (${if (isAutomatic) "automatic" else "manual"})")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("updateForegroundedAction") { identifier: Int, options: ExpoForegroundOptions, promise: Promise ->
            try {
                val notificationIconInt: Int = context.resources.getIdentifier(options.notificationIconName, options.notificationIconType, context.packageName)
                val notification: Notification = ExpoForegroundActionsService.buildNotification(
                        context,
                        options.notificationTitle,
                        options.notificationDesc,
                        Color.parseColor(options.notificationColor),
                        notificationIconInt,
                        options.notificationProgress,
                        options.notificationMaxProgress,
                        options.notificationIndeterminate,
                        options.linkingURI,
                );
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(identifier, notification)
                promise.resolve(null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error updating foreground action: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("getForegroundIdentifiers") { promise: Promise ->
            if (currentTask != null) {
                promise.resolve(currentTask!!.intent.getIntExtra("notificationId", -1))
            } else {
                promise.resolve(arrayOf<Int>())
            }
        }

        AsyncFunction("isServiceRunning") { identifier: Int, promise: Promise ->
            try {
                val activeId = currentTask?.intent?.getIntExtra("notificationId", -1)
                promise.resolve(activeId == identifier)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error checking service status: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }
    }

    private val context
        get() = requireNotNull(appContext.reactContext) {
            "React Application Context is null"
        }

    private val applicationContext
        get() = requireNotNull(this.context.applicationContext) {
            "React Application Context is null"
        }

    private fun stopCurrentTask() {
        currentTask?.let { task ->
            val id = task.intent.getIntExtra("notificationId", -1)
            // Cancel the notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
            // Stop the service which will trigger onDestroy and fire the expiration listener
            context.stopService(Intent(context, ExpoForegroundActionsService::class.java))
            currentTask = null
        }
    }
}

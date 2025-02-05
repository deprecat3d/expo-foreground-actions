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
        // Define a private constant for the task ID since only one task can run.
        private const val TASK_ID = 1
    }

    private data class ServiceInfo(
        val intent: Intent,
        val startTime: Long = System.currentTimeMillis()
    )

    // Since only one task is allowed, we use a constant task ID (1) and do not need dynamic IDs.
    private var currentTask: ServiceInfo? = null

    @SuppressLint("DiscouragedApi")
    override fun definition() = ModuleDefinition {
        Events(ON_EXPIRATION_EVENT)
        Name("ExpoForegroundActions")

        AsyncFunction("startForegroundAction") { options: ExpoForegroundOptions, promise: Promise ->
            try {
                if (currentTask != null) {
                    AndroidLog.e(LOG_TAG, "Attempted to start a new foreground action while one is already running")
                    throw Exception("A foreground action is already running. Please stop it before starting a new one.")
                }
                // Using the constant task identifier TASK_ID.
                val taskId = TASK_ID
                val intent = Intent(context, ExpoForegroundActionsService::class.java).apply {
                    action = "$BASE_ACTION.$taskId"
                }

                // Add extras – these include the notification details, etc.
                intent.putExtra("notificationId", taskId)
                intent.putExtra("headlessTaskName", options.headlessTaskName)
                intent.putExtra("notificationTitle", options.notificationTitle)
                intent.putExtra("notificationDesc", options.notificationDesc)
                intent.putExtra("notificationColor", options.notificationColor)
                val notificationIconInt: Int = context.resources.getIdentifier(
                    options.notificationIconName,
                    options.notificationIconType,
                    context.packageName
                )
                intent.putExtra("notificationIconInt", notificationIconInt)
                intent.putExtra("notificationProgress", options.notificationProgress)
                intent.putExtra("notificationMaxProgress", options.notificationMaxProgress)
                intent.putExtra("notificationIndeterminate", options.notificationIndeterminate)
                intent.putExtra("linkingURI", options.linkingURI)

                // Add a ResultReceiver so that onDestroy in the service fires the expiration event.
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

                currentTask = ServiceInfo(intent.clone() as Intent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                // Always resolve with the constant task ID (even though JS will not use it)
                promise.resolve(taskId)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error starting foreground action: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("stopForegroundAction") { isAutomatic: Boolean, promise: Promise ->
            try {
                if (currentTask != null) {
                    stopCurrentTask()
                    AndroidLog.d(LOG_TAG, "Successfully stopped the running task (${if (isAutomatic) "automatic" else "manual"})")
                } else {
                    AndroidLog.w(LOG_TAG, "No running task to stop (${if (isAutomatic) "automatic" else "manual"})")
                }
                promise.resolve(null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error stopping task: ${e.message} (${if (isAutomatic) "automatic" else "manual"})")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("updateForegroundedAction") { options: ExpoForegroundOptions, promise: Promise ->
            try {
                val notificationIconInt: Int = context.resources.getIdentifier(
                    options.notificationIconName,
                    options.notificationIconType,
                    context.packageName
                )
                val notification: Notification = ExpoForegroundActionsService.buildNotification(
                    context,
                    options.notificationTitle,
                    options.notificationDesc,
                    Color.parseColor(options.notificationColor),
                    notificationIconInt,
                    options.notificationProgress,
                    options.notificationMaxProgress,
                    options.notificationIndeterminate,
                    options.linkingURI
                )
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // Notify using the constant task ID TASK_ID.
                notificationManager.notify(TASK_ID, notification)
                promise.resolve(null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error updating foreground action: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("isServiceRunning") { promise: Promise ->
            try {
                promise.resolve(currentTask != null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error checking service status: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }
    }

    private val context: Context
        get() = requireNotNull(appContext.reactContext) {
            "React Application Context is null"
        }

    private fun stopCurrentTask() {
        currentTask?.let { task ->
            val id = task.intent.getIntExtra("notificationId", -1)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
            context.stopService(Intent(context, ExpoForegroundActionsService::class.java))
            currentTask = null
        }
    }
}

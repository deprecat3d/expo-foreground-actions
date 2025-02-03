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

    private val intentMap: MutableMap<Int, Intent> = mutableMapOf()
    private var currentReferenceId: Int = 0

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
                currentReferenceId++

                val intent = Intent(context, ExpoForegroundActionsService::class.java).apply {
                    action = "$BASE_ACTION.$currentReferenceId"
                }

                // Add all the extras
                intent.putExtra("notificationId", currentReferenceId)  // Use this as the identifier
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

                // Add ResultReceiver
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

                // Store the intent with its unique identifier
                intentMap[currentReferenceId] = intent.clone() as Intent

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                promise.resolve(currentReferenceId)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error starting foreground action: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }

        AsyncFunction("stopForegroundAction") { identifier: Int, isAutomatic: Boolean = false, promise: Promise ->
            try {
                val intent = intentMap[identifier]
                if (intent != null) {
                    AndroidLog.d(LOG_TAG, "Attempting to stop service with action: ${intent.action} (${if (isAutomatic) "automatic" else "manual"})")
                    val stopped = context.stopService(intent)
                    if (stopped) {
                        intentMap.remove(identifier)
                        AndroidLog.d(LOG_TAG, "Successfully stopped task with identifier $identifier (${if (isAutomatic) "automatic" else "manual"})")
                    } else {
                        AndroidLog.e(LOG_TAG, "Failed to stop task with identifier $identifier")
                    }
                } else {
                    AndroidLog.w(LOG_TAG, "Task with identifier $identifier does not exist or has already been ended")
                }
                promise.resolve(null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error stopping task with identifier $identifier: ${e.message}")
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

        AsyncFunction("forceStopAllForegroundActions") { promise: Promise ->
            try {
                val iterator = intentMap.iterator()
                while (iterator.hasNext()) {
                    val (id, intent) = iterator.next()
                    val stopped = context.stopService(intent)
                    if (stopped) {
                        AndroidLog.d(LOG_TAG, "Successfully stopped task with identifier $id")
                        iterator.remove() // Remove the entry from the map
                    } else {
                        AndroidLog.e(LOG_TAG, "Failed to stop task with identifier $id")
                    }
                }
                promise.resolve(null)
            } catch (e: Exception) {
                AndroidLog.e(LOG_TAG, "Error force stopping all tasks: ${e.message}")
                promise.reject(e.toCodedException())
            }
        }
        AsyncFunction("getForegroundIdentifiers") { promise: Promise ->
            val identifiers = intentMap.keys.toTypedArray()
            promise.resolve(identifiers)
        }

        AsyncFunction("isServiceRunning") { identifier: Int, promise: Promise ->
            try {
                val intent = intentMap[identifier]
                promise.resolve(intent != null)
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
}

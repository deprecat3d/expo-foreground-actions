import ExpoModulesCore
let ON_EXPIRATION_EVENT = "onExpirationEvent"

public class ExpoForegroundActionsModule: Module {
    // Use a single background task identifier to support one foreground action at a time.
    var currentBackgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid

    // Each module class must implement the definition function. The definition consists of components
    // that describes the module's functionality and behavior.
    // See https://docs.expo.dev/modules/module-api for more details about available components.
    public func definition() -> ModuleDefinition {
        Events(ON_EXPIRATION_EVENT)

        // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
        // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
        // The module will be accessible from `requireNativeModule('ExpoForegroundActions')` in JavaScript.
        Name("ExpoForegroundActions")

        AsyncFunction("startForegroundAction") { (promise: Promise) in
            // Reject if a task is already running.
            if self.currentBackgroundTaskIdentifier != .invalid {
                promise.reject("E_TASK_ALREADY_RUNNING", "A foreground action is already running. Please stop it before starting a new one.")
                return
            }
            var backgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid
            backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask {
                // Expiration block: fire expiration event, end the task, and mark it as invalid.
                self.onExpiration(amount: UIApplication.shared.backgroundTimeRemaining, identifier: backgroundTaskIdentifier)
                UIApplication.shared.endBackgroundTask(backgroundTaskIdentifier)
                self.currentBackgroundTaskIdentifier = .invalid
            }
            self.currentBackgroundTaskIdentifier = backgroundTaskIdentifier
            print(backgroundTaskIdentifier.rawValue)
            promise.resolve(backgroundTaskIdentifier.rawValue)
        }

        AsyncFunction("stopForegroundAction") { (isAutomatic: Bool, promise: Promise) in
            if self.currentBackgroundTaskIdentifier == .invalid {
                print("No running background task to stop (\(isAutomatic ? "automatic" : "manual")).")
                promise.resolve()
                return
            }
            let taskID = self.currentBackgroundTaskIdentifier
            print("Stopping background task \(taskID.rawValue) (\(isAutomatic ? "automatic" : "manual")).")
            UIApplication.shared.endBackgroundTask(taskID)
            self.currentBackgroundTaskIdentifier = .invalid
            promise.resolve()
        }

        AsyncFunction("getBackgroundTimeRemaining") { (promise: Promise) in
            promise.resolve(UIApplication.shared.backgroundTimeRemaining)
        }

        AsyncFunction("getForegroundIdentifiers") { (promise: Promise) in
            if self.currentBackgroundTaskIdentifier != .invalid {
                promise.resolve([self.currentBackgroundTaskIdentifier.rawValue])
            } else {
                promise.resolve([])
            }
        }
    }

    @objc
    private func onExpiration(amount: Double, identifier: UIBackgroundTaskIdentifier) {
        sendEvent(ON_EXPIRATION_EVENT, [
            "remaining": amount,
            "identifier": identifier.rawValue
        ])
    }
}

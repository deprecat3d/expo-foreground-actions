import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
  Platform
} from "expo-modules-core";
import { platformApiLevel } from "expo-device";
import {
  ExpireEventPayload,
  AndroidSettings,
  ForegroundApi,
  Settings,
  ServiceStatus
} from "./ExpoForegroundActions.types";
import ExpoForegroundActionsModule from "./ExpoForegroundActionsModule";
import { AppRegistry, AppState } from "react-native";

const emitter = new EventEmitter(
  ExpoForegroundActionsModule ?? NativeModulesProxy.ExpoForegroundActions
);

let ranTaskCount: number = 0;

export class NotForegroundedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "NotForegroundedError";
  }
}

const startForegroundAction = async (options?: AndroidSettings): Promise<number> => {
  if (Platform.OS === "android" && !options) {
    throw new Error("Foreground action options are required on Android.");
  }
  if (Platform.OS === "android") {
    return ExpoForegroundActionsModule.startForegroundAction(options);
  } else {
    return ExpoForegroundActionsModule.startForegroundAction();
  }
};

export const runForegroundedAction = async (
  act: (api: ForegroundApi) => Promise<void>,
  androidSettings: AndroidSettings,
  settings: Settings = { runInJS: false }
): Promise<void> => {
  if (!androidSettings) {
    throw new Error("Foreground action options are required.");
  }
  if (AppState.currentState === "background") {
    throw new NotForegroundedError("Foreground actions can only be run in the foreground.");
  }
  if (Platform.OS !== "ios" && Platform.OS !== "android") {
    throw new Error("Unsupported platform. Currently only iOS and Android are supported.");
  }
  if (Platform.OS === "android" && platformApiLevel && platformApiLevel < 26) {
    settings.runInJS = true;
  }

  const headlessTaskName = `${androidSettings.headlessTaskName}${ranTaskCount}`;
  const action = async () => {
    if (AppState.currentState === "background") {
      throw new NotForegroundedError("Foreground actions can only be run in the foreground.");
    }
    await act({ headlessTaskName });
  };

  let expirationSubscription: Subscription | undefined;
  if (settings.events?.onBeforeExpires) {
    expirationSubscription = addExpirationListener((event: ExpireEventPayload) => {
      expirationSubscription?.remove();
      settings.events!.onBeforeExpires!().catch(err =>
        console.error("[Foreground Actions] Error in onBeforeExpires:", err)
      );
    });
  }
  try {
    ranTaskCount++;
    if (settings.runInJS === true) {
      await runJS(action);
      return;
    }
    if (Platform.OS === "android") {
      await runAndroid(action, { ...androidSettings, headlessTaskName });
      return;
    }
    if (Platform.OS === "ios") {
      await runIos(action);
      return;
    }
  } catch (e) {
    throw e;
  } finally {
    expirationSubscription?.remove();
  }
};

const runJS = async (
  action: () => Promise<void>,
) => {
  await action();
};

const runIos = async (
  action: () => Promise<void>,
) => {
  await startForegroundAction();
  try {
    await action();
  } catch (e) {
    throw e;
  } finally {
    await ExpoForegroundActionsModule.stopForegroundAction(true);  // automatic stop
  }
};

const runAndroid = async (
  action: () => Promise<void>,
  options: AndroidSettings,
) => new Promise<void>(async (resolve, reject) => {
  try {
    AppRegistry.registerHeadlessTask(options.headlessTaskName, () => async () => {
      try {
        await action();
        await ExpoForegroundActionsModule.stopForegroundAction(true);  // automatic stop
        resolve();
      } catch (e) {
        await ExpoForegroundActionsModule.stopForegroundAction(true);  // automatic stop
        reject(e);
      }
    });
    await startForegroundAction(options);
  } catch (e) {
    reject(e);
    throw e;
  }
});

export const updateForegroundedAction = async (options: AndroidSettings): Promise<void> => {
  if (Platform.OS !== "android") return;
  return ExpoForegroundActionsModule.updateForegroundedAction(options);
};

export const stopForegroundAction = async (): Promise<void> => {
  await ExpoForegroundActionsModule.stopForegroundAction(false);  // manual stop
};

export const getServiceStatus = async (): Promise<ServiceStatus> => {
  if (Platform.OS === "ios") {
    const remaining = await ExpoForegroundActionsModule.getBackgroundTimeRemaining();
    return {
      isRunning: remaining > 0,
      remaining,
    };
  }
  if (Platform.OS === "android") {
    const isRunning = await ExpoForegroundActionsModule.isServiceRunning();
    return { isRunning };
  }
  return { isRunning: false };
};

export { ExpireEventPayload, ServiceStatus };

export function addExpirationListener(
  listener: (event: ExpireEventPayload) => void
): Subscription {
  return emitter.addListener("onExpirationEvent", listener);
}

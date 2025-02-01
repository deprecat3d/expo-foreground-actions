export type ExpireEventPayload = {
  remaining: number;
  identifier: number;
};

/**
 * Service types for Android foreground services.
 * Only supported on Android 10+ (API 29+).
 * Required on Android 14+ (API 34+) for apps targeting Android 14 or higher.
 * Defaults to 'dataSync' if not specified or on older Android versions.
 *
 * @see https://developer.android.com/about/versions/14/behavior-changes-14#fgs-types-required
 * @see https://developer.android.com/reference/android/app/Service#startForeground(int,%20android.app.Notification,%20int)
 */
export type ForegroundServiceType =
  | 'dataSync'
  | 'mediaPlayback'
  | 'location'
  | 'camera'
  | 'microphone'
  | 'phoneCall';

export interface AndroidSettings {
  headlessTaskName: string;
  notificationTitle: string;
  notificationDesc: string;
  notificationColor: string;
  notificationIconName: string;
  notificationIconType: string;
  notificationProgress: number;
  notificationMaxProgress: number;
  notificationIndeterminate: boolean;
  linkingURI: string;
  serviceType?: ForegroundServiceType;
}

export interface Settings {
  events?: {
    onIdentifier?: (identifier: number) => void;
  }
  runInJS?: boolean,
}

export interface ForegroundApi {
  headlessTaskName: string;
  identifier: number;
}

export type ForegroundAction<Params> = (params: Params, api: ForegroundApi) => Promise<void>;

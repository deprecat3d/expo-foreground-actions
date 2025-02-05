export type ExpireEventPayload = {
  remaining: number;
  identifier: number;
};

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
}

export interface Settings {
  events?: {
    onBeforeExpires?: () => Promise<void>;
  };
  runInJS?: boolean,
}

export interface ForegroundApi {
  headlessTaskName: string;
}

export type ForegroundAction<Params> = (params: Params, api: ForegroundApi) => Promise<void>;

export type ServiceStatus = {
  isRunning: boolean;
  /** Only available on iOS */
  remaining?: number;
}

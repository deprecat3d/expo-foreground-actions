<div align="center">
<h1 align="center">
<img src="https://github.com/Acetyld/expo-foreground-actions/blob/main/assets/logo.png" width="100" />
<br>EXPO-FOREGROUND-ACTIONS</h1>
<div style="font-style: italic">Running Foreground actions for Android/IOS</div>
<div style="opacity: 0.5;margin-top:10px;">Developed with the software and tools below.</div>

<p align="center">
<img src="https://img.shields.io/badge/JavaScript-F7DF1E.svg?style=flat-square&logo=JavaScript&logoColor=black" alt="JavaScript" />
<img src="https://img.shields.io/badge/TypeScript-3178C6.svg?style=flat-square&logo=TypeScript&logoColor=white" alt="TypeScript" />
<img src="https://img.shields.io/badge/React-61DAFB.svg?style=flat-square&logo=React&logoColor=black" alt="React" />
<img src="https://img.shields.io/badge/React Native-61DAFB.svg?style=flat-square&logo=React&logoColor=black" alt="React" />
<img src="https://img.shields.io/badge/Swift-F05138.svg?style=flat-square&logo=Swift&logoColor=white" alt="Swift" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF.svg?style=flat-square&logo=Kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Expo-000020.svg?style=flat-square&logo=Expo&logoColor=white" alt="Expo" />

</p>
<a href="https://www.npmjs.com/package/expo-foreground-actions">
  <img src="https://img.shields.io/npm/v/expo-foreground-actions?style=flat-square" alt="npm version">
</a>
<img src="https://img.shields.io/github/license/Acetyld/expo-foreground-actions?style=flat-square&color=5D6D7E" alt="GitHub license" />
<img src="https://img.shields.io/github/last-commit/Acetyld/expo-foreground-actions?style=flat-square&color=5D6D7E" alt="git-last-commit" />
<img src="https://img.shields.io/github/commit-activity/m/Acetyld/expo-foreground-actions?style=flat-square&color=5D6D7E" alt="GitHub commit activity" />
<img src="https://img.shields.io/github/languages/top/Acetyld/expo-foreground-actions?style=flat-square&color=5D6D7E" alt="GitHub top language" />
</div>

---

> **This is an updated and fixed version of the original package, which is no longer maintained. It contains breaking changes from the last published version of the original.**

## 📖 Table of Contents

- [📖 Table of Contents](#-table-of-contents)
- [📍 Overview](#-overview)
- [📦 Features](#-features)
- [📂 repository Structure](#-repository-structure)
- [🚀 Getting Started](#-getting-started)
    - [🔧 Installation](#-installation)
    - [🤖 How to use](#-how-to-use)
    - [🤖 Functions](#-functions)
    - [🤖 Interfaces](#-interfaces)
- [🛣 Roadmap](#-roadmap)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)
- [👏 Acknowledgments](#-acknowledgments)

---

## 📍 Overview

Start actions that continue to run in the grace period after the user switches apps. This library facilitates the
execution of **ios**'s `beginBackgroundTaskWithName` and **android**'s `startForegroundService` methods. The primary
objective is to emulate the behavior of `beginBackgroundTaskWithName`, allowing actions to persist even when the user
switches to another app. Examples include sending chat messages, creating tasks, or running synchronizations.

On iOS, the grace period typically lasts around 30 seconds, while on Android, foreground tasks can run for a longer
duration, subject to device models and background policies. In general, a foreground task can safely run for about 30
seconds on both platforms. However, it's important to note that this library is not intended for background location
tracking. iOS's limited 30-second window makes it impractical for such purposes. For background location tracking,
alternatives like WorkManager or GTaskScheduler are more suitable.

[OUT OF DATE] For usage instructions, please refer to
the [Example](https://github.com/Acetyld/expo-foreground-actions/tree/main/example) provided.

---

## 📦 Features

### For IOS & Android:

- Execute JavaScript while the app is in the background.
- Stop execution when you're finished (manually or automatically when the routine completes).

### For Android:

- Display a notification with customizable titles, descriptions, and optional progress bars, along with support for deep
  linking.
- Update the notification on the fly.
- Comply with the latest Android 34+ background policy: allows a foreground service to run without forcing a persistent, “in your face” notification on the status bar. Instead, a low‑priority (minimal) notification is used that only appears in the notification drawer if the user opens it.

### For IOS:

- Handle an event that emits when the background execution time is about to expire. Save data and terminate tasks gracefully.

### Web Support:

- This plugin is not intended for web platforms since it relies on native Android and iOS functionality. The `runInJS` mode is only provided as a fallback for older Android devices, and this setting is forced to true internally in that scenario.

---

## 🚀 Getting Started

***Dependencies***

Please ensure you have the following dependencies installed on your system:

`- ℹ️ Expo v51+`

`- ℹ️ Bare/Manage workflow, we do not support Expo GO`

### 🔧 Installation

1. Install the package:

  Add to `package.json` dependencies:
  ```json
  "expo-foreground-actions": "github:deprecat3d/expo-foreground-actions",
  ```

  Update packages:
   **NPM**
    ```sh
    npm install
    ```

   **Yarn**
    ```sh
    yarn install
    ```

2. Install the config plugin:

    To integrate Expo Foreground Actions using the Expo config plugin, add the plugin to your Expo configuration file (e.g. `app.json` or `app.config.js`). For example:

    ```json
    {
      "expo": {
        "plugins": [
          [
            "expo-foreground-actions",
            {
              "androidNotificationIcon": "./assets/notification.png"
            }
          ]
        ]
      }
    }
    ```

    **Notes:**

    - The path provided for `"androidNotificationIcon"` is relative to your project's root directory.
    - If you're using `expo-notifications`, our config plugin will fall back to the icon you specified in that plugin's config. Just ensure that `expo-notifications` config plugin is listed **before** this plugin in your plugins array in `app.json`.

### 🤖 Functions

#### `runForegroundedAction`

This function executes a foreground action and is intended to be invoked only from the global scope. Do not call it from within the React component tree.

```typescript
export const runForegroundedAction = async (
  act: (api: ForegroundApi) => Promise<void>,
  androidSettings: AndroidSettings,
  settings: Settings = { runInJS: false }
): Promise<void>;
```

- `act`: The foreground action function to be executed.
- `androidSettings`: Android-specific settings for the foreground action.
- `settings`: Additional settings for the foreground action.

**Note: Only one foreground action may run at a time. Starting a new action while one is active will fail.**

This was changed from an earlier version of the package due to a bug where some notification center items on Android would become inaccessible.

#### `stopForegroundAction`

```typescript
export const stopForegroundAction = async (): Promise<void>;
```

Stops the currently running foreground action (if any).

#### `updateForegroundedAction`

```typescript
export const updateForegroundedAction = async (
  options: AndroidSettings
): Promise<void>;
```

- `options`: Updated Android-specific settings for the foreground action.

Updates the notification settings of the currently running foreground action (Android only).

#### `getServiceStatus`

```typescript
export const getServiceStatus = async (): Promise<ServiceStatus>;
```

- Returns the status of the currently running foreground service (if any).

#### `addExpirationListener`

```typescript
export function addExpirationListener(
  listener: (event: ExpireEventPayload) => void
): Subscription;

// Example:
const unsubscribe = addExpirationListener((event) => {
  console.log("Expiration event received.", event);
});
unsubscribe?.remove();
```

- Adds an event listener for when the service is about to expire (iOS - you must stop your code before the service is ended or your app will be terminated - you can also use the onBeforeExpires hook in settings) or is manually or otherwise stopped.

### 🤖 Interfaces

#### `ExpireEventPayload`

```typescript
export type ExpireEventPayload = {
  remaining: number;
  identifier: number;
};
```

#### `ServiceStatus`

```typescript
export type ServiceStatus = {
  isRunning: boolean;
  /** Only available on iOS */
  remaining?: number;
}
```

- `remaining`: The remaining time in seconds before the foreground action expires.

#### `AndroidSettings`

```typescript
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
```

- `headlessTaskName`: Name of the headless task associated with the foreground action.
- `notificationTitle`: Title of the notification shown during the foreground action.
- `notificationDesc`: Description of the notification.
- `notificationColor`: Color of the notification.
- `notificationIconName`: Name of the notification icon.
- `notificationIconType`: Type of the notification icon.
- `notificationProgress`: Current progress value for the notification.
- `notificationMaxProgress`: Maximum progress value for the notification.
- `notificationIndeterminate`: Indicates if the notification progress is indeterminate.
- `linkingURI`: URI to link to when the notification is pressed.

#### `Settings`

```typescript
export interface Settings {
  events?: {
    onBeforeExpires?: () => Promise<void>;
  }
  runInJS?: boolean,
}
```

- `events`: Event handlers for foreground actions.
    - `onBeforeExpires`: A callback function called when the service is about to expire.
- `runInJS`: Indicates whether to run the foreground action without using a headless task or ios background task.

---

## 🤝 Contributing

Contributions are welcome! Here are several ways you can contribute:

- **[Submit Pull Requests](https://github.com/Acetyld/expo-foreground-actions/blob/main/CONTRIBUTING.md)**: Review open
  PRs, and submit your own PRs.
- **[Join the Discussions](https://github.com/Acetyld/expo-foreground-actions/discussions)**: Share your insights,
  provide feedback, or ask questions.
- **[Report Issues](https://github.com/Acetyld/expo-foreground-actions/issues)**: Submit bugs found or log feature
  requests for ACETYLD.

#### *Contributing Guidelines*

<details closed>
<summary>Click to expand</summary>

1. **Fork the Repository**: Start by forking the project repository to your GitHub account.
2. **Clone Locally**: Clone the forked repository to your local machine using a Git client.
   ```sh
   git clone <your-forked-repo-url>
   ```
3. **Create a New Branch**: Always work on a new branch, giving it a descriptive name.
   ```sh
   git checkout -b new-feature-x
   ```
4. **Make Your Changes**: Develop and test your changes locally.
5. **Commit Your Changes**: Commit with a clear and concise message describing your updates.
   ```sh
   git commit -m 'Implemented new feature x.'
   ```
6. **Push to GitHub**: Push the changes to your forked repository.
   ```sh
   git push origin new-feature-x
   ```
7. **Submit a Pull Request**: Create a PR against the original project repository. Clearly describe the changes and
   their motivations.

Once your PR is reviewed and approved, it will be merged into the main branch.

</details>

---

## 📄 License

This project is protected under the [MIT](https://choosealicense.com/licenses) License. For more details, refer to
the [LICENSE](https://choosealicense.com/licenses/) file.

---

## 👏 Acknowledgments

- Idea/inspiration from https://github.com/Rapsssito/react-native-background-actions
- [Expo](https://expo.dev) for providing a platform to build universal apps using React Native.
- [Benedikt](https://twitter.com/bndkt) for mentioning this package in the "thisweekinreact"
  newsletter: [Week 176](https://thisweekinreact.com/newsletter/176)

[**Return**](#Top)



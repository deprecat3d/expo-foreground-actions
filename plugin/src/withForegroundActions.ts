import { ConfigPlugin, withAndroidManifest, withDangerousMod, AndroidConfig } from '@expo/config-plugins';
import fs from 'fs';
import path from 'path';
import { generateImageAsync } from '@expo/image-utils';

const NOTIFICATION_ICON_NAME = 'notification_icon';

type PluginProps = {
  androidNotificationIcon?: string;
};

export const withForegroundActions: ConfigPlugin<PluginProps> = (config, { androidNotificationIcon } = {}) => {
  // First, update the Android manifest to ensure the proper permissions and service are registered.
  config = withAndroidManifest(config, async (config) => {
    const { Manifest } = AndroidConfig;
    const application = Manifest.getMainApplicationOrThrow(config.modResults);
    const service = Array.isArray(application.service) ? application.service : [];

    // List required permissions for foreground service operation
    const permissions = [
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
    ].map(permission => ({
      $: {
        'android:name': permission,
      },
    }));

    // Create an intent filter for the foreground service action.
    const intentFilter = {
      $: {},
      action: [
        {
          $: {
            'android:name': 'expo.modules.foregroundactions.ACTION_FOREGROUND_SERVICE.*',
          },
        },
      ],
    };

    // Merge the new permissions and service declaration with the existing manifest.
    config.modResults.manifest = {
      ...config.modResults.manifest,
      'uses-permission': [
        ...(config.modResults.manifest['uses-permission'] || []),
        ...permissions,
      ],
      application: [
        {
          ...application,
          service: [
            ...service,
            // Android manifest type does not recognize android:foregroundServiceType,
            // which is a required attribute for the foreground service.
            // See: https://developer.android.com/about/versions/14/changes/fgs-types-required
            ({
              $: {
                'android:name': 'expo.modules.foregroundactions.ExpoForegroundActionsService',
                'android:exported': 'false',
                'android:foregroundServiceType': 'dataSync',
              },
              'intent-filter': [intentFilter],
            }) as any,
          ],
        },
      ],
    };

    return config;
  });

  // Next, if the user has specified a custom notification icon, generate images from it.
  if (androidNotificationIcon) {
    config = withDangerousMod(config, [
      'android',
      async (config) => {
        // Use a fallback: if the user did not explicitly pass an icon, try the expo config value.
        const iconSource = androidNotificationIcon || (config.notification && config.notification.icon);
        if (!iconSource) {
          // If still not provided, do nothing.
          return config;
        }

        // Resolve the icon path relative to the project root (e.g. from the expo assets folder)
        const iconPath = path.resolve(config.modRequest.projectRoot, iconSource);
        if (!fs.existsSync(iconPath)) {
          throw new Error(`Could not find notification icon at "${iconSource}"`);
        }

        // Define desired dimensions for various densities.
        const densities: { [key: string]: number } = {
          mdpi: 24,
          hdpi: 36,
          xhdpi: 48,
          xxhdpi: 72,
          xxxhdpi: 96,
        };

        // Define the proper Android resources folder path.
        const androidResPath = path.join(config.modRequest.projectRoot, 'android/app/src/main/res');

        // For each density, generate the image into the appropriate drawable folder.
        for (const [density, size] of Object.entries(densities)) {
          const resourcePath = path.join(
            androidResPath,
            `drawable-${density}`,
            `${NOTIFICATION_ICON_NAME}.png`
          );
          await fs.promises.mkdir(path.dirname(resourcePath), { recursive: true });
          const imageResult = await generateImageAsync(
            { projectRoot: config.modRequest.projectRoot, cacheType: 'foreground-action' },
            {
              src: iconPath,
              width: size,
              height: size,
              backgroundColor: 'transparent', // match expo-notifications config
              resizeMode: 'contain',
            }
          );
          await fs.promises.writeFile(resourcePath, imageResult.source);
        }
        return config;
      }
    ]);
  }
  return config;
};

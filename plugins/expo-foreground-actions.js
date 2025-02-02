const { withAndroidManifest, AndroidConfig } = require("expo/config-plugins");
const { getMainApplicationOrThrow } = AndroidConfig.Manifest;

module.exports = function withBackgroundActions(config) {
  return withAndroidManifest(config, async (config) => {
    const application = getMainApplicationOrThrow(config.modResults);
    const service = application.service ? application.service : [];

    // Create new permissions array
    const permissions = [
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_DATA_SYNC'
    ].map(permission => ({
      $: {
        "android:name": permission
      }
    }));

    // Create intent filter for the service
    const intentFilter = {
      $: {},
      action: [
        {
          $: {
            "android:name": "expo.modules.foregroundactions.ACTION_FOREGROUND_SERVICE.*"
          }
        }
      ]
    };

    config.modResults = {
      manifest: {
        ...config.modResults.manifest,
        'uses-permission': [
          ...(config.modResults.manifest['uses-permission'] || []),
          ...permissions
        ],
        application: [
          {
            ...application,
            service: [
              ...service,
              {
                $: {
                  "android:name": "expo.modules.foregroundactions.ExpoForegroundActionsService",
                  "android:exported": "false",
                  "android:foregroundServiceType": "dataSync",
                },
                'intent-filter': [intentFilter]
              },
            ],
          },
        ],
      },
    };

    return config;
  });
};

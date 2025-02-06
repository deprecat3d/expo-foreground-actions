import { ConfigPlugin, createRunOncePlugin } from 'expo/config-plugins';
import { withForegroundActions } from './withForegroundActions';

const withExpoForegroundActions: ConfigPlugin<{ androidNotificationIcon?: string }> = (config, props) => {
  return withForegroundActions(config, props);
};

const pkg = require('../../package.json');

export default createRunOncePlugin(withExpoForegroundActions, pkg.name, pkg.version);

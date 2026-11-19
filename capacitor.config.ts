import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.orkeit.desktop',
  appName: 'Oriket Desktop',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
  },
};

export default config;

import type { ExpoConfig } from 'expo/config'

/**
 * Dev unless a build explicitly opts out.
 *
 * Defaulting to `true` is what makes a local `expo run:ios` produce a dev client without
 * anyone having to remember an env var; a production build sets `APP_VARIANT=production`.
 */
const IS_DEV = process.env.APP_VARIANT !== 'production'

const config: ExpoConfig = {
  name: 'rngui example',
  slug: 'rngui-example',
  version: '1.0.0',
  orientation: 'portrait',
  userInterfaceStyle: 'automatic',
  // No `newArchEnabled`: it is gone from SDK 57's config type because the new architecture is
  // the only architecture there. Fabric is on unconditionally, which is what this library
  // requires.
  scheme: 'rngui',
  ios: {
    bundleIdentifier: 'com.rngui.example',
    supportsTablet: true,
    deploymentTarget: '26.0',
  },
  android: {
    package: 'com.rngui.example',
  },
  plugins: [
    'expo-router',
    'expo-status-bar',
    [
      'expo-dev-client',
      {
        // Only register the generated `exp+rngui-example` scheme in dev builds, so a
        // production build doesn't advertise a development-client URL handler.
        addGeneratedScheme: !!IS_DEV,
      },
    ],
  ],
}

export default config

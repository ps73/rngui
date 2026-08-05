import { Platform } from 'react-native'
import SettingsAndroid from '../../../src/screens/SettingsAndroid'
import SettingsIOS from '../../../src/screens/SettingsIOS'

/**
 * Two rebuilds of "the Settings app", picked at runtime — because there are two Settings apps.
 *
 * **`Platform.select` rather than `index.ios.tsx` / `index.android.tsx`**, which is the more usual
 * React Native answer and is the wrong one here. Metro resolves platform extensions, but TypeScript
 * does not: `npm run typecheck` would silently check one variant and skip the other, and an example
 * app whose whole job is to be a compiled test of the public API cannot have half of it unchecked.
 * Both files are in the bundle, which for a demo costs nothing worth the trade.
 *
 * Same argument as `nav/screen-options.ts`, one level up: the platforms differ, so the code says so
 * in one place instead of every screen carrying a branch.
 */
export default Platform.OS === 'android' ? SettingsAndroid : SettingsIOS

/**
 * Empty platform entries, but the file is **required**.
 *
 * Without a `dependency` entry, React Native's autolinking never creates a Gradle project
 * for this package, so nothing under `android/` is compiled and the view manager is never
 * registered — with no error, the component simply doesn't exist at runtime. iOS gets away
 * without it because CocoaPods globs `node_modules` for podspecs independently.
 */
module.exports = {
  dependency: {
    platforms: {
      ios: {},
      android: {},
    },
  },
}

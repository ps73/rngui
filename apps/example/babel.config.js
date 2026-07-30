module.exports = function (api) {
  api.cache(true)

  return {
    presets: ['babel-preset-expo'],
    plugins: [
      // Unistyles rewrites StyleSheet.create call sites at build time; without this
      // plugin its themes and runtime don't take effect at all.
      [
        'react-native-unistyles/plugin',
        {
          root: 'src',
          // CollectionView takes plain colour objects rather than RN styles, so the
          // theme is read with `useUnistyles()` inside these files. The plugin has to
          // know they depend on the theme in order to re-render them when it changes.
          autoProcessImports: ['@rngui/collection-view'],
        },
      ],
      // Reanimated 4 moved its worklet transform into react-native-worklets.
      'react-native-worklets/plugin',
    ],
  }
}

// Must run before any component renders — it registers the Unistyles themes.
import '../src/unistyles'

import { StyleSheet, useColorScheme } from 'react-native'
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { useFonts } from 'expo-font'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import { INTER } from '../src/fonts'

/**
 * The root stack.
 *
 * Two invariants hold for this whole app, and both are easy to break by accident:
 *
 * 1. **No manual top inset anywhere.** Each tab's stack draws a transparent, blurred
 *    header, and UIKit insets the screen's scroll view for it and for the status bar.
 *    Padding by hand insets twice and stops content scrolling under the blur.
 *
 * 2. **No `SafeAreaProvider` here.** expo-router mounts one above the root layout; a
 *    second one reports zero insets to everything below it.
 *
 * 3. **The `ThemeProvider` is not decoration — the native header reads its colours.**
 *    React Navigation falls back to the light `DefaultTheme` unless something provides a
 *    theme, and native-stack hands `theme.colors.text` to react-native-screens as the title
 *    colour (`RNSScreenStackHeaderConfig.mm` puts it straight into
 *    `largeTitleTextAttributes`). `DefaultTheme.colors.text` is `rgb(28, 28, 30)`, so in dark
 *    mode every large title was drawn near-black on a near-black background — invisible, with
 *    nothing in the app's own code to suggest why. `DefaultTheme.colors.card` being white is
 *    the same bug's other face: it is what paints the large-title area light when
 *    `headerLargeStyle` does not force it transparent.
 *
 * 4. **Nothing renders until Inter has registered.** A `CollectionView.Root` asking for a family
 *    that is not yet registered falls back to the system font *and caches that resolution*, so a
 *    list mounted a frame early keeps the wrong face until something invalidates it. Gating here
 *    is one line; the alternative is a cache-busting mechanism that exists for no other reason.
 *
 * 5. **`GestureHandlerRootView` belongs here, once.** It is what gesture-handler documents, and
 *    the reason is mounting: per-screen roots come and go with the screens, and a sheet or a
 *    gesture detector mounted while its root is still settling has nothing to attach to. One at
 *    the top of the tree is created before any screen and outlives all of them.
 */
export default function RootLayout() {
  const scheme = useColorScheme()
  const [fontsLoaded, fontError] = useFonts(INTER)

  // `|| fontError` rather than `fontsLoaded` alone: a failed load leaves `loaded` false forever, so
  // gating on it by itself turns a missing font into a permanently blank app. Carrying on renders
  // the system face instead, which is what `FontResolver` falls back to anyway — and it logs.
  if (!fontsLoaded && !fontError) return null

  return (
    <GestureHandlerRootView style={styles.fill}>
      <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
        <StatusBar style="auto" />
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(tabs)" />
        </Stack>
      </ThemeProvider>
    </GestureHandlerRootView>
  )
}

const styles = StyleSheet.create({ fill: { flex: 1 } })

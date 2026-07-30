// Must run before any component renders — it registers the Unistyles themes.
import '../src/unistyles'

import { useColorScheme } from 'react-native'
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router'
import { StatusBar } from 'expo-status-bar'

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
 */
export default function RootLayout() {
  const scheme = useColorScheme()

  return (
    <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" />
      </Stack>
    </ThemeProvider>
  )
}

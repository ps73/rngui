// Must run before any component renders — it registers the Unistyles themes.
import '../src/unistyles'

import { Stack } from 'expo-router'
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
 */
export default function RootLayout() {
  return (
    <>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" />
      </Stack>
    </>
  )
}

import { StyleSheet } from 'react-native-unistyles'

/**
 * The example app's themes.
 *
 * These deliberately mirror UIKit's semantic colours rather than inventing a palette:
 * the whole point of the example is to sit next to a real iOS app and look the same, and
 * `@rngui/collection-view` falls back to the same system colours when a field is unset.
 * Naming them here is what lets the theming screens prove that an app-driven theme wins.
 */
const shared = {
  gap: (v: number) => v * 8,
} as const

const lightTheme = {
  ...shared,
  colors: {
    // systemGroupedBackground / secondarySystemGroupedBackground — the Settings look.
    background: '#F2F2F7',
    rowBackground: '#FFFFFF',
    // The inverted variant swaps these two.
    invertedBackground: '#FFFFFF',
    invertedRowBackground: '#F2F2F7',
    label: '#000000',
    secondaryLabel: 'rgba(60, 60, 67, 0.6)',
    separator: 'rgba(60, 60, 67, 0.29)',
    tint: '#007AFF',
    destructive: '#FF3B30',
  },
} as const

const darkTheme = {
  ...shared,
  colors: {
    background: '#000000',
    rowBackground: '#1C1C1E',
    invertedBackground: '#1C1C1E',
    invertedRowBackground: '#000000',
    label: '#FFFFFF',
    secondaryLabel: 'rgba(235, 235, 245, 0.6)',
    separator: 'rgba(84, 84, 88, 0.65)',
    tint: '#0A84FF',
    destructive: '#FF453A',
  },
} as const

type AppThemes = {
  light: typeof lightTheme
  dark: typeof darkTheme
}

declare module 'react-native-unistyles' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface UnistylesThemes extends AppThemes {}
}

StyleSheet.configure({
  themes: { light: lightTheme, dark: darkTheme },
  settings: {
    // Follow the system appearance by default. The theming screen overrides this at
    // runtime to prove an app-pinned theme reaches the native list.
    adaptiveThemes: true,
  },
})

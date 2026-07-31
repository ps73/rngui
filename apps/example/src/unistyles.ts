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

/**
 * The UIKit system palette, spelled out per interface style.
 *
 * Needed because a colour reaching `<CollectionView.Icon background>` crosses as one static hex —
 * unlike an `appearance` field, which crosses as a light/dark pair and becomes a
 * `UIColor(dynamicProvider:)`. Settings' tiles are `systemOrange`, `systemBlue` and friends, and
 * those genuinely differ between the two modes, so the pair has to live somewhere. Here it is a
 * theme, which means unistyles re-renders the screen on an appearance change and the new values
 * ride down with the next tree.
 *
 * That is a JavaScript render where the rest of the library needs none, and it is the honest cost
 * of a per-row colour the caller picked.
 */
const lightSystem = {
  red: '#FF3B30',
  orange: '#FF9500',
  yellow: '#FFCC00',
  green: '#34C759',
  mint: '#00C7BE',
  teal: '#30B0C7',
  cyan: '#32ADE6',
  blue: '#007AFF',
  indigo: '#5856D6',
  purple: '#AF52DE',
  pink: '#FF2D55',
  brown: '#A2845E',
  gray: '#8E8E93',
  graphite: '#48484A',
} as const

const darkSystem = {
  red: '#FF453A',
  orange: '#FF9F0A',
  yellow: '#FFD60A',
  green: '#30D158',
  mint: '#63E6E2',
  teal: '#40C8E0',
  cyan: '#64D2FF',
  blue: '#0A84FF',
  indigo: '#5E5CE6',
  purple: '#BF5AF2',
  pink: '#FF375F',
  brown: '#AC8E68',
  gray: '#8E8E93',
  graphite: '#8E8E93',
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
    system: lightSystem,
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
    system: darkSystem,
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

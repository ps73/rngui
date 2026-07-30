import { createContext, useContext } from 'react'
import { processColor } from 'react-native'
import type { Appearance, ListAppearance } from './tree'

/**
 * Normalises any colour React Native accepts into `#RRGGBBAA`.
 *
 * Done here rather than natively so that `'red'`, `'#f0f'`, `'rgba(0,0,0,.5)'` and
 * `hsl(...)` all work without reimplementing React Native's colour parser in Swift — and so
 * that whatever a theming library like unistyles resolves to is accepted for free.
 */
export function resolveColor(input: string | undefined): string | undefined {
  if (input == null) return undefined

  const processed = processColor(input)

  if (typeof processed !== 'number') {
    // `PlatformColor()` and `DynamicColorIOS()` come back as opaque objects rather than
    // numbers. They cannot survive a trip through a JSON string, and silently dropping them
    // would look like the colour was ignored — so say so. Use `appearance` /
    // `darkAppearance` for the same effect; that pair *is* the dynamic-colour mechanism here.
    if (__DEV__ && processed != null) {
      console.error(
        `[@rngui/collection-view] PlatformColor / DynamicColorIOS values are not supported ` +
          `in appearance. Set the colour in both \`appearance\` and \`darkAppearance\` instead.`
      )
    } else if (__DEV__) {
      console.error(
        `[@rngui/collection-view] Unrecognised colour: ${String(input)}`
      )
    }
    return undefined
  }

  // `processColor` hands back 0xAARRGGBB; the native side parses #RRGGBBAA.
  const alpha = (processed >>> 24) & 0xff
  const red = (processed >>> 16) & 0xff
  const green = (processed >>> 8) & 0xff
  const blue = processed & 0xff
  const hex = (value: number) => value.toString(16).padStart(2, '0')

  return `#${hex(red)}${hex(green)}${hex(blue)}${hex(alpha)}`
}

/** Appearance fields holding a colour, and therefore needing normalisation. */
const COLOR_KEYS = [
  'background',
  'rowBackground',
  'separator',
  'labelColor',
  'secondaryLabelColor',
  'headerTextColor',
  'footerTextColor',
  'tintColor',
] as const

/** Rewrites every colour in an appearance to `#RRGGBBAA`, dropping any that cannot parse. */
export function normalizeAppearance(
  appearance: Appearance | undefined
): Appearance | undefined {
  if (appearance == null) return undefined

  const out: Appearance = { ...appearance }
  for (const key of COLOR_KEYS) {
    const value = appearance[key]
    if (value == null) continue
    const resolved = resolveColor(value)
    // Dropped rather than passed through unparsed: an absent field falls back to the
    // platform's own colour, which is a better outcome than native failing to read it.
    if (resolved == null) delete out[key]
    else out[key] = resolved
  }
  return out
}

/**
 * The `inverted` look: a plain background with tinted rows.
 *
 * iOS's grouped default is the other way round — a tinted `systemGroupedBackground` behind
 * white `secondarySystemGroupedBackground` cards — so inverting means naming both explicitly.
 * Kept in JavaScript rather than as a native branch so it stays inspectable and overridable:
 * anything the caller sets in `appearance` wins over it, field by field.
 */
export const INVERTED_LIGHT: Appearance = {
  background: '#FFFFFF',
  rowBackground: '#F2F2F7',
}

export const INVERTED_DARK: Appearance = {
  background: '#000000',
  rowBackground: '#1C1C1E',
}

/** Caller values win over preset values, field by field. */
export function mergeAppearance(
  preset: Appearance | undefined,
  caller: Appearance | undefined
): Appearance | undefined {
  if (preset == null) return caller
  if (caller == null) return preset
  return { ...preset, ...caller }
}

/**
 * What `<CollectionView.Host>` children can read to match the list they sit in.
 *
 * The contract worth being precise about: **what comes back is what you configured, not what
 * native drew.** A field left unset is `undefined` here even though the cells around you are
 * rendered in some concrete platform colour, because that colour is resolved by UIKit against
 * the current trait collection and never travels back to JavaScript. Use it to align a custom
 * row with an *explicitly themed* list; fall back to your own theme otherwise.
 */
export interface InheritedAppearance {
  appearance: Appearance | undefined
  darkAppearance: Appearance | undefined
  listAppearance: ListAppearance
}

const AppearanceContext = createContext<InheritedAppearance>({
  appearance: undefined,
  darkAppearance: undefined,
  listAppearance: 'insetGrouped',
})

export const AppearanceProvider = AppearanceContext.Provider

export function useCollectionViewAppearance(): InheritedAppearance {
  return useContext(AppearanceContext)
}

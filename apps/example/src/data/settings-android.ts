/**
 * The Pixel Settings root screen, as data.
 *
 * **A different screen from `settings.ts`, not a translation of it.** The iOS file is a list of
 * titles with a coloured tile each, because that is what iOS Settings is; this one is a list of
 * titles *with a summary under each*, because that is what Pixel Settings is. Translating the first
 * into the second would have produced twenty single-line rows with Material icons — which is the
 * exact failure this package exists to argue against, and it would have looked almost right.
 *
 * The summaries are the point. Pixel Settings tells you what is inside a screen before you open it
 * ("Wi-Fi, mobile, data usage, hotspot"), and iOS Settings never does. Two lines is also the M3
 * list item people have actually seen: 72dp tall, a monochrome glyph, no chevron.
 *
 * Icons are Material Symbol names rather than SF Symbols. The mapped SF names would mostly have
 * worked, but "mostly" is how a screen ends up with the wrong glyph in three places — and these are
 * the names an Android developer would reach for anyway.
 */
export interface PixelSettingsRow {
  id: string
  title: string
  /** The second line: what you will find inside, which is the whole Pixel Settings idea. */
  summary: string
  /** A Material Symbol name, drawn bare and monochrome. */
  symbol: string
}

export interface PixelSettingsGroup {
  id: string
  rows: PixelSettingsRow[]
}

/**
 * Grouped rather than one continuous run.
 *
 * Pixel Settings was a single flat list for years and became grouped cards with Material 3 — which
 * is also what `androidListStyle="segmented"` draws, so the two agree without the screen having to
 * ask for anything unusual.
 */
export const PIXEL_SETTINGS_GROUPS: PixelSettingsGroup[] = [
  {
    id: 'connectivity',
    rows: [
      {
        id: 'network',
        title: 'Network & internet',
        summary: 'Wi-Fi, mobile, data usage, hotspot',
        symbol: 'wifi',
      },
      {
        id: 'connected',
        title: 'Connected devices',
        summary: 'Bluetooth, pairing',
        symbol: 'devices',
      },
      {
        id: 'apps',
        title: 'Apps',
        summary: 'Assistant, default apps, screen time',
        symbol: 'apps',
      },
      {
        id: 'notifications',
        title: 'Notifications',
        summary: 'Notification history, conversations',
        symbol: 'notifications',
      },
    ],
  },
  {
    id: 'device',
    rows: [
      {
        id: 'battery',
        title: 'Battery',
        summary: '78% — about 9 hr 30 min left',
        symbol: 'battery_full',
      },
      {
        id: 'storage',
        title: 'Storage',
        summary: '51% used — 62 GB free',
        symbol: 'storage',
      },
      {
        id: 'sound',
        title: 'Sound & vibration',
        summary: 'Volume, haptics, Do Not Disturb',
        symbol: 'volume_up',
      },
      {
        id: 'display',
        title: 'Display',
        summary: 'Dark theme, font size, brightness',
        symbol: 'display_settings',
      },
      {
        id: 'wallpaper',
        title: 'Wallpaper & style',
        summary: 'Wallpapers, themed icons, colours',
        symbol: 'wallpaper',
      },
    ],
  },
  {
    id: 'safety',
    rows: [
      {
        id: 'accessibility',
        title: 'Accessibility',
        summary: 'Display, interaction, audio',
        symbol: 'accessibility_new',
      },
      {
        id: 'security',
        title: 'Security & privacy',
        summary: 'App security, device lock, permissions',
        symbol: 'security',
      },
      {
        id: 'location',
        title: 'Location',
        summary: 'On — 3 apps have access to location',
        symbol: 'location_on',
      },
      {
        id: 'emergency',
        title: 'Safety & emergency',
        summary: 'Emergency SOS, medical info, alerts',
        symbol: 'emergency',
      },
    ],
  },
  {
    id: 'accounts',
    rows: [
      {
        id: 'passwords',
        title: 'Passwords & accounts',
        summary: 'Saved passwords, autofill, synced accounts',
        symbol: 'password',
      },
      {
        id: 'wellbeing',
        title: 'Digital Wellbeing & parental controls',
        summary: 'Screen time, app timers, bedtime schedules',
        symbol: 'hourglass_empty',
      },
      {
        id: 'google',
        title: 'Google',
        summary: 'Services & preferences',
        symbol: 'account_circle',
      },
    ],
  },
  {
    id: 'system',
    rows: [
      {
        id: 'system',
        title: 'System',
        summary: 'Languages, gestures, time, backup',
        symbol: 'phonelink_setup',
      },
      {
        id: 'update',
        title: 'System update',
        summary: 'Updated to Android 16',
        symbol: 'system_update',
      },
      {
        id: 'about',
        title: 'About phone',
        summary: 'Pixel 10',
        symbol: 'info',
      },
    ],
  },
]

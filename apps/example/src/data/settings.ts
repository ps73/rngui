/**
 * The iOS Settings root screen, as data.
 *
 * Separated from the screen because it is the *content* that makes the rebuild convincing —
 * twenty-odd rows in the right order with the right symbols and the right tile colours — and
 * because the search bar has to filter it, which is far easier over a list than over JSX.
 *
 * Tile colours are named rather than hex so they resolve per interface style: `systemOrange` is
 * not the same orange in dark mode, and a screen meant to sit beside the real Settings app has to
 * get that right. See the note on the palette in `src/unistyles.ts`.
 */
export type TileColor =
  | 'red'
  | 'orange'
  | 'yellow'
  | 'green'
  | 'mint'
  | 'teal'
  | 'cyan'
  | 'blue'
  | 'indigo'
  | 'purple'
  | 'pink'
  | 'brown'
  | 'gray'
  | 'graphite'

export interface SettingsRow {
  id: string
  title: string
  systemImage: string
  tile: TileColor
  /** The grey detail text on the trailing edge — Wi-Fi's network name, Bluetooth's "On". */
  value?: string
  /** Rows whose control is a switch rather than a disclosure, like Airplane Mode. */
  toggle?: boolean
}

export interface SettingsSection {
  id: string
  rows: SettingsRow[]
}

export const SETTINGS_SECTIONS: SettingsSection[] = [
  {
    id: 'connectivity',
    rows: [
      {
        id: 'airplane',
        title: 'Airplane Mode',
        systemImage: 'airplane',
        tile: 'orange',
        toggle: true,
      },
      {
        id: 'wifi',
        title: 'Wi-Fi',
        systemImage: 'wifi',
        tile: 'blue',
        value: 'Studio',
      },
      {
        id: 'bluetooth',
        title: 'Bluetooth',
        systemImage: 'antenna.radiowaves.left.and.right',
        tile: 'blue',
        value: 'On',
      },
      {
        id: 'cellular',
        title: 'Cellular',
        systemImage: 'antenna.radiowaves.left.and.right',
        tile: 'green',
      },
      {
        id: 'hotspot',
        title: 'Personal Hotspot',
        systemImage: 'personalhotspot',
        tile: 'green',
        value: 'Off',
      },
      { id: 'vpn', title: 'VPN', systemImage: 'network', tile: 'blue' },
    ],
  },
  {
    id: 'attention',
    rows: [
      {
        id: 'notifications',
        title: 'Notifications',
        systemImage: 'bell.badge.fill',
        tile: 'red',
      },
      {
        id: 'sounds',
        title: 'Sounds & Haptics',
        systemImage: 'speaker.wave.3.fill',
        tile: 'pink',
      },
      {
        id: 'focus',
        title: 'Focus',
        systemImage: 'moon.fill',
        tile: 'indigo',
      },
      {
        id: 'screen-time',
        title: 'Screen Time',
        systemImage: 'hourglass',
        tile: 'indigo',
      },
    ],
  },
  {
    id: 'device',
    rows: [
      {
        id: 'general',
        title: 'General',
        systemImage: 'gear',
        tile: 'gray',
      },
      {
        id: 'control-centre',
        title: 'Control Centre',
        systemImage: 'switch.2',
        tile: 'gray',
      },
      {
        id: 'display',
        title: 'Display & Brightness',
        systemImage: 'textformat.size',
        tile: 'blue',
      },
      {
        id: 'home-screen',
        title: 'Home Screen & App Library',
        systemImage: 'square.grid.2x2.fill',
        tile: 'indigo',
      },
      {
        id: 'search',
        title: 'Search',
        systemImage: 'magnifyingglass',
        tile: 'gray',
      },
      {
        id: 'wallpaper',
        title: 'Wallpaper',
        systemImage: 'photo.fill',
        tile: 'cyan',
      },
      {
        id: 'accessibility',
        title: 'Accessibility',
        systemImage: 'figure.wave.circle.fill',
        tile: 'blue',
      },
      {
        id: 'siri',
        title: 'Siri',
        systemImage: 'sparkles',
        tile: 'graphite',
      },
      {
        id: 'camera',
        title: 'Camera',
        systemImage: 'camera.fill',
        tile: 'gray',
      },
    ],
  },
  {
    id: 'privacy',
    rows: [
      {
        id: 'privacy',
        title: 'Privacy & Security',
        systemImage: 'hand.raised.fill',
        tile: 'blue',
      },
    ],
  },
  {
    id: 'services',
    rows: [
      {
        id: 'app-store',
        title: 'App Store',
        systemImage: 'arrow.down.app.fill',
        tile: 'blue',
      },
      {
        id: 'wallet',
        title: 'Wallet & Apple Pay',
        systemImage: 'creditcard.fill',
        tile: 'graphite',
      },
    ],
  },
  {
    id: 'apps',
    rows: [
      {
        id: 'passwords',
        title: 'Passwords',
        systemImage: 'key.fill',
        tile: 'gray',
      },
      { id: 'mail', title: 'Mail', systemImage: 'envelope.fill', tile: 'blue' },
      {
        id: 'contacts',
        title: 'Contacts',
        systemImage: 'person.crop.circle.fill',
        tile: 'brown',
      },
      {
        id: 'calendar',
        title: 'Calendar',
        systemImage: 'calendar',
        tile: 'red',
      },
      { id: 'notes', title: 'Notes', systemImage: 'note.text', tile: 'yellow' },
      {
        id: 'reminders',
        title: 'Reminders',
        systemImage: 'checklist',
        tile: 'orange',
      },
      {
        id: 'photos',
        title: 'Photos',
        systemImage: 'photo.stack.fill',
        tile: 'teal',
      },
      {
        id: 'health',
        title: 'Health',
        systemImage: 'heart.fill',
        tile: 'pink',
      },
    ],
  },
]

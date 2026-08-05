import { useMemo, useState } from 'react'
import { Stack } from 'expo-router'
import { CollectionView } from '@rngui/collection-view'
import { PIXEL_SETTINGS_GROUPS } from '../data/settings-android'

/**
 * The Pixel Settings root screen, rebuilt — the Android counterpart to `SettingsIOS`.
 *
 * **Not the iOS screen in Material colours, and the differences are the reason it exists.** Both
 * files rebuild "the Settings app", and they share almost nothing, because the two Settings apps
 * share almost nothing:
 *
 * ```
 *   iOS Settings                        Pixel Settings
 *   ┌──────────────────────────────┐    ┌──────────────────────────────┐
 *   │ ▨  Wi-Fi          Studio  ›  │    │ ◇  Network & internet        │
 *   ├──────────────────────────────┤    │    Wi-Fi, mobile, hotspot    │
 *   │ ▨  Bluetooth          On  ›  │    ├──────────────────────────────┤
 *   └──────────────────────────────┘    │ ◇  Connected devices         │
 *    coloured tile, one line,           │    Bluetooth, pairing        │
 *    trailing value, chevron            └──────────────────────────────┘
 *                                        bare glyph, two lines, no chevron
 * ```
 *
 * - **Two lines, not one.** Pixel tells you what is inside a screen before you open it. That single
 *   decision changes the row height from 44 to 72, moves the icon's vertical alignment, and is the
 *   thing that makes the screen recognisable from across a room.
 * - **A bare monochrome glyph, no container.** The coloured rounded tile is Apple's, invented in
 *   iOS 7 Settings. `background` is deliberately unset here — see the note on `imageBackground`,
 *   which resolves to a circle rather than a squircle on this platform precisely so that a screen
 *   that *does* want a container gets the local one.
 * - **No chevrons.** Android has never drawn a disclosure indicator on a list row; the row being
 *   tappable is communicated by the ripple.
 * - **No trailing values.** Where iOS puts "Studio" on the trailing edge, Pixel folds it into the
 *   summary line — "On — 3 apps have access to location".
 *
 * What the two screens *do* share is the tree API, which is the claim: the same `Section`/`Row`
 * vocabulary expresses both without either one bending toward the other.
 */
export default function SettingsScreenAndroid() {
  const [query, setQuery] = useState('')

  const groups = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (needle === '') return PIXEL_SETTINGS_GROUPS
    // Matched against the summary too, which is the point of having one: "hotspot" finds Network &
    // internet, and on Pixel it does.
    return PIXEL_SETTINGS_GROUPS.map((group) => ({
      ...group,
      rows: group.rows.filter(
        (row) =>
          row.title.toLowerCase().includes(needle) ||
          row.summary.toLowerCase().includes(needle)
      ),
    })).filter((group) => group.rows.length > 0)
  }, [query])

  return (
    <>
      {/*
        A toolbar search, which is what Android has. react-native-screens puts a `SearchView` on the
        toolbar, and the magnifier sits where every Android app puts it — as against iOS, where the
        search field is part of the navigation item and slides under the large title.
      */}
      <Stack.Screen
        options={{
          headerSearchBarOptions: {
            placeholder: 'Search settings',
            onChangeText: (event: { nativeEvent: { text: string } }) =>
              setQuery(event.nativeEvent.text),
            onClose: () => setQuery(''),
          },
        }}
      />

      <CollectionView.Root
        // Stated rather than inherited. `insetGrouped` already implies `segmented` on Android, but
        // this screen is *about* being an Android screen, and a reader should not have to know the
        // default mapping to see which arrangement it asked for.
        listAppearance="insetGrouped"
        androidListStyle="segmented"
      >
        {query.trim() === '' && (
          <CollectionView.Section id="account">
            <CollectionView.Row id="google-account" height={80} onPress={noop}>
              {/*
                A monogram avatar, which is what Google draws for an account with no photo — and the
                one leading element a symbol set cannot express, since the letters come from the
                row's own data rather than from a fixed vocabulary.
              */}
              <CollectionView.Icon monogram="PS" background="#5B6ABF" />
              <CollectionView.Label>Phil Schaffarzyk</CollectionView.Label>
              <CollectionView.Description>
                Google Account, backup, security
              </CollectionView.Description>
            </CollectionView.Row>
          </CollectionView.Section>
        )}

        {groups.map((group) => (
          <CollectionView.Section key={group.id} id={group.id}>
            {group.rows.map((row) => (
              <CollectionView.Row key={row.id} id={row.id} onPress={noop}>
                {/*
                  `materialSymbol` rather than `systemImage`. Most of these have an SF name that
                  would map, but "most" is how a screen ends up with the wrong glyph in three places
                  — and these are the names an Android developer would reach for anyway.

                  No `background`: bare and monochrome is what Pixel draws. `size` matches M3's 24dp
                  leading icon rather than the 22 this library defaults to.
                */}
                <CollectionView.Icon materialSymbol={row.symbol} size={24} />
                <CollectionView.Label>{row.title}</CollectionView.Label>
                <CollectionView.Description>
                  {row.summary}
                </CollectionView.Description>
              </CollectionView.Row>
            ))}
          </CollectionView.Section>
        ))}
      </CollectionView.Root>
    </>
  )
}

/** Shared rather than one closure per row; which row was tapped comes back as its id. */
function noop() {}

import { useMemo, useState } from 'react'
import { Stack } from 'expo-router'
import { CollectionView } from '@rngui/collection-view'
import { useUnistyles } from 'react-native-unistyles'
import { SETTINGS_SECTIONS } from '../data/settings'

/**
 * The iOS Settings root screen, rebuilt.
 *
 * Not a showcase of the library's features — a copy of a screen, which is a harder and more useful
 * test. Everything here is something Settings does, and therefore something the component has to do
 * the same way:
 *
 * - **The rounded coloured tiles.** `<CollectionView.Icon background>` draws a white glyph on a
 *   29pt continuous-corner square. Settings is unmistakable because of these, and the corner curve
 *   and the reserved layout width are the difference between a copy and an impression.
 * - **A real search bar in the header**, via `headerSearchBarOptions` — react-native-screens puts a
 *   `UISearchController` on the navigation item, and the list it filters stays entirely native.
 * - **An account row taller than the rest**, with a large glyph and two lines of text.
 * - **A switch on one row and a disclosure on the next**, inside one section, which is where a list
 *   that fakes its cells usually gives itself away.
 *
 * Deliberately the *first* tab, so it sits beside the `ScrollView`-based screens in the others: the
 * large title, the blur fade and the insets should be indistinguishable, and any difference is a
 * bug here.
 *
 * The one thing not copied is the account row's photo — Settings shows the user's picture, and an
 * SF Symbol is as close as this gets without an asset pipeline.
 */
export default function SettingsScreenIOS() {
  // Read here rather than only inside `StyleSheet.create`, because these colours are *props* on the
  // rows and have to cross into the tree. Unistyles re-renders this screen on an appearance change,
  // which is what gets the dark-mode palette down to the tiles — see the note in `unistyles.ts`.
  const { theme } = useUnistyles()
  const [airplane, setAirplane] = useState(false)
  const [query, setQuery] = useState('')

  const sections = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (needle === '') return SETTINGS_SECTIONS
    // Sections that lose every row are dropped rather than left as empty headed groups. An empty
    // rounded card with nothing in it reads as a bug, and it is not what Settings does.
    return SETTINGS_SECTIONS.map((section) => ({
      ...section,
      rows: section.rows.filter((row) =>
        row.title.toLowerCase().includes(needle)
      ),
    })).filter((section) => section.rows.length > 0)
  }, [query])

  return (
    <>
      {/*
        The search bar belongs to the navigation item rather than to the list, which is why it never
        scrolls away with the content and why `hideWhenScrolling` is UIKit's behaviour rather than
        something implemented here. Configured on the screen rather than in `_layout.tsx` because
        `onChangeText` has to reach this component's state.
      */}
      <Stack.Screen
        options={{
          headerSearchBarOptions: {
            placeholder: 'Search',
            onChangeText: (event) => setQuery(event.nativeEvent.text),
            onCancelButtonPress: () => setQuery(''),
            hideWhenScrolling: true,
          },
        }}
      />

      <CollectionView.Root>
        {/* Unheaded, as in Settings: the account is not a category, it is who you are. */}
        {query.trim() === '' && (
          <CollectionView.Section id="account">
            <CollectionView.Row
              id="apple-account"
              height={76}
              onPress={() => {}}
            >
              <CollectionView.Icon
                systemImage="person.crop.circle.fill"
                color={theme.colors.system.gray}
                size={52}
              />
              <CollectionView.Label>Phil Schaffarzyk</CollectionView.Label>
              <CollectionView.Description>
                Apple Account, iCloud, and more
              </CollectionView.Description>
              <CollectionView.Chevron />
            </CollectionView.Row>
          </CollectionView.Section>
        )}

        {sections.map((section) => (
          <CollectionView.Section key={section.id} id={section.id}>
            {section.rows.map((row) => (
              <CollectionView.Row
                key={row.id}
                id={row.id}
                // A switch row is not a link, so it gets no press target and therefore no highlight.
                // Settings behaves the same way: pressing the Airplane Mode row does nothing.
                onPress={row.toggle ? undefined : () => {}}
              >
                <CollectionView.Icon
                  systemImage={row.systemImage}
                  background={theme.colors.system[row.tile]}
                />
                <CollectionView.Label>{row.title}</CollectionView.Label>

                {row.toggle ? (
                  <CollectionView.Switch
                    value={airplane}
                    onValueChange={setAirplane}
                  />
                ) : (
                  <>
                    {row.value != null && (
                      <CollectionView.Value>{row.value}</CollectionView.Value>
                    )}
                    {row.badge != null && (
                      <CollectionView.Badge>{row.badge}</CollectionView.Badge>
                    )}
                    <CollectionView.Chevron />
                  </>
                )}
              </CollectionView.Row>
            ))}
          </CollectionView.Section>
        ))}
      </CollectionView.Root>
    </>
  )
}

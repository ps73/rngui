import { CollectionView } from '@rngui/collection-view'
import { Text, View } from 'react-native'
import { StyleSheet } from 'react-native-unistyles'

/**
 * Target: a near-1:1 rebuild of the iOS Settings app.
 *
 * Right now it exercises the descriptor pipeline end to end — sections with headers and
 * footers, the three stock cell presets, accessories, and one `Host` row holding a real React
 * Native view. Deliberately the *first* tab so it sits next to the `ScrollView`-based screens
 * in the other tabs: the large title, the blur fade and the top and bottom insets should be
 * indistinguishable between them, and any difference is a bug here.
 */
export default function SettingsScreen() {
  return (
    <CollectionView.Root>
      <CollectionView.Section
        header="Stock cells"
        footer="Three UIListContentConfiguration presets. The kind is inferred in JS from which slots a row fills, so there is never a second source of truth to disagree with the children."
      >
        <CollectionView.Row>
          <CollectionView.Label>Airplane Mode</CollectionView.Label>
        </CollectionView.Row>

        <CollectionView.Row onPress={() => {}}>
          <CollectionView.Label>Wi-Fi</CollectionView.Label>
          <CollectionView.Value>Not Connected</CollectionView.Value>
          <CollectionView.Chevron />
        </CollectionView.Row>

        <CollectionView.Row>
          <CollectionView.Label>Bluetooth</CollectionView.Label>
          <CollectionView.Description>
            Two devices connected
          </CollectionView.Description>
        </CollectionView.Row>

        <CollectionView.Row>
          <CollectionView.Label>Automatic</CollectionView.Label>
          <CollectionView.Checkmark />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        header="Hosted React child"
        footer="A real React Native view, reparented into the cell's contentView rather than floated over it — so UIKit clips it, hit-tests it and scrolls it with the list."
      >
        <CollectionView.Host height={120}>
          <View style={styles.hosted}>
            <Text style={styles.hostedLabel}>React Native child</Text>
          </View>
        </CollectionView.Host>
      </CollectionView.Section>

      {/*
        Long enough to actually scroll. Without this the screen is shorter than the viewport,
        and a large title that never collapses is indistinguishable from one that has nothing
        to collapse against. Mapped rather than written out so the `key`-derived row id
        fallback and cell recycling both get exercised.
      */}
      <CollectionView.Section header="Recycling">
        {Array.from({ length: 30 }, (_, i) => (
          <CollectionView.Row key={i}>
            <CollectionView.Label>{`Recycled row ${i + 1}`}</CollectionView.Label>
            <CollectionView.Value>{String(i + 1)}</CollectionView.Value>
          </CollectionView.Row>
        ))}
      </CollectionView.Section>
    </CollectionView.Root>
  )
}

const styles = StyleSheet.create((theme) => ({
  hosted: {
    flex: 1,
    backgroundColor: theme.colors.destructive,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hostedLabel: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '600',
  },
}))

import { useCallback, useState } from 'react'
import { Text, View } from 'react-native'
import BottomSheet from '@gorhom/bottom-sheet'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import { CollectionView } from '@rngui/collection-view'
import { BottomSheetCollectionView } from '@rngui/collection-view/bottom-sheet'
import { StyleSheet } from 'react-native-unistyles'

/**
 * A real `UICollectionView` inside a `@gorhom/bottom-sheet`.
 *
 * The hardest integration in the library, because a sheet and its list have to share one vertical
 * drag. Three things make that work, and all three are visible here:
 *
 * - **The list reports its scroll position** on every frame, so the sheet knows when the content
 *   is at the top and the next downward drag belongs to the sheet rather than the list.
 * - **The sheet can pin the list at the top mid-gesture**, through the native `scrollTo`
 *   command reached from a worklet — synchronously on the UI thread, not via a React commit.
 * - **The sheet drives `decelerationRate` to `0`** while collapsed, so a fling does not carry on
 *   underneath a sheet that has already started moving.
 *
 * The readout above the sheet is fed by the same events the sheet uses, so a frozen number means
 * the sheet has gone deaf — which is the failure this screen exists to catch.
 */
const SNAP_POINTS = ['35%', '92%']

export default function SheetScreen() {
  const [offsetY, setOffsetY] = useState(0)
  const [insetTop, setInsetTop] = useState(0)
  const [contentHeight, setContentHeight] = useState(0)

  // `{ nativeEvent }` rather than a bare payload: gorhom re-wraps the reanimated event before
  // handing it on. `BottomSheetCollectionViewProps` types it that way for exactly this reason.
  const handleScroll = useCallback(
    (event: {
      nativeEvent: {
        contentOffset: { y: number }
        contentInset: { top: number }
      }
    }) => {
      setOffsetY(Math.round(event.nativeEvent.contentOffset.y))
      setInsetTop(Math.round(event.nativeEvent.contentInset.top))
    },
    []
  )

  const handleContentSizeChange = useCallback((_: number, height: number) => {
    setContentHeight(Math.round(height))
  }, [])

  return (
    // Required by gesture-handler, and it has to be above the sheet rather than inside it: the
    // sheet's pan gesture and the list's own scrolling are coordinated through this view.
    <GestureHandlerRootView style={styles.fill}>
      <View style={styles.readout}>
        <Text style={styles.readoutLabel}>contentOffset.y</Text>
        <Text style={styles.readoutValue}>{offsetY}</Text>
        <Text style={styles.readoutLabel}>adjustedContentInset.top</Text>
        <Text style={styles.readoutValue}>{insetTop}</Text>
        <Text style={styles.readoutLabel}>contentSize.height</Text>
        <Text style={styles.readoutValue}>{contentHeight}</Text>
      </View>

      <BottomSheet
        snapPoints={SNAP_POINTS}
        index={0}
        // Off because the list is `flex: 1` and taller than any snap point — dynamic sizing is
        // for a sheet that should hug short content, and would fight a scroll view that wants all
        // the room it can get. `onContentSizeChange` is wired either way; the readout proves it.
        enableDynamicSizing={false}
      >
        <BottomSheetCollectionView
          onScroll={handleScroll}
          onContentSizeChange={handleContentSizeChange}
          appearance={{ tintColor: '#AF52DE', headerTextColor: '#AF52DE' }}
          darkAppearance={{ tintColor: '#BF5AF2', headerTextColor: '#BF5AF2' }}
        >
          <CollectionView.Section
            header="Drag me"
            footer="Drag down here with the list already at the top and the sheet collapses. Drag up and the list scrolls instead — the same finger, two different jobs, decided by whether contentOffset.y is 0."
          >
            {ROWS.map((row) => (
              <CollectionView.Row key={row} id={row} onPress={() => {}}>
                <CollectionView.Icon systemImage="circle.dashed" />
                <CollectionView.Label>{row}</CollectionView.Label>
                <CollectionView.Chevron />
              </CollectionView.Row>
            ))}
          </CollectionView.Section>

          <CollectionView.Section
            header="Still a real list"
            footer="Every row above is a recycled UICollectionViewListCell. Nothing about being inside a sheet changes that."
          >
            <CollectionView.Row id="swipe">
              <CollectionView.Label>Swipe actions</CollectionView.Label>
              <CollectionView.Description>
                Work here too — they are the cell&apos;s, not the sheet&apos;s
              </CollectionView.Description>
              <CollectionView.SwipeActions>
                <CollectionView.SwipeAction
                  id="delete"
                  title="Delete"
                  style="destructive"
                  onPress={() => {}}
                />
              </CollectionView.SwipeActions>
            </CollectionView.Row>
          </CollectionView.Section>
        </BottomSheetCollectionView>
      </BottomSheet>
    </GestureHandlerRootView>
  )
}

const ROWS = Array.from({ length: 40 }, (_, index) => `Row ${index + 1}`)

const styles = StyleSheet.create((theme) => ({
  fill: { flex: 1 },
  readout: {
    padding: 20,
    gap: 2,
  },
  readoutLabel: {
    fontSize: 12,
    fontVariant: ['tabular-nums'],
    color: theme.colors.secondaryLabel,
  },
  readoutValue: {
    fontSize: 28,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    marginBottom: 8,
    color: theme.colors.label,
  },
}))

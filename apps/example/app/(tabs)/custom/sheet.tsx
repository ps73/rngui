import { useCallback, useRef, useState } from 'react'
import { Text, View } from 'react-native'
import BottomSheet from '@gorhom/bottom-sheet'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
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
  const insets = useSafeAreaInsets()
  const [offsetY, setOffsetY] = useState(0)
  const [insetTop, setInsetTop] = useState(0)
  const [contentHeight, setContentHeight] = useState(0)

  /**
   * How many times the scroll direction has flipped, which is the only objective way to see a
   * judder from outside the device.
   *
   * A settling bounce reverses **once**: past the edge, then back. A list being fought over —
   * the sheet correcting an offset the scroll view is still animating — reverses every few
   * frames, and the count runs away. Whether it looks smooth is a matter of opinion; whether
   * this number jumps by two or by forty is not.
   */
  const [reversals, setReversals] = useState(0)
  const lastOffset = useRef(0)
  const lastDirection = useRef(0)

  // `{ nativeEvent }` rather than a bare payload: gorhom re-wraps the reanimated event before
  // handing it on. `BottomSheetCollectionViewProps` types it that way for exactly this reason.
  const handleScroll = useCallback(
    (event: {
      nativeEvent: {
        contentOffset: { y: number }
        contentInset: { top: number }
      }
    }) => {
      const y = event.nativeEvent.contentOffset.y
      // A dead band, so a sub-pixel wobble in a resting scroll view is not counted as motion.
      const delta = y - lastOffset.current
      if (Math.abs(delta) > 0.5) {
        const direction = Math.sign(delta)
        if (
          lastDirection.current !== 0 &&
          direction !== lastDirection.current
        ) {
          setReversals((count) => count + 1)
        }
        lastDirection.current = direction
        lastOffset.current = y
      }

      setOffsetY(Math.round(y))
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
      <BottomSheet
        snapPoints={SNAP_POINTS}
        index={0}
        // Off because the list is `flex: 1` and taller than any snap point — dynamic sizing is
        // for a sheet that should hug short content, and would fight a scroll view that wants all
        // the room it can get. `onContentSizeChange` is wired either way; the readout proves it.
        enableDynamicSizing={false}
        // The sheet, not the list, owns the surface. Gorhom's default is an opaque white card, so
        // a grouped list inside it reads as a grey panel floating on a white one — two surfaces
        // where iOS has one.
        backgroundStyle={styles.sheetBackground}
        // The list is the whole sheet, grabber included. Gorhom's handle is a *sibling* laid out
        // above the content, so keeping it would mean the list starts below it and content is
        // clipped at a hard edge rather than sliding under the grabber the way it does in Maps.
        // The grabber below is an overlay instead; dragging the list still moves the sheet, which
        // is what the handle's own pan gesture was for.
        handleComponent={null}
      >
        <BottomSheetCollectionView
          onScroll={handleScroll}
          onContentSizeChange={handleContentSizeChange}
          // A plain surface with tinted rows, rather than iOS's tinted surface with plain cards.
          // Inside a sheet this is the look that reads correctly: the sheet *is* the card, so a
          // second set of cards floating on a grey backdrop inside it is one card too many.
          inverted
          // Transparent so the sheet's rounded background shows through: the collection view is a
          // square opaque rectangle, and left opaque it paints its own corners over the sheet's.
          // This overrides the `inverted` preset's own background, which the sheet now supplies —
          // and it has to be repeated for dark mode, because the dark appearance deliberately does
          // not inherit the light one field by field.
          appearance={{
            background: 'transparent',
            tintColor: '#AF52DE',
            headerTextColor: '#AF52DE',
          }}
          darkAppearance={{
            background: 'transparent',
            tintColor: '#BF5AF2',
            headerTextColor: '#BF5AF2',
          }}
          // The sheet reaches the bottom of the screen, under a floating tab bar and the home
          // indicator, and `contentInsetAdjustmentBehavior` is pinned to `never` inside a sheet —
          // so nothing folds the safe area in on the list's behalf. Bottom only: a *top* inset
          // would move the list's resting offset off `0`, which is the value the sheet pins to.
          contentInset={{ bottom: insets.bottom }}
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

        {/*
          The grabber, floating over the list rather than laid out above it. A sibling of the
          scrollable inside the sheet's content, absolutely positioned and non-interactive — the
          drag it used to serve is handled by the list itself.
        */}
        <View pointerEvents="none" style={styles.grabber}>
          <View style={styles.grabberBar} />
        </View>
      </BottomSheet>

      {/*
        After the sheet rather than before it, so it stays legible at the tallest detent — the
        whole point is to read these while the sheet is covering the screen.
      */}
      <View pointerEvents="none" style={styles.readout}>
        <Text style={styles.readoutLabel}>contentOffset.y</Text>
        <Text style={styles.readoutValue}>{offsetY}</Text>
        <Text style={styles.readoutLabel}>direction reversals</Text>
        <Text style={styles.readoutValue}>{reversals}</Text>
        <Text style={styles.readoutLabel}>
          inset.top {insetTop} · content {contentHeight}
        </Text>
      </View>
    </GestureHandlerRootView>
  )
}

const ROWS = Array.from({ length: 40 }, (_, index) => `Row ${index + 1}`)

const styles = StyleSheet.create((theme) => ({
  fill: { flex: 1 },
  sheetBackground: {
    // The `inverted` background, since the sheet is the surface the list would otherwise paint
    // itself: plain white in light, near-black in dark, with the rows tinted against it.
    backgroundColor: theme.colors.invertedBackground,
  },
  grabber: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  grabberBar: {
    width: 36,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: theme.colors.separator,
  },
  readout: {
    position: 'absolute',
    top: 8,
    left: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 12,
    gap: 2,
    // The same tint the rows use, so the readout reads as a chip on the sheet rather than
    // disappearing into it — the sheet's own surface is now plain.
    backgroundColor: theme.colors.invertedRowBackground,
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

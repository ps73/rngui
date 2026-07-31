import { useCallback, useMemo, useState } from 'react'
import { Text, View } from 'react-native'
import { CollectionView, type VisibleRange } from '@rngui/collection-view'
import { StyleSheet } from 'react-native-unistyles'

/**
 * Windowing hosted rows, which is the one thing this library cannot do for you.
 *
 * Every stock row kind is a `UICollectionViewListCell` and UIKit pools it: two hundred of them
 * cost however many fit on screen. A `Host` row cannot join that pool — it is a distinct React
 * subtree with distinct state, and there is no such thing as an interchangeable one — so two
 * hundred hosted rows would be two hundred live subtrees, all mounted, all running their effects.
 *
 * `onVisibleRangeChange` is the way out. Native reports which rows are on screen, and JavaScript
 * renders children for those plus some overscan and `null` for the rest. The rows all still exist:
 * they keep their ids, their heights and their place in the scroll, so the scroll bar is honest and
 * nothing jumps. Only the *content* comes and goes.
 *
 * The counter is the whole point of the screen — scroll it and watch two hundred rows cost a dozen
 * subtrees.
 */
const ROW_COUNT = 200
const ROW_HEIGHT = 72

/**
 * Rows rendered beyond each edge of the visible range.
 *
 * Native reports the range *after* a cell has been asked for, so with no overscan the subtree for
 * a row mounts on the frame it appears — visibly late during a fast scroll. A few rows of slack
 * costs a few subtrees and buys the content being there before the row is.
 */
const OVERSCAN = 4

export default function WindowingScreen() {
  const [range, setRange] = useState<VisibleRange>({
    firstIndex: 0,
    lastIndex: 0,
  })

  // Coalesced natively to at most one call per run-loop turn, and only when the range actually
  // changed — so this is not a per-frame callback despite firing during a scroll.
  const handleVisibleRangeChange = useCallback((next: VisibleRange) => {
    setRange(next)
  }, [])

  const window = useMemo(() => {
    // The reported indices are into the flattened row list — every section's rows concatenated.
    // This screen has exactly one section so the mapping is the identity; a screen with more
    // would have to offset by the rows that come before.
    const first = Math.max(0, range.firstIndex - OVERSCAN)
    const last = Math.min(ROW_COUNT - 1, range.lastIndex + OVERSCAN)
    return { first, last, count: Math.max(0, last - first + 1) }
  }, [range])

  return (
    <View style={styles.fill}>
      <View style={styles.readout}>
        <Text style={styles.readoutLabel}>mounted React subtrees</Text>
        <Text style={styles.readoutValue}>
          {window.count} of {ROW_COUNT}
        </Text>
        <Text style={styles.readoutLabel}>
          visible rows {range.firstIndex}–{range.lastIndex} · window{' '}
          {window.first}–{window.last}
        </Text>
      </View>

      <CollectionView.Root
        appearance={{ tintColor: '#AF52DE', headerTextColor: '#AF52DE' }}
        darkAppearance={{ tintColor: '#BF5AF2', headerTextColor: '#BF5AF2' }}
        onVisibleRangeChange={handleVisibleRangeChange}
      >
        <CollectionView.Section
          header={`${ROW_COUNT} hosted rows`}
          footer="Each row is a real React subtree, not a recycled cell. Only the rows in the window render their children; the rest are empty cells holding their place, which is why the scroll never jumps."
        >
          {ROWS.map((row, index) => (
            <CollectionView.Host
              key={row.id}
              id={row.id}
              // Stated rather than measured, and windowing is exactly why: a row whose children
              // are `null` would measure as nothing and collapse, taking the scroll position with
              // it. Self-measurement is for content that is always there.
              height={ROW_HEIGHT}
            >
              {index >= window.first && index <= window.last ? (
                <HostedRow index={index} seed={row.seed} />
              ) : null}
            </CollectionView.Host>
          ))}
        </CollectionView.Section>
      </CollectionView.Root>
    </View>
  )
}

/**
 * Deliberately a real subtree rather than a `<Text>`: several views, a little layout, and state
 * of its own, so that mounting one is a cost worth avoiding two hundred times over.
 */
function HostedRow({ index, seed }: { index: number; seed: number }) {
  const bars = useMemo(
    () =>
      Array.from({ length: 12 }, (_, i) => 0.25 + ((seed + i * 37) % 60) / 80),
    [seed]
  )

  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>Row {index}</Text>
      <View style={styles.bars}>
        {bars.map((height, i) => (
          <View key={i} style={[styles.bar, { height: `${height * 100}%` }]} />
        ))}
      </View>
    </View>
  )
}

const ROWS = Array.from({ length: ROW_COUNT }, (_, index) => ({
  id: `row-${index}`,
  seed: index * 17,
}))

const styles = StyleSheet.create((theme) => ({
  fill: { flex: 1 },
  readout: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 4,
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
    color: theme.colors.label,
  },
  row: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
  },
  rowLabel: {
    width: 72,
    fontSize: 15,
    fontVariant: ['tabular-nums'],
    color: theme.colors.label,
  },
  bars: {
    flex: 1,
    height: 40,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 3,
  },
  bar: {
    flex: 1,
    borderRadius: 2,
    backgroundColor: '#AF52DE',
  },
}))

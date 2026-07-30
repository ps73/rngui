import { useCallback, useMemo, useRef, type ReactNode } from 'react'
import {
  StyleSheet,
  View,
  type NativeSyntheticEvent,
  type ViewProps,
} from 'react-native'
import NativeCollectionView from './specs/RNGUICollectionViewNativeComponent'
import { createRegistry, serialize } from './serialize'
import {
  AppearanceProvider,
  INVERTED_DARK,
  INVERTED_LIGHT,
  mergeAppearance,
  normalizeAppearance,
  type InheritedAppearance,
} from './appearance'
import type { Appearance, ListAppearance, Tree } from './tree'

export type ColorScheme = 'system' | 'light' | 'dark'

/**
 * The rows currently on screen, as inclusive indices into the flattened row list — every
 * section's rows concatenated in the order they were written.
 */
export interface VisibleRange {
  firstIndex: number
  lastIndex: number
}

export interface SectionIndexOptions {
  /**
   * Points of vertical space per letter. Omit for the system's own compact metric.
   *
   * The control is deliberately *not* stretched to fill the available height: the system one is a
   * compact block centred in the scroll view, and letters that drift apart on a tall screen stop
   * reading as a single object.
   */
  rowHeight?: number
  /** The magnified letter shown beside the finger while scrubbing. Defaults to true. */
  callout?: boolean
}

export interface RootProps extends Pick<ViewProps, 'style' | 'testID'> {
  /** `insetGrouped` is the iOS Settings look, and the default. */
  listAppearance?: ListAppearance

  /**
   * Colour, spacing and typography overrides. Anything left unset keeps the platform's own
   * value, which is already correct in both light and dark mode.
   */
  appearance?: Appearance

  /**
   * Overrides for dark mode, falling back to `appearance` field by field.
   *
   * Set only `appearance` and you get that look in both modes. Resolution happens natively
   * against the trait collection, so switching appearance restyles the list without a
   * JavaScript render.
   */
  darkAppearance?: Appearance

  /**
   * Swaps the grouped look: a plain background with tinted rows, rather than iOS's tinted
   * background with plain cards. A preset — anything set in `appearance` still wins.
   */
  inverted?: boolean

  /**
   * Pins the interface style rather than following the device.
   *
   * Needed whenever the app has its own theme switch: `UIListContentConfiguration` labels,
   * separators and accessories draw with *system* colours that follow the device, so a
   * theme that says "dark" on a light-mode phone would otherwise produce dark rows with
   * black text.
   */
  colorScheme?: ColorScheme

  /**
   * The A–Z scrubber down the trailing edge, built from each section's `indexTitle`.
   *
   * `true` for the defaults, or an object to tune it. Sections without an `indexTitle` are skipped
   * rather than given a blank stop, and when there is not enough vertical room for every letter the
   * bar thins itself out with `•` separators the way the system one does — each remaining stop
   * still scrolling to a real section.
   */
  sectionIndex?: boolean | SectionIndexOptions

  /**
   * The vertical scroll indicator. Defaults to true, as `ScrollView` does.
   *
   * Usually turned off alongside `sectionIndex`, since the scrubber already occupies that edge.
   */
  showsVerticalScrollIndicator?: boolean

  /**
   * Reports which rows are on screen, so `Host` children can be windowed.
   *
   * Needed only for lists of hosted rows: those are real React subtrees and cannot be recycled,
   * so a long one has to render children for the visible range plus overscan and nothing else.
   * Every stock row kind is pooled by UIKit and needs none of this.
   *
   * Coalesced natively to at most one call per run-loop turn, and only when the range actually
   * changed. Passing no callback disables the tracking entirely rather than merely ignoring it.
   */
  onVisibleRangeChange?: (range: VisibleRange) => void

  children?: ReactNode
}

/**
 * Monotonic, module-scoped.
 *
 * Native gates all decoding on `revision` changing, so the only property that has to hold is
 * that a new tree never reuses a previous number. A counter guarantees that; deriving it from
 * the content would risk a collision, and a collision means a silently stale list.
 * Recomputing with an unchanged tree merely costs one redundant decode.
 */
let nextRevision = 1

/**
 * The horizontal inset UIKit gives an inset-grouped card, per side.
 *
 * **Measured, not documented.** UIKit does not expose this, and it is not the 20pt the older
 * grouped `UITableView` used: on a 402pt iPhone the card comes out 370pt, i.e. 16pt per side.
 * Guessing 20 laid hosted children out 8pt too narrow, which distorted their right edge.
 *
 * Only hosted children care, because they are the one thing Yoga measures *here* while UIKit
 * positions it *there*. The mismatch is not silent: `HostCell` compares the two widths and logs the
 * exact delta in debug builds, so a device or size class where this constant is wrong reports
 * itself. Replacing the constant with a width reported back from native is the real fix, and is
 * scheduled alongside making `Host`'s `height` optional.
 */
const GROUPED_CARD_MARGIN = 16

/**
 * A real `UICollectionView`.
 *
 * The children are never mounted — `Section`, `Row` and the slot components all render `null`
 * and exist only to be read. This walks them into a flat descriptor tree, hands that across as
 * one JSON string, and lets UIKit build and recycle its own cells.
 *
 * `Host` children are the single exception: those are rendered here so Fabric mounts them, and
 * native reparents each into the cell that owns it.
 */
export function Root({
  style,
  listAppearance,
  appearance,
  darkAppearance,
  inverted = false,
  colorScheme,
  sectionIndex,
  showsVerticalScrollIndicator = true,
  onVisibleRangeChange,
  children,
  ...rest
}: RootProps) {
  const serialized = useMemo(() => {
    const registry = createRegistry()
    return { registry, ...serialize(children, registry) }
  }, [children])

  const resolved = useMemo(() => {
    // The dark side deliberately does *not* inherit `appearance` here — native falls back
    // field by field, so merging in JavaScript would make every light value an explicit dark
    // value and defeat that.
    const light = normalizeAppearance(
      mergeAppearance(inverted ? INVERTED_LIGHT : undefined, appearance)
    )
    const dark = normalizeAppearance(
      mergeAppearance(inverted ? INVERTED_DARK : undefined, darkAppearance)
    )
    return { light, dark }
  }, [appearance, darkAppearance, inverted])

  const { json, revision } = useMemo(() => {
    const tree: Tree = { sections: serialized.sections }
    // Omitted rather than set to undefined: `JSON.stringify` drops undefined keys anyway, but
    // being explicit keeps the payload minimal and the intent obvious.
    if (listAppearance != null) tree.listAppearance = listAppearance
    if (resolved.light != null) tree.appearance = resolved.light
    if (resolved.dark != null) tree.darkAppearance = resolved.dark

    return { json: JSON.stringify(tree), revision: nextRevision++ }
  }, [serialized.sections, listAppearance, resolved])

  // The horizontal inset UIKit gives a grouped section's card on iPhone. A constant because it is
  // UIKit's metric, not ours, and it is not exposed anywhere queryable.
  const hostedMargin = listAppearance === 'plain' ? 0 : GROUPED_CARD_MARGIN

  // Unwrapped here rather than in native so the public callback takes a plain object and the
  // `nativeEvent` shape stays an implementation detail.
  const handleVisibleRangeChange = useCallback(
    (event: NativeSyntheticEvent<VisibleRange>) => {
      onVisibleRangeChange?.(event.nativeEvent)
    },
    [onVisibleRangeChange]
  )

  /**
   * Row events, dispatched from the registry built alongside the descriptors.
   *
   * The registry is read through a ref rather than captured, and that matters: these handlers are
   * installed on the native view once, but the registry is rebuilt on every render. Capturing it
   * would mean a stale closure calling last render's `setState` — which usually still works, and
   * then silently stops when a handler starts closing over something that changed.
   *
   * Every callback is looked up by row id. A missing entry is not an error: native reports what
   * the user did, and a row is free to have no handler for it.
   */
  const registryRef = useRef(serialized.registry)
  registryRef.current = serialized.registry

  const handlers = useMemo(
    () => ({
      onRowPress: (event: NativeSyntheticEvent<{ rowId: string }>) => {
        registryRef.current.press.get(event.nativeEvent.rowId)?.()
      },
      onSwitchChange: (
        event: NativeSyntheticEvent<{ rowId: string; value: boolean }>
      ) => {
        const { rowId, value } = event.nativeEvent
        registryRef.current.switchChange.get(rowId)?.(value)
      },
      onTextChange: (
        event: NativeSyntheticEvent<{ rowId: string; value: string }>
      ) => {
        const { rowId, value } = event.nativeEvent
        registryRef.current.textChange.get(rowId)?.(value)
      },
      onFocusChange: (
        event: NativeSyntheticEvent<{ rowId: string; focused: boolean }>
      ) => {
        const { rowId, focused } = event.nativeEvent
        registryRef.current.focusChange.get(rowId)?.(focused)
      },
      onDateChange: (
        event: NativeSyntheticEvent<{ rowId: string; millis: number }>
      ) => {
        const { rowId, millis } = event.nativeEvent
        registryRef.current.dateChange.get(rowId)?.(millis)
      },
      onMenuSelect: (
        event: NativeSyntheticEvent<{ rowId: string; itemId: string }>
      ) => {
        const { rowId, itemId } = event.nativeEvent
        registryRef.current.menuSelect.get(rowId)?.(itemId)
      },
      onSwipeAction: (
        event: NativeSyntheticEvent<{ rowId: string; actionId: string }>
      ) => {
        const { rowId, actionId } = event.nativeEvent
        registryRef.current.swipeAction.get(rowId)?.[actionId]?.()
      },
    }),
    []
  )

  const inherited = useMemo<InheritedAppearance>(
    () => ({
      appearance: resolved.light,
      darkAppearance: resolved.dark,
      listAppearance: listAppearance ?? 'insetGrouped',
    }),
    [resolved, listAppearance]
  )

  return (
    <AppearanceProvider value={inherited}>
      <NativeCollectionView
        {...rest}
        // `flex: 1` by default: this is a scroll view, and every screen that uses one wants it
        // to fill its parent. Callers can still override through `style`.
        style={[styles.fill, style]}
        tree={json}
        revision={revision}
        colorScheme={colorScheme}
        showsSectionIndex={sectionIndex !== false && sectionIndex != null}
        // `0` is the sentinel for "automatic" — codegen has no representation of an absent number,
        // so a scalar prop cannot be optional at the native boundary the way a tree field can.
        sectionIndexRowHeight={
          typeof sectionIndex === 'object' ? (sectionIndex.rowHeight ?? 0) : 0
        }
        showsVerticalScrollIndicator={showsVerticalScrollIndicator}
        sectionIndexShowsCallout={
          typeof sectionIndex === 'object'
            ? sectionIndex.callout !== false
            : true
        }
        // Native has no way to ask whether JavaScript is listening — a Fabric event emitter is
        // always installed and always dispatches — so the answer has to be sent.
        tracksVisibleRange={onVisibleRangeChange != null}
        onVisibleRangeChange={handleVisibleRangeChange}
        {...handlers}
      >
        {serialized.hosted.map((child, index) => (
          <View
            key={index}
            // Guarantees exactly one native view per host, in a mount order that matches
            // `RowSpec.hostIndex`. Without it React Native is free to flatten the wrapper away
            // and the indices stop lining up.
            collapsable={false}
            style={[
              styles.hosted,
              {
                height: child.height,
                // Yoga lays this subtree out here, against the collection view's full width, but
                // the cell it ends up in is inset by the grouped style's margin. The native side
                // corrects the wrapper's own frame, which is not enough: without matching the
                // inset here, everything *inside* is measured for a container ~40pt too wide, so
                // a row of chips or a two-column layout lands subtly wrong.
                marginHorizontal: hostedMargin,
              },
            ]}
          >
            {child.node}
          </View>
        ))}
      </NativeCollectionView>
    </AppearanceProvider>
  )
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  // Taken out of flow: these are parked in the container until a cell claims one, and from then
  // on the cell owns the frame. Left in flow they would stack up and push the real content down.
  hosted: { position: 'absolute', left: 0, right: 0 },
})

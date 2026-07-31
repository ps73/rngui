import {
  SCROLLABLE_TYPE,
  createBottomSheetScrollableComponent,
} from '@gorhom/bottom-sheet'
import Animated from 'react-native-reanimated'
import { Root, type RootProps, type ScrollMetrics } from './CollectionView'

/**
 * Props gorhom hands to every scrollable, which mean nothing here.
 *
 * Most are `ScrollView`'s Android or refresh-control surface. They are dropped explicitly rather
 * than spread onto the native view, where they would be silently discarded at the Fabric boundary
 * and leave nothing to read when someone wonders why `refreshControl` did nothing.
 */
const IGNORED_SCROLLABLE_PROPS = [
  'overScrollMode',
  'refreshing',
  'onRefresh',
  'refreshControl',
  'progressViewOffset',
  'contentContainerStyle',
  'scrollEventThrottle',
  'setContentSize',
  'focusHook',
  'scrollEventsHandlersHook',
  'enableFooterMarginAdjustment',
] as const

/**
 * Adapts gorhom's `ScrollView`-shaped prop bag onto `RootProps`.
 *
 * Typed loosely on purpose: what arrives here is the caller's `RootProps` *plus* whatever
 * `createBottomSheetScrollableComponent` injects, and that second half has no exported type.
 * The looseness is contained to this one function — the exported component is typed properly.
 */
function BottomSheetCollectionViewAdapter(
  props: RootProps & Record<string, unknown>
) {
  const forwarded: Record<string, unknown> = {}
  for (const key of Object.keys(props)) {
    if (!(IGNORED_SCROLLABLE_PROPS as readonly string[]).includes(key)) {
      forwarded[key] = props[key as keyof typeof props]
    }
  }

  return (
    <Root
      // Not the library's default, which is `automatic` so a large title has something to
      // collapse against. Inside a sheet there is no navigation bar to fold in, and `automatic`
      // would still fold in the safe area — which shifts `contentOffset` so that the top of the
      // list reads as a negative number. Gorhom pins the list by calling `scrollTo(0, 0)`, so any
      // offset between "the top" and `0` is a list that jumps by exactly that much every time the
      // sheet locks it.
      contentInsetAdjustmentBehavior="never"
      // A reanimated handler is attached by view tag, so there is no prop for `Root` to infer
      // this from. Without it the sheet receives no scroll events at all and never unlocks.
      tracksScroll
      {...(forwarded as RootProps)}
    />
  )
}

const AnimatedCollectionView = Animated.createAnimatedComponent(
  BottomSheetCollectionViewAdapter
)

export interface BottomSheetCollectionViewProps extends Omit<
  RootProps,
  'onScroll' | 'ref' | 'tracksScroll'
> {
  /**
   * Scroll position, on every frame.
   *
   * **Wrapped in `{ nativeEvent }`, unlike `Root`'s own `onScroll`, which is already unwrapped.**
   * That is not a choice made here: gorhom intercepts the prop, runs the sheet's worklet with it
   * first, and then hands it to you re-wrapped in `ScrollView` shape from the UI thread. The type
   * says so rather than letting it be discovered at runtime.
   */
  onScroll?: (event: { nativeEvent: ScrollMetrics }) => void
  onScrollBeginDrag?: (event: { nativeEvent: ScrollMetrics }) => void
  onScrollEndDrag?: (event: { nativeEvent: ScrollMetrics }) => void
}

/**
 * `<CollectionView.Root>` as a `@gorhom/bottom-sheet` scrollable.
 *
 * A separate entry point rather than part of the main one, and that is not tidiness: importing this
 * pulls in `@gorhom/bottom-sheet`, `react-native-reanimated` and `react-native-gesture-handler`.
 * All three are optional peers, so the cost has to fall on the import rather than on everyone who
 * ever renders a list.
 *
 * ```tsx
 * <BottomSheet snapPoints={['40%', '90%']}>
 *   <BottomSheetCollectionView>
 *     <CollectionView.Section header="Options">…</CollectionView.Section>
 *   </BottomSheetCollectionView>
 * </BottomSheet>
 * ```
 *
 * Three things have to be true for a sheet and a list to share one vertical drag, and this exists
 * to make all three true at once:
 *
 * 1. **The sheet sees every scroll event.** Gorhom attaches a reanimated
 *    `useAnimatedScrollHandler`, which subscribes by *view tag* rather than by passing a prop —
 *    hence `tracksScroll`, since nothing else could infer that anyone is listening.
 * 2. **The sheet can pin the list at the top mid-gesture**, through the native `scrollTo` command
 *    reached from a worklet: synchronously on the UI thread, not through a React commit.
 * 3. **`contentOffset` reads `0` at the top**, which is the value gorhom pins to — see the note on
 *    `contentInsetAdjustmentBehavior` above.
 *
 * `SCROLLABLE_TYPE.SCROLLVIEW` because that is what this is from the sheet's side: one scrollable
 * region with a content offset, not a virtualized list whose windowing gorhom would try to reason
 * about.
 *
 * **Known gap.** Collapsing the sheet by dragging down on a list already at the top leaves the last
 * `onScroll` payload reporting the rubber-banded offset rather than the `0` the list actually
 * settles at, until the next real scroll. The list itself is correct — verified by pixel-diffing
 * both states — and the sheet is unaffected because it reads its own animated state. A parallax
 * header driven off `onScroll` would be briefly wrong. See the note on `emit` in
 * `RNGUICollectionViewHost.swift` for the two fixes that were tried and did not work.
 */
export const BottomSheetCollectionView = createBottomSheetScrollableComponent<
  never,
  BottomSheetCollectionViewProps
>(SCROLLABLE_TYPE.SCROLLVIEW, AnimatedCollectionView)

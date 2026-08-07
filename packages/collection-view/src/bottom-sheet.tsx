import { useState } from 'react'
import {
  SCROLLABLE_STATUS,
  SCROLLABLE_TYPE,
  createBottomSheetScrollableComponent,
  useBottomSheetInternal,
} from '@gorhom/bottom-sheet'
import Animated, { runOnJS, useAnimatedReaction } from 'react-native-reanimated'
import { Root, type RootProps, type ScrollMetrics } from './CollectionView'

/**
 * Props gorhom hands to every scrollable, which mean nothing here.
 *
 * Most are `ScrollView`'s Android surface. They are dropped explicitly rather than spread onto the
 * native view, where they would be silently discarded at the Fabric boundary and leave nothing to
 * read when someone wonders why one of them did nothing.
 *
 * **`refreshControl` is the one that is real on `Root` and still dropped here, and that is a
 * decision rather than an omission.** Inside a sheet the pull and the sheet's own collapse are the
 * same gesture: at `contentOffset 0` the list cannot scroll up, so `wouldScroll()` answers false,
 * `NativeViewGestureHandler` never activates, and the sheet keeps the drag. Below the tallest
 * detent gorhom locks the list outright — `scrollEnabled: false`, which switches the refresh layout
 * off on Android and stops the rubber-band on iOS. A `refreshControl` forwarded here would
 * therefore be a control that renders and never fires, which is worse than one that was never
 * accepted. `@gorhom/bottom-sheet` has the same conflict with a plain `BottomSheetScrollView`.
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
  const scrollEnabled = useLockedBySheet()

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
      //
      // The cost is that nothing folds the *bottom* safe area in either, so a sheet that reaches
      // the screen edge will run its last rows under the home indicator and any floating tab bar.
      // Pass `contentInset={{ bottom }}` for that. It has to be the caller's call rather than a
      // default here: only the app knows whether a tab bar is in the way, and this package does
      // not depend on react-native-safe-area-context. A *top* inset is the one thing to avoid —
      // it moves the resting offset off `0`, which is the value the sheet pins to.
      contentInsetAdjustmentBehavior="never"
      // A reanimated handler is attached by view tag, so there is no prop for `Root` to infer
      // this from. Without it the sheet receives no scroll events at all and never unlocks.
      tracksScroll
      scrollEnabled={scrollEnabled}
      {...(forwarded as RootProps)}
    />
  )
}

/**
 * `false` for as long as the sheet, not the list, owns the vertical drag.
 *
 * **This is the fix for a judder, and the judder came from an assumption gorhom makes that cannot
 * hold here.** Gorhom locks a list by leaving it scrollable and yanking the offset back to the top
 * on every scroll event, which works because react-native-gesture-handler makes the scrollable's
 * own pan recognizer *simultaneous* with the sheet's — and it arranges that by reaching inside an
 * `RCTScrollViewComponentView` for its `UIScrollView` (`RNGestureHandler.mm`, one hard
 * `isKindOfClass:` with no extension point). This component is not one, so both gestures activate
 * independently: the list scrolls under the finger while the sheet moves, and gorhom's correction
 * chases it every frame. Measured at ten direction reversals in a single half-second drag.
 *
 * Removing the second gesture rather than correcting it afterwards costs nothing gorhom relies on:
 * it already treats a locked list as one that must not move, and it still gets its scroll events
 * the moment the sheet reaches its tallest detent and unlocks.
 *
 * A React state update rather than an animated prop, because this changes on lock transitions —
 * a handful of times per gesture — not per frame. `animatedScrollableStatus` is the same value
 * gorhom drives `decelerationRate` from, so the two can never disagree about what is locked.
 */
function useLockedBySheet(): boolean {
  const { animatedScrollableStatus } = useBottomSheetInternal()
  const [enabled, setEnabled] = useState(true)

  useAnimatedReaction(
    () => animatedScrollableStatus.value === SCROLLABLE_STATUS.UNLOCKED,
    (next, previous) => {
      if (next !== previous) runOnJS(setEnabled)(next)
    },
    [animatedScrollableStatus]
  )

  return enabled
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

// From the package root, not `react-native/Libraries/Utilities/codegenNativeComponent`: deep
// imports are deprecated and Metro warns about them at bundle time. The `Int32` import below
// stays on its deep path because it is type-only and therefore erased — nothing reaches the
// bundler to warn about.
import { codegenNativeComponent, codegenNativeCommands } from 'react-native'

import type { HostComponent, ViewProps } from 'react-native'
import type {
  DirectEventHandler,
  Double,
  Float,
  Int32,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes'

/**
 * The native contract for the collection view.
 *
 * Two rules shape this file, and both come from measuring what React Native's codegen
 * actually does rather than from what it looks like it should do:
 *
 * 1. **The section/row tree crosses as one JSON string, not as typed props.** Codegen does
 *    support objects nested inside array elements, so that is not the reason. The reasons
 *    are that a string enum inside an array-element struct emits a reference to an
 *    `enum class` that is never defined (so `Props.h` will not compile), that enum type
 *    names are not path-namespaced so two structs with a same-named field collide, that
 *    there is no representation of "absent" at any depth (`height?: Double` becomes
 *    `double height{0.0}`, making "self-size me" indistinguishable from zero), and that
 *    every generated struct decoder casts a `jsi` object to
 *    `std::unordered_map<std::string, RawValue>` — which for a 10k-row list means hundreds
 *    of thousands of string allocations and hash lookups on every commit that touches the
 *    tree. A single string crosses as one `jsi::String` and is decoded once by
 *    `JSONDecoder`.
 *
 * 2. **Everything hot, scalar or cheap to diff stays a typed prop.** Those get real
 *    diffing and real defaults, so toggling a boolean or nudging an inset never re-encodes
 *    the tree.
 *
 * Note that codegen requires an enum prop to be *both* optional and wrapped in
 * `WithDefault` — `kind: WithDefault<…>` fails with "must be optional" and a bare union
 * fails with "a default enum value is required".
 */
/**
 * `UIScrollView` geometry, shaped exactly like `ScrollView`'s own scroll event.
 *
 * The shape is not ours to choose: `@gorhom/bottom-sheet` destructures
 * `{ contentOffset: { y } }` inside a worklet, and reanimated's `useAnimatedScrollHandler`
 * types its argument as React Native's `NativeScrollEvent`. Anything narrower would work
 * against those two and then fail in a way that reads as a reanimated bug.
 */
type ScrollEvent = Readonly<{
  contentOffset: Readonly<{ x: Double; y: Double }>
  contentSize: Readonly<{ width: Double; height: Double }>
  layoutMeasurement: Readonly<{ width: Double; height: Double }>
  contentInset: Readonly<{
    top: Double
    left: Double
    bottom: Double
    right: Double
  }>
  zoomScale: Double
}>

export interface NativeProps extends ViewProps {
  /**
   * The whole descriptor tree as JSON: sections, rows, the content DSL, and the light and
   * dark appearances. Decoded by Swift `Codable`.
   *
   * Appearance rides in here rather than in a typed prop on purpose — Swift `Codable`
   * optionals express "unset" natively, which codegen structs cannot, so this is what
   * makes "fall back to the platform's own colour" expressible at all.
   */
  tree: string

  /**
   * Bumped by JS whenever `tree` is re-serialized.
   *
   * `updateProps` gates all decoding on this rather than comparing the strings: the tree
   * can be several megabytes, so comparing it would mean a pointless `memcmp` on every
   * commit and decoding it unconditionally would be far worse.
   */
  revision: Int32

  /**
   * Pins the interface style the list resolves its colours against.
   *
   * A typed prop rather than part of the tree because it is a cheap scalar that changes
   * independently of the content, so flipping a theme should not re-encode every row.
   *
   * This exists because `UIListContentConfiguration` labels, separators and accessories draw
   * with *system* colours, and those follow the **device**. An app whose own theme says "dark"
   * while the phone is in light mode would otherwise get dark rows with black text. Setting
   * this drives `overrideUserInterfaceStyle`, so UIKit resolves its own semantic colours
   * against the app's choice.
   */
  colorScheme?: WithDefault<'system' | 'light' | 'dark', 'system'>

  /**
   * Shows the A–Z scrubber down the trailing edge, built from each section's `indexTitle`.
   *
   * A typed prop rather than part of the tree because it is a scalar the caller toggles
   * independently of the content, and because native builds the bar from the tree it already
   * has — there is nothing extra to serialize.
   */
  showsSectionIndex?: boolean

  /**
   * Points of vertical space per letter in the scrubber. `0` means automatic.
   *
   * The system control is compact — roughly one line of a caption font — and sits centred in the
   * available height rather than stretched across it. Stretching is what a naive implementation
   * does, and it is immediately wrong next to the real thing: the letters drift apart on a tall
   * screen and the whole control stops reading as one object.
   */
  sectionIndexRowHeight?: Float

  /** The magnified letter shown beside the finger while scrubbing. */
  sectionIndexShowsCallout?: WithDefault<boolean, true>

  /**
   * The scroll indicator down the trailing edge. Named as `ScrollView` names it, because this is
   * the same `UIScrollView` property and the familiarity is worth more than a shorter name.
   *
   * Worth turning off when the list carries a section index: the two occupy the same strip, and
   * even inset apart they read as two competing trailing rails.
   */
  showsVerticalScrollIndicator?: WithDefault<boolean, true>

  // -------------------------------------------------------------------------
  // Insets
  //
  // Named as `ScrollView` names them, because these *are* the `UIScrollView` properties and a
  // familiar name is worth more than a tidier one.
  // -------------------------------------------------------------------------

  /**
   * Extra inset around the content, as four scalars.
   *
   * `Root` takes a `contentInset` object and unpacks it here, because codegen cannot be given the
   * `EdgeInsetsValue` primitive from TypeScript: it matches that name only on an *unresolved*
   * annotation, and React Native exports the type from Flow alone — so a local alias is inlined
   * and the parser then rejects the plain `number` members. Four floats cost nothing and the public
   * API keeps the shape people expect.
   *
   * Kept separately from the resolved inset natively, because the keyboard path computes
   * `max(keyboardOverlap, contentInsetBottom)` and must never shrink the list below what the
   * caller asked for.
   */
  contentInsetTop?: Float
  contentInsetLeft?: Float
  contentInsetBottom?: Float
  contentInsetRight?: Float

  /**
   * How UIKit folds the surrounding chrome into the content inset.
   *
   * **Defaults to `automatic`, which diverges from `ScrollView`'s `never`.** That default is
   * deliberate: this control is normally a screen's only scroll view under a native stack, and
   * with `never` the content starts underneath the navigation bar and the large title has nothing
   * to collapse against. React Native gets away with `never` because react-native-screens forces
   * `automatic` on the scroll view it discovers; nothing forces it on ours, so the useful default
   * has to be the one that is set here.
   */
  contentInsetAdjustmentBehavior?: WithDefault<
    'automatic' | 'scrollableAxes' | 'never' | 'always',
    'automatic'
  >

  /**
   * Sugar over the above: `false` pins the behaviour to `never`.
   *
   * Worth knowing that this prop is a **no-op on Fabric's own `ScrollView`** —
   * `RCTScrollViewComponentView.mm` has its `MAP_SCROLL_VIEW_PROP` line commented out — so "behaves
   * exactly like ScrollView" is ambiguous here. It is implemented to what React Native *documents*
   * rather than to what it currently does, and `contentInsetAdjustmentBehavior` is the precise
   * control when the two disagree.
   */
  automaticallyAdjustContentInsets?: WithDefault<boolean, true>

  /** Whether the scroll indicators inset themselves along with the content. */
  automaticallyAdjustsScrollIndicatorInsets?: WithDefault<boolean, true>

  // -------------------------------------------------------------------------
  // Keyboard
  // -------------------------------------------------------------------------

  /**
   * Grows the bottom inset by however much the keyboard covers this list.
   *
   * The overlap is computed from the list's own geometry in window space rather than from the
   * screen height, which is what makes it correct inside a form sheet or a bottom sheet — there
   * the list's bottom edge and the screen's are nowhere near each other.
   */
  automaticallyAdjustKeyboardInsets?: WithDefault<boolean, false>

  /**
   * A superset of the above: also scrolls the focused row above the keyboard, and keeps it there
   * when focus moves between fields with the keyboard already up.
   */
  keyboardAware?: WithDefault<boolean, false>

  /** Extra breathing room above the focused row, in points. */
  keyboardAwareOffset?: Float

  keyboardDismissMode?: WithDefault<'none' | 'onDrag' | 'interactive', 'onDrag'>

  /**
   * Whether tapping a row keeps the keyboard up. `ScrollView`'s prop, same name, same values.
   *
   * `handled` is the one that carries real information here: a row with an `onPress` *handled* the
   * tap, so the keyboard stays; a row without one did not, so it goes.
   */
  keyboardShouldPersistTaps?: WithDefault<
    'never' | 'always' | 'handled',
    'never'
  >

  // -------------------------------------------------------------------------
  // Scrolling
  // -------------------------------------------------------------------------

  /**
   * Whether the list scrolls at all. As `ScrollView` has it.
   *
   * Not only a convenience: it is how `@rngui/collection-view/bottom-sheet` keeps a sheet and its
   * list from fighting. Gorhom's design assumes the scrollable's own pan recognizer can be made
   * *simultaneous* with the sheet's — react-native-gesture-handler arranges that by finding the
   * `UIScrollView` inside an `RCTScrollViewComponentView`, which this component is not and cannot
   * be. Both gestures therefore run at once: the list scrolls under the finger while the sheet
   * moves, and the sheet's per-frame correction fights it. Turning scrolling off for as long as the
   * sheet owns the drag removes the second gesture instead of correcting it afterwards.
   */
  scrollEnabled?: WithDefault<boolean, true>

  /**
   * `UIScrollView.decelerationRate`, raw. Negative means "leave UIKit's own value alone".
   *
   * A number rather than the `'normal' | 'fast'` enum it looks like it should be, because
   * `@gorhom/bottom-sheet` drives this from a worklet and sends **`0`** for a locked list — stop
   * dead, so the drag that follows moves the sheet rather than continuing an inherited fling.
   * An enum could not express that, and `Root` still takes `ScrollView`'s `'normal' | 'fast' |
   * number` and converts.
   *
   * The sentinel is negative rather than `0` for exactly that reason: codegen has no
   * representation of an absent number, and `0` is the one value the sheet most needs to send.
   *
   * Reanimated writes animated props straight onto the shadow node, so a prop it wants and we do
   * not declare is dropped in silence — this being a typed prop is what makes the lock work at all.
   */
  decelerationRate?: WithDefault<Float, -1>

  /**
   * Whether native should emit scroll events at all.
   *
   * The same gate as `tracksVisibleRange`, for the same reason and with one extra one: a
   * reanimated `useAnimatedScrollHandler` is attached by *view tag* rather than by passing a
   * prop, so unlike `onVisibleRangeChange` there is nothing for `Root` to infer a listener
   * from. Somebody has to say.
   */
  tracksScroll?: boolean

  /**
   * `UIScrollViewDelegate`, forwarded whole.
   *
   * All five, because reanimated's `useAnimatedScrollHandler` subscribes to all five whenever
   * a handler object is passed — and a subscription with nothing behind it is a sheet that
   * never learns the drag ended.
   */
  onScroll?: DirectEventHandler<ScrollEvent>
  onScrollBeginDrag?: DirectEventHandler<ScrollEvent>
  onScrollEndDrag?: DirectEventHandler<ScrollEvent>
  onMomentumScrollBegin?: DirectEventHandler<ScrollEvent>
  onMomentumScrollEnd?: DirectEventHandler<ScrollEvent>

  /**
   * The laid-out size of the content, whenever it changes.
   *
   * Gated by `tracksScroll` as well: it is what a bottom sheet with `enableDynamicSizing` reads
   * to size itself to its content, and useless to everything else.
   */
  onContentSizeChange?: DirectEventHandler<
    Readonly<{ width: Double; height: Double }>
  >

  /**
   * Whether native should report the visible row range at all.
   *
   * Set by `Root` from whether an `onVisibleRangeChange` callback was passed, and it exists
   * because Fabric gives native no way to ask. An event emitter is always installed and always
   * dispatches, so without this the list would post an event on every run-loop turn of every
   * scroll — a real cost on the JavaScript thread — for the overwhelming majority of lists that
   * never listen.
   */
  tracksVisibleRange?: boolean

  /**
   * The range of rows currently on screen, as indices into the flattened row list.
   *
   * "Flattened" means every section's rows concatenated in tree order, which is the same order
   * `serialize` produces — so JavaScript can map an index back to a row without native having
   * to send ids. Both ends are inclusive; an empty list reports `-1, -1`.
   *
   * This is the escape hatch for the one thing that cannot recycle. Every stock row kind is
   * pooled by UIKit, but a `Host` row is a distinct React subtree with its own state and there
   * is no pool of interchangeable ones — so a long list of hosted rows has to be windowed in
   * JavaScript, rendering children only for the rows in view plus some overscan.
   */
  onVisibleRangeChange?: DirectEventHandler<
    Readonly<{ firstIndex: Int32; lastIndex: Int32 }>
  >

  /**
   * Row-scoped events.
   *
   * Every one carries a `rowId` rather than an index, and that is the whole design: indices shift
   * the moment a row is inserted, and an inline date picker appearing between a switch and its
   * footer shifts every index after it. `Root` keeps a registry of callbacks keyed by the same id
   * and dispatches, because a function cannot ride inside the serialized tree.
   *
   * A single set of top-level handlers rather than per-row ones is also what keeps a 2,000-row
   * list cheap: one event emitter, not one per row.
   */
  onRowPress?: DirectEventHandler<Readonly<{ rowId: string }>>
  onSwitchChange?: DirectEventHandler<
    Readonly<{ rowId: string; value: boolean }>
  >
  onTextChange?: DirectEventHandler<Readonly<{ rowId: string; value: string }>>
  /**
   * Fires when a text row gains or loses first-responder status.
   *
   * Separate from `onTextChange` because focus drives layout — it is what tells JavaScript which
   * row to keep above the keyboard — and it fires when no text changed at all.
   */
  onFocusChange?: DirectEventHandler<
    Readonly<{ rowId: string; focused: boolean }>
  >
  /** `millis` rather than a date string: it is the one date encoding that survives JSON intact. */
  onDateChange?: DirectEventHandler<Readonly<{ rowId: string; millis: Double }>>
  /**
   * A slider's value, while it is being dragged.
   *
   * **Fires per frame, which is why the pair exists.** A drag across a phone produces sixty of
   * these a second, and a caller that recomputes a screen from each one is doing sixty React
   * commits a second — the exact cost this library exists to avoid. `onSliderCommit` fires once,
   * on release, and is what most callers actually want to act on; this one is for the label that
   * has to track the thumb.
   */
  onSliderChange?: DirectEventHandler<
    Readonly<{ rowId: string; value: Double }>
  >
  /** The value the drag settled on, once. */
  onSliderCommit?: DirectEventHandler<
    Readonly<{ rowId: string; value: Double }>
  >
  onMenuSelect?: DirectEventHandler<Readonly<{ rowId: string; itemId: string }>>
  onSwipeAction?: DirectEventHandler<
    Readonly<{ rowId: string; actionId: string }>
  >
  /** The one event addressed by *section* rather than by row — a header's trailing button. */
  onSectionAction?: DirectEventHandler<Readonly<{ sectionId: string }>>
}

/**
 * A command rather than a prop, because the caller is reanimated rather than React.
 *
 * `scrollTo` from a worklet compiles to `dispatchCommand(ref, 'scrollTo', [x, y, animated])`,
 * which reaches the component view's `handleCommand:args:` synchronously on the UI thread. That
 * is the only way a bottom sheet can pin its list at the top *during* a drag: routing it through
 * a prop would put a React commit in the middle of a gesture, one frame late every time.
 */
interface NativeCommands {
  scrollTo: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>,
    x: Double,
    y: Double,
    animated: boolean
  ) => void
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: ['scrollTo'],
})

export default codegenNativeComponent<NativeProps>('RNGUICollectionView')

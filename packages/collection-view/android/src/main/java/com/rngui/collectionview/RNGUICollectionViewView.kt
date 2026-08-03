package com.rngui.collectionview

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.MotionEvent
import kotlin.math.abs
import android.widget.FrameLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.rngui.collectionview.generated.Appearance
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.Tree

/**
 * The Android collection view: a `RecyclerView` inside a `FrameLayout`.
 *
 * A wrapper rather than a `RecyclerView` subclass, because what is still to come sits *beside* the
 * list rather than inside it — M5's fast-scroller bubble, M8's hidden container parking hosted
 * React children until a holder claims one. Retrofitting a parent later would mean moving every
 * prop already written against the old root.
 *
 * Still a `ViewGroup`, which is the one thing the stub this replaces was load-bearing about: a
 * manager whose view is not a group makes the mounting layer throw the moment a React child
 * arrives. `FrameLayout` keeps that guarantee.
 */
class RNGUICollectionViewView(context: ThemedReactContext) : FrameLayout(context) {
  private val reactContext: ReactContext = context

  private val layout = LockableLayoutManager(context)

  val list =
    CollectionRecyclerView(context).apply {
      layoutManager = layout
      // The default animator cross-fades a rebound item, which would turn a theme flip into
      // 2,000 simultaneous 250ms alpha animations. Identity changes still animate — that comes
      // from ListAdapter's diff — but a pure content change should simply redraw.
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

  // Explicit types, and `scroll` before `insets`. The two hold lambdas that reach for each other,
  // which Kotlin cannot infer through ("Type checking has run into a recursive problem") — and
  // declaring the one whose lambda fires *first* second is what keeps the cycle safe at runtime as
  // well as resolvable at compile time.
  private val scroll: ScrollReporter =
    ScrollReporter(
      list = list,
      emit = ::dispatch,
      surfaceAndTag = { UIManagerHelper.getSurfaceId(reactContext) to id },
      insets = {
        ScrollEvent.Insets(
          top = PixelUtil.toDIPFromPixel(insets.resolvedTop.toFloat()).toDouble(),
          left = 0.0,
          bottom = PixelUtil.toDIPFromPixel(insets.resolvedBottom.toFloat()).toDouble(),
          right = 0.0,
        )
      },
    )

  private val insets: InsetController =
    InsetController(
      root = this,
      list = list,
      onInsetsChanged = { scroll.reportContentSizeIfChanged() },
    )

  // -- pending props ----------------------------------------------------------------------------
  //
  // Fabric writes props one setter at a time, in no guaranteed order, so `setTree` and
  // `setRevision` cannot act on their own: a revision arriving first would gate on a tree that has
  // not landed, and a tree arriving first would be decoded before the gate had a chance to reject
  // it. Both are stashed here and `commitProps` runs once per transaction from the manager's
  // `onAfterUpdateTransaction` — the Android analogue of iOS's `updateProps`, where this exact
  // gate lives.

  var pendingTree: String? = null
  var pendingRevision: Int = 0
  var pendingColorScheme: ColorScheme = ColorScheme.system

  private var appliedRevision: Int = Int.MIN_VALUE
  private var appliedColorScheme: ColorScheme = ColorScheme.system

  /** The decoded tree, flattened. Kept so a restyle never has to decode again. */
  var flattened: FlattenedTree = FlattenedTree()
    private set

  /** Both null until a tree arrives, which resolves to the platform's own colours. */
  private var appearance: Appearance? = null
  private var darkAppearance: Appearance? = null

  /** `insetGrouped` unless the tree says otherwise, matching the JS default. */
  private var listAppearance: ListAppearance = ListAppearance.insetGrouped

  /**
   * The configuration the appearance resolves against.
   *
   * Held rather than read from `resources` on demand, and that is a race fixed rather than a
   * caching trick. A `ThemedReactContext` hands out the *application* context's `Resources`, and
   * those are updated on their own message — so at the instant `onConfigurationChanged` reaches
   * this view, `resources.configuration` can still report the mode the device has just left. The
   * `newConfig` handed to that callback cannot: it is the new configuration, by definition.
   *
   * The symptom was specific enough to be worth recording. Colours resolved from the stale
   * configuration produced *light* values on a screen that had already gone dark, so the secondary
   * labels — 60% grey either way — stayed legible while every primary label went black on black.
   * Half the list restyling correctly is a much better disguise than none of it.
   */
  private var configuration: Configuration = Configuration(context.resources.configuration)

  // Declared *after* everything `resolver()` reads, and that ordering is load-bearing: Kotlin runs
  // property initializers top to bottom, so an adapter built above `appliedColorScheme` would read
  // it before it was assigned and hand a null to a non-null parameter. Which is exactly what the
  // first version of this file did, and it crashed on mount rather than at compile time — the
  // platform declaration is `@NonNull`, so the check is a runtime one.
  /**
   * Every row event, dispatched straight to the event dispatcher.
   *
   * One object for the whole list rather than a set of lambdas per row: a 2,000-row list allocates
   * this once.
   */
  private val rowEvents =
    object : RowEvents {
      override fun onRowPress(rowId: String) =
        dispatch(RowPressEvent(surfaceId(), id, rowId))

      override fun onSwitchChange(rowId: String, value: Boolean) =
        dispatch(RowValueEvent.bool(surfaceId(), id, RowValueEvent.SWITCH, rowId, value))

      override fun onTextChange(rowId: String, value: String) =
        dispatch(RowValueEvent.string(surfaceId(), id, RowValueEvent.TEXT, rowId, value))

      override fun onFocusChange(rowId: String, focused: Boolean) =
        dispatch(RowValueEvent.bool(surfaceId(), id, RowValueEvent.FOCUS, rowId, focused))

      override fun onDateChange(rowId: String, millis: Double) =
        dispatch(RowValueEvent.number(surfaceId(), id, RowValueEvent.DATE, rowId, millis))

      override fun onMenuSelect(rowId: String, itemId: String) =
        dispatch(RowValueEvent.string(surfaceId(), id, RowValueEvent.MENU, rowId, itemId))

      override fun onSwipeAction(rowId: String, actionId: String) =
        dispatch(RowValueEvent.string(surfaceId(), id, RowValueEvent.SWIPE, rowId, actionId))
    }

  /**
   * Where React mounts hosted children.
   *
   * Every `<CollectionView.Host>` child arrives here through the manager's `addView`, and a holder
   * borrows the one it needs while its row is on screen. React owns their lifetime throughout;
   * this only owns *where they sit* when nothing is showing them.
   */
  private val parking = ParkingView(context)

  private val adapter =
    CollectionAdapter(
      rowStyle(),
      listStyle(),
      rowEvents,
      parking,
      // `claimedChildren`, never `parking.getChildAt` — see the note on that list. Reading the
      // bay's own child order renumbers everything after the first claimed child, so the rows on
      // screen borrow the wrong subtrees and the ones being windowed in come up empty.
      hostChildAt = { index -> claimedChildren.getOrNull(index) },
    )

  /**
   * Every hosted child in mount order, whether parked or currently claimed.
   *
   * `hostIndex` indexes into *mount order*, and a child that a holder has borrowed is no longer a
   * child of the parking bay — so reading the bay alone would renumber every child after the first
   * one on screen. This list is the stable order; the bay is only where the unclaimed ones sit.
   */
  private val claimedChildren = mutableListOf<android.view.View>()

  private val swipe =
    SwipeActionsCallback(
      actionsAt = adapter::swipeActionsAt,
      rowIdAt = adapter::rowIdAt,
      style = adapter::currentStyle,
      onAction = rowEvents::onSwipeAction,
    )

  private val decoration = GroupDecoration(listStyle())

  private val stickyHeaders = StickyHeaderDecoration(flattened, enabled = false)

  private val sectionIndex = SectionIndexView(context, list)

  /** `showsSectionIndex`, kept until a tree arrives to build the scrubber from. */
  var pendingShowsSectionIndex: Boolean = false

  fun setSectionIndexShowsCallout(value: Boolean) {
    sectionIndex.showsCallout = value
  }

  init {
    addView(parking, LayoutParams(0, 0))
    addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    // Above the list, so the thumb draws over the rows and receives touches before they do.
    addView(sectionIndex, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    list.adapter = adapter
    list.addItemDecoration(decoration)
    list.addItemDecoration(stickyHeaders)
    list.addOnItemTouchListener(
      PinnedHeaderTouchListener(stickyHeaders) { sectionId ->
        dispatch(SectionActionEvent(surfaceId(), id, sectionId))
      }
    )
    ItemTouchHelper(swipe).attachToRecyclerView(list)
    // Before the pinned-header listener: an open tray must swallow the tap that closes it, and the
    // header listener would otherwise claim a touch that lands under a pinned header.
    list.addOnItemTouchListener(swipe.touchListener())
    list.addItemDecoration(SwipeTrayDecoration(swipe))
    list.addOnScrollListener(scroll)
    list.addOnScrollListener(
      object : RecyclerView.OnScrollListener() {
        override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
          reportVisibleRange()
          // Scrolling closes an open tray. Leaving it open would leave a row translated off its
          // card while its actions scrolled away from the fingers aiming at them.
          if (dy != 0) swipe.close(view)
        }
      }
    )
    insets.attach()
    applyBackground()
  }

  // -- the scroll contract ----------------------------------------------------------------------

  var contentInsetTop: Int
    get() = insets.top
    set(value) { insets.top = value; insets.apply() }

  var contentInsetLeft: Int
    get() = insets.left
    set(value) { insets.left = value; insets.apply() }

  var contentInsetBottom: Int
    get() = insets.bottom
    set(value) { insets.bottom = value; insets.apply() }

  var contentInsetRight: Int
    get() = insets.right
    set(value) { insets.right = value; insets.apply() }

  fun setInsetAdjustment(value: InsetAdjustment) {
    insets.adjustment = value
    insets.apply()
  }

  fun setAdjustsForKeyboard(value: Boolean) {
    insets.adjustsForKeyboard = value
    insets.apply()
  }

  fun setTracksScroll(value: Boolean) {
    scroll.tracksScroll = value
  }

  fun setScrollEnabled(value: Boolean) {
    layout.scrollEnabled = value
  }

  fun setDecelerationRate(value: Float) {
    list.decelerationRate = value
  }

  fun setShowsVerticalScrollIndicator(value: Boolean) {
    list.isVerticalScrollBarEnabled = value
  }

  fun setKeyboardDismissMode(mode: KeyboardDismissMode) {
    keyboardDismissMode = mode
  }

  private var keyboardDismissMode: KeyboardDismissMode = KeyboardDismissMode.onDrag

  /**
   * `keyboardAware` implies `automaticallyAdjustKeyboardInsets` — it is documented as a superset.
   *
   * The other half, scrolling the focused row above the IME, needs a focused row to scroll to and
   * therefore needs M7's text fields. Setting the inset half now means a form is usable rather
   * than half-covered in the meantime.
   */
  fun setKeyboardAware(value: Boolean) {
    keyboardAware = value
    if (value) setAdjustsForKeyboard(true)
  }

  private var keyboardAware: Boolean = false

  var keyboardAwareOffset: Int = 0

  // -- gesture interop with react-native-gesture-handler -----------------------------------------
  //
  // **This is the Android analogue of the iOS `RCTScrollViewComponentView` problem, and unlike iOS
  // it has an answer.** `@gorhom/bottom-sheet` wraps the scrollable in a `GestureDetector` carrying
  // a `Gesture.Native()`, and RNGH's `NativeViewGestureHandler` decides whether to activate with
  // exactly one line:
  //
  //     tryIntercept(view, event) = view is ViewGroup && view.onInterceptTouchEvent(event)
  //
  // The view it asks is this one. A `FrameLayout` never intercepts, so the handler never activated,
  // the sheet kept the whole gesture, and the list neither scrolled nor emitted a single scroll
  // event. Forwarding `canScrollVertically` was necessary — RNGH and the touch dispatcher must
  // agree — but nothing ever asked it.
  //
  // So this answers the probe. What it must *not* do is answer it during ordinary dispatch:
  // `onInterceptTouchEvent` is called there too, and returning true would take the touch away from
  // the `RecyclerView` entirely — the list would stop scrolling in every context, sheet or no
  // sheet. The two callers are told apart by which one is on the stack.

  private var inDispatch = false
  private var probeDownX = 0f
  private var probeDownY = 0f
  private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    inDispatch = true
    try {
      return super.dispatchTouchEvent(event)
    } finally {
      inDispatch = false
    }
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    // The ordinary path. Never steal: the children — the list, and the scrubber above it — are
    // perfectly capable of claiming their own touches, and that is how every other screen works.
    if (inDispatch) return super.onInterceptTouchEvent(event)
    return wouldScroll(event)
  }

  /**
   * Hands RNGH's touches to the list, because they arrive by a route children never see.
   *
   * Once `NativeViewGestureHandler` activates it drives the view itself:
   *
   *     fun sendTouchEvent(view: View?, event: MotionEvent) = view?.onTouchEvent(event)
   *
   * For a `ReactScrollView` that works, because the view it was handed *is* the scroller. Here the
   * scroller is a child, and `onTouchEvent` on a parent never reaches one — children are reached
   * through `dispatchTouchEvent`. So the handler activated, took the gesture off the sheet, and
   * then delivered every move to a `FrameLayout` that had nothing to do with it: the list did not
   * scroll and no scroll events were emitted, which is exactly the symptom with none of the causes
   * anyone would guess.
   *
   * Guarded by the same flag as the interception probe. In ordinary dispatch this is only reached
   * when no child claimed the touch, and forwarding it then would hand the list an event it has
   * already declined.
   *
   * The list fills this view at the origin, so the event needs no translation.
   */
  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (inDispatch) return super.onTouchEvent(event)
    return list.onTouchEvent(event)
  }

  /**
   * Whether a drag this far in this direction would actually scroll the list.
   *
   * Deliberately *not* `list.onInterceptTouchEvent(event)`. That would answer perfectly and drive
   * the `RecyclerView`'s own scroll-detection state machine a second time for the same event, since
   * ordinary dispatch is about to hand it the very same one. Slop and direction are enough, and
   * they are side-effect free.
   *
   * Deltas rather than absolute coordinates, because RNGH reports the event in the root view's
   * space rather than in ours — a delta is the same in both.
   *
   * `canScrollVertically` runs through `LockableLayoutManager`, so `scrollEnabled = false` answers
   * "no" here as well. That is the whole handshake: while the sheet owns the drag it has locked the
   * list, this reports that the list would not scroll, the handler stays inactive, and the sheet
   * keeps the gesture. The moment gorhom unlocks, the same question starts answering "yes".
   */
  private fun wouldScroll(event: MotionEvent): Boolean =
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        probeDownX = event.x
        probeDownY = event.y
        false
      }
      MotionEvent.ACTION_MOVE -> {
        val dy = event.y - probeDownY
        val dx = event.x - probeDownX
        // A mostly-horizontal drag belongs to a chip strip or a swipe action, never to the sheet.
        if (abs(dy) < touchSlop || abs(dy) <= abs(dx)) false
        // Dragging up scrolls the content down, and vice versa.
        else list.canScrollVertically(if (dy < 0) 1 else -1)
      }
      else -> false
    }

  /**
   * Answers for the list inside, because that is the view everything else asks about.
   *
   * **This is the Android analogue of the iOS `RCTScrollViewComponentView` problem, and the risk
   * register called it.** `react-native-gesture-handler`'s `NativeViewGestureHandler` decides
   * whether a view is scrollable by asking it — and what it is attached to here is this
   * `FrameLayout`, not the `RecyclerView` inside. A `FrameLayout` says no, so the sheet keeps the
   * whole gesture and the list never receives a touch: no scrolling, and no scroll events either,
   * which is what the sheet needs to know when to hand the drag back.
   *
   * Forwarding costs nothing and makes the wrapper honest. `LockableLayoutManager` still has the
   * final say, so `scrollEnabled = false` reads as "not scrollable" all the way out — which is the
   * answer the sheet wants while it owns the drag.
   */
  override fun canScrollVertically(direction: Int): Boolean = list.canScrollVertically(direction)

  override fun canScrollHorizontally(direction: Int): Boolean =
    list.canScrollHorizontally(direction)

  /**
   * Re-runs measure and layout on the next frame, because nothing else will.
   *
   * **React Native lays views out from the shadow tree and ignores requests from below.** A native
   * child that decides for itself that it needs re-laying out — which is every `RecyclerView` after
   * `notifyItemRangeChanged` — calls `requestLayout()`, the call walks up into React Native's view
   * tree, and stops there. No `onLayout`, so no `dispatchLayout`, so no rebind.
   *
   * The failure mode is worth naming because it is not the one anyone looks for: the *style* was
   * correct, `RowStyle` said `ffffffff`, `restyle()` ran, and the rows carried on drawing black.
   * Nothing was wrong with any of the code being suspected. The rebind simply never happened.
   *
   * This is the standard shape for hosting a self-laying-out native view inside React Native, and
   * it costs one posted runnable per request rather than one per frame.
   */
  override fun requestLayout() {
    super.requestLayout()
    if (relayoutScheduled || width == 0 || height == 0) return
    relayoutScheduled = true
    post(relayout)
  }

  private var relayoutScheduled = false

  private val relayout = Runnable {
    relayoutScheduled = false
    measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
    )
    layout(left, top, right, bottom)
  }

  /**
   * Applies whatever changed in this transaction.
   *
   * **The revision gate.** `tree` is a string that can be megabytes, so decoding is gated on
   * `revision` rather than on comparing the strings: comparing would mean a full-length comparison
   * on every commit, and decoding unconditionally would be far worse. JavaScript reuses the
   * previous revision when the serialized tree is byte-identical, so an unchanged tree never
   * reaches here at all. Same field, same rule, same reasoning as iOS.
   */
  fun commitProps() {
    val schemeChanged = pendingColorScheme != appliedColorScheme
    val revisionChanged = pendingRevision != appliedRevision

    if (schemeChanged) appliedColorScheme = pendingColorScheme

    if (revisionChanged) {
      appliedRevision = pendingRevision
      // Decoded exactly once per revision. The appearance is kept alongside the flattened rows
      // rather than re-read from the string later, because "decode only when revision changes" is
      // the guarantee and a second decode for a restyle would quietly break it.
      val tree = Tree.decode(pendingTree)
      appearance = tree.appearance
      darkAppearance = tree.darkAppearance
      listAppearance = tree.listAppearance ?: ListAppearance.insetGrouped
      flattened = FlattenedTree.of(tree)
      adapter.submitList(flattened.items)
    }

    // A tree carries its own appearance, so a new revision restyles too — and this runs *after*
    // the list so the rows being restyled are the ones that just landed, not the ones they
    // replaced.
    if (schemeChanged || revisionChanged) restyle()

    reportVisibleRange()

    // Only `plain` pins its headers. A grouped list's header belongs to the card below it and
    // scrolls away with it, on both platforms.
    stickyHeaders.update(flattened, enabled = listAppearance == ListAppearance.plain)
    sectionIndex.update(flattened, listStyle(), pendingShowsSectionIndex)
  }

  /**
   * Rebinds the visible rows against the current configuration.
   *
   * **The Android half of "a theme flip costs no JS render".** iOS gets it free from dynamic
   * `UIColor`s, which UIKit re-resolves on a trait change without reconfiguring a single cell.
   * Android has no dynamic colour — a resolved colour is an `Int`, and an `Int` does not know what
   * mode produced it — so the same guarantee is bought by rebinding instead. Strictly more work
   * than UIKit does, and still no prop change, no commit and no round trip to JavaScript, which is
   * the part the guarantee was ever about.
   *
   * This reaches a view only when the host Activity declares `uiMode` in its
   * `android:configChanges`. Without it the Activity is recreated and the tree is rebuilt from
   * scratch, which produces the right pixels by a far more expensive route. React Native's own
   * template declares it.
   */
  /**
   * Re-resolves the insets, because they depend on where this view sits rather than only on the
   * window — see `InsetController.overlappingBars`. A view that has just been given its geometry
   * has just changed the answer.
   */
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    insets.apply()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    configuration = Configuration(newConfig)
    restyle()
  }

  private fun restyle() {
    val list = listStyle()
    decoration.restyle(list)
    adapter.restyle(rowStyle(), list)
    applyBackground()
    // The decoration draws from `onDraw`, which only runs on a draw pass — and a restyle that
    // changes nothing about layout would not schedule one.
    this.list.invalidateItemDecorations()
  }

  private fun rowStyle() = RowStyle.of(resolver())

  private fun listStyle() =
    ListStyle.of(context, resolver(), rowStyle(), listAppearance)

  /**
   * The colour behind the cards.
   *
   * On the wrapper rather than on the `RecyclerView`, so M8's hidden host container and M5's
   * fast-scroller bubble sit on it too rather than on whatever is behind the whole component.
   */
  private fun applyBackground() {
    setBackgroundColor(listStyle().backgroundColor)
  }

  private fun resolver(): AppearanceResolver =
    AppearanceResolver(
      isDark = AppearanceResolver.isDark(configuration, appliedColorScheme),
      light = appearance,
      dark = darkAppearance,
    )

  private fun surfaceId(): Int = UIManagerHelper.getSurfaceId(reactContext)

  // -- hosted children --------------------------------------------------------------------------
  //
  // React's view of this component's children is *only* the hosted subtrees. The list, the parking
  // bay and the scrubber are ours, and the manager's overrides below keep the two numbering
  // schemes apart — an off-by-one here puts a React child where the RecyclerView should be.

  fun addHostChild(child: android.view.View, index: Int) {
    claimedChildren.add(index.coerceIn(0, claimedChildren.size), child)
    parking.addView(child)
  }

  fun removeHostChildAt(index: Int) {
    val child = claimedChildren.getOrNull(index) ?: return
    claimedChildren.removeAt(index)
    (child.parent as? android.view.ViewGroup)?.removeView(child)
  }

  fun removeAllHostChildren() {
    claimedChildren.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
    claimedChildren.clear()
  }

  val hostChildCount: Int
    get() = claimedChildren.size

  fun hostChildAt(index: Int): android.view.View? = claimedChildren.getOrNull(index)

  // -- visible range ----------------------------------------------------------------------------

  var tracksVisibleRange: Boolean = false

  private var lastRange = -1 to -1

  /**
   * Reports the visible **row** range, as indices into the flattened row list.
   *
   * The one escape hatch for the thing that cannot recycle: a long list of hosted rows has to be
   * windowed in JavaScript, rendering children only for the rows in view plus overscan. Reported
   * from `findFirst/LastVisibleItemPosition` and mapped through the flattened index, because an
   * adapter position counts headers and a row index does not.
   */
  private fun reportVisibleRange() {
    if (!tracksVisibleRange) return
    val manager = layout
    val first = manager.findFirstVisibleItemPosition()
    val last = manager.findLastVisibleItemPosition()

    val range =
      if (first == RecyclerView.NO_POSITION || flattened.rowCount == 0) {
        -1 to -1
      } else {
        // Scanned outward rather than taken directly: the first visible *item* may be a header,
        // which has no row index at all.
        var firstRow = -1
        for (position in first..last) {
          val index = flattened.rowIndexAt(position)
          if (index >= 0) {
            firstRow = index
            break
          }
        }
        var lastRow = -1
        for (position in last downTo first) {
          val index = flattened.rowIndexAt(position)
          if (index >= 0) {
            lastRow = index
            break
          }
        }
        firstRow to lastRow
      }

    if (range == lastRange) return
    lastRange = range
    dispatch(VisibleRangeChangeEvent(surfaceId(), id, range.first, range.second))
  }

  private fun dispatch(event: Event<*>) {
    UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(event)
  }

  /**
   * The `scrollTo` command.
   *
   * `scrollToPosition(0)` for the `(0, 0)` case rather than a computed delta, because that is what
   * `@gorhom/bottom-sheet` sends on every frame of a drag and it has to land exactly at zero.
   * `computeVerticalScrollOffset` is an average-item-height estimate, so using it for that case
   * would leave the list a few pixels off the top and the sheet would keep correcting. M10 owns
   * the rest of the scroll contract, including the exact offset accumulator.
   */
  fun scrollTo(y: Double, animated: Boolean) {
    if (y <= 0.0) {
      list.scrollToPosition(0)
      scroll.resetOffset(0)
      return
    }
    // Against the *accumulated* offset rather than `computeVerticalScrollOffset()`, for the same
    // reason the event reports the accumulator: the platform's number is an estimate, and
    // scrolling by the difference between a target and an estimate lands somewhere neither.
    val target = context.dp(y) - scroll.offsetPx
    if (animated) list.smoothScrollBy(0, target) else list.scrollBy(0, target)
  }
}

package com.rngui.collectionview

import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.facebook.react.bridge.ReactContext
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

  val list =
    RecyclerView(context).apply {
      layoutManager = LinearLayoutManager(context)
      // The default animator cross-fades a rebound item, which would turn a theme flip into
      // 2,000 simultaneous 250ms alpha animations. Identity changes still animate — that comes
      // from ListAdapter's diff — but a pure content change should simply redraw.
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

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
  private val adapter =
    CollectionAdapter(rowStyle(), listStyle(), onRowPress = ::emitRowPress)

  private val decoration = GroupDecoration(listStyle())

  init {
    addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    list.adapter = adapter
    list.addItemDecoration(decoration)
    applyBackground()
  }

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

  private fun emitRowPress(rowId: String) {
    dispatch(RowPressEvent(UIManagerHelper.getSurfaceId(reactContext), id, rowId))
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
      return
    }
    val target = context.dp(y) - list.computeVerticalScrollOffset()
    if (animated) list.smoothScrollBy(0, target) else list.scrollBy(0, target)
  }
}

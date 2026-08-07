package com.rngui.collectionview

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.module.annotations.ReactModule
import android.view.View
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.RNGUICollectionViewManagerDelegate
import com.facebook.react.viewmanagers.RNGUICollectionViewManagerInterface

/**
 * The Android view manager for `RNGUICollectionView`.
 *
 * Implementing `RNGUICollectionViewManagerInterface` is the guarantee this file is built on: that
 * interface is generated from `src/specs/RNGUICollectionViewNativeComponent.ts`, so the Kotlin
 * compiler fails the build the moment a prop is added, removed or retyped on the JavaScript side.
 * A prop cannot be forgotten on one platform. The signatures are not ours to choose, and the
 * bodies that are still `Unit` say which milestone owns them rather than pretending to be done.
 */
@ReactModule(name = RNGUICollectionViewManager.NAME)
class RNGUICollectionViewManager :
  ViewGroupManager<RNGUICollectionViewView>(),
  RNGUICollectionViewManagerInterface<RNGUICollectionViewView> {
  private val delegate: ViewManagerDelegate<RNGUICollectionViewView> =
    RNGUICollectionViewManagerDelegate<RNGUICollectionViewView, RNGUICollectionViewManager>(this)

  override fun getDelegate(): ViewManagerDelegate<RNGUICollectionViewView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(reactContext: ThemedReactContext): RNGUICollectionViewView =
    RNGUICollectionViewView(reactContext)

  // -----------------------------------------------------------------------------------------
  // Hosted children
  //
  // React's children here are *only* the hosted subtrees. Everything else in the view — the
  // RecyclerView, the parking bay, the scrubber — is ours, so every one of these has to route to
  // the bay rather than to the FrameLayout. Leaving them at the default would have React counting
  // our views among its own, and `hostIndex` would address the wrong thing.
  // -----------------------------------------------------------------------------------------

  /**
   * The Android analogue of overriding `mountChildComponentView:` on iOS.
   *
   * Without it Fabric positions hosted children itself, straight from the Yoga result, and they
   * pile up at the origin — every hosted row drawn on top of every other, at the top of the list.
   */
  override fun needsCustomLayoutForChildren(): Boolean = true

  override fun addView(parent: RNGUICollectionViewView, child: View, index: Int) {
    parent.addHostChild(child, index)
  }

  override fun getChildCount(parent: RNGUICollectionViewView): Int = parent.hostChildCount

  override fun getChildAt(parent: RNGUICollectionViewView, index: Int): View? =
    parent.hostChildAt(index)

  override fun removeViewAt(parent: RNGUICollectionViewView, index: Int) {
    parent.removeHostChildAt(index)
  }

  override fun removeAllViews(parent: RNGUICollectionViewView) {
    parent.removeAllHostChildren()
  }

  // ---------------------------------------------------------------------------------------------
  // Props
  //
  // Every one is accepted and discarded. They are written out rather than collapsed behind a
  // helper because each override is a compile-time assertion that this file still matches the
  // spec — which is the entire value of the stub.
  // ---------------------------------------------------------------------------------------------

  // Three props are stashed rather than applied. Fabric writes props one setter at a time in no
  // guaranteed order, so acting here would mean decoding a tree against the wrong revision half
  // the time; `onAfterUpdateTransaction` below is where they land together.

  override fun setTree(
    view: RNGUICollectionViewView,
    value: String?,
  ) {
    view.pendingTree = value
  }

  override fun setRevision(
    view: RNGUICollectionViewView,
    value: Int,
  ) {
    view.pendingRevision = value
  }

  override fun setColorScheme(
    view: RNGUICollectionViewView,
    value: String?,
  ) {
    view.pendingColorScheme = ColorScheme.from(value)
  }

  /**
   * The Android analogue of iOS's `updateProps`: called once after every prop in a transaction has
   * been written, which is the only point at which `tree` and `revision` are both current.
   */
  override fun onAfterUpdateTransaction(view: RNGUICollectionViewView) {
    super.onAfterUpdateTransaction(view)
    view.commitProps()
  }

  /**
   * Maps each event class's name to the prop React should call.
   *
   * Without an entry here the dispatcher cannot resolve the name and drops the event in silence —
   * no warning, no crash, just a list that ignores taps. Which is why `RowPressEvent.NAME` is the
   * `topRowPress` form rather than `onRowPress`.
   */
  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
    mutableMapOf(
      RowPressEvent.NAME to MapBuilder.of("registrationName", "onRowPress"),
      SectionActionEvent.NAME to MapBuilder.of("registrationName", "onSectionAction"),
      ScrollEvent.SCROLL to MapBuilder.of("registrationName", "onScroll"),
      ScrollEvent.BEGIN_DRAG to MapBuilder.of("registrationName", "onScrollBeginDrag"),
      ScrollEvent.END_DRAG to MapBuilder.of("registrationName", "onScrollEndDrag"),
      ScrollEvent.MOMENTUM_BEGIN to MapBuilder.of("registrationName", "onMomentumScrollBegin"),
      ScrollEvent.MOMENTUM_END to MapBuilder.of("registrationName", "onMomentumScrollEnd"),
      ContentSizeChangeEvent.NAME to MapBuilder.of("registrationName", "onContentSizeChange"),
      RowValueEvent.SWITCH to MapBuilder.of("registrationName", "onSwitchChange"),
      RowValueEvent.TEXT to MapBuilder.of("registrationName", "onTextChange"),
      RowValueEvent.FOCUS to MapBuilder.of("registrationName", "onFocusChange"),
      RowValueEvent.DATE to MapBuilder.of("registrationName", "onDateChange"),
      RowValueEvent.MENU to MapBuilder.of("registrationName", "onMenuSelect"),
      RowValueEvent.SWIPE to MapBuilder.of("registrationName", "onSwipeAction"),
      RowValueEvent.SLIDER to MapBuilder.of("registrationName", "onSliderChange"),
      RowValueEvent.SLIDER_COMMIT to MapBuilder.of("registrationName", "onSliderCommit"),
      VisibleRangeChangeEvent.NAME to MapBuilder.of("registrationName", "onVisibleRangeChange"),
      RefreshEvent.NAME to MapBuilder.of("registrationName", "onRefresh"),
    )

  override fun setShowsSectionIndex(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.pendingShowsSectionIndex = value
  }

  /**
   * A **documented no-op on Android**, and one of the handful this library has.
   *
   * The iOS prop sets the vertical space per letter in the A–Z rail, and exists to stop a naive
   * implementation stretching that rail across the whole screen. Android's affordance is a
   * fast-scroller thumb rather than a rail — see `SectionIndexView` for why porting the rail would
   * be the wrong idiom — so there is nothing here whose per-letter height could be set.
   *
   * Accepted rather than removed because the prop is part of the shared JS contract, and a screen
   * that sets it must keep compiling for both platforms.
   */
  override fun setSectionIndexRowHeight(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setSectionIndexShowsCallout(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.setSectionIndexShowsCallout(value)
  }

  override fun setShowsVerticalScrollIndicator(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.setShowsVerticalScrollIndicator(value)
  }

  override fun setContentInsetTop(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.contentInsetTop = value.toInt()
  }

  override fun setContentInsetLeft(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.contentInsetLeft = value.toInt()
  }

  override fun setContentInsetBottom(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.contentInsetBottom = value.toInt()
  }

  override fun setContentInsetRight(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.contentInsetRight = value.toInt()
  }

  override fun setContentInsetAdjustmentBehavior(
    view: RNGUICollectionViewView,
    value: String?,
  ) {
    view.setInsetAdjustment(InsetAdjustment.from(value))
  }

  /** Sugar over `contentInsetAdjustmentBehavior`: `false` pins the behaviour to `never`. */
  override fun setAutomaticallyAdjustContentInsets(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    if (!value) view.setInsetAdjustment(InsetAdjustment.never)
  }

  /**
   * A **documented no-op on Android**. The scroll indicator is drawn inside the list's padding
   * already, so there is no separate indicator inset to adjust — `clipToPadding = false` is what
   * makes the content pass under the bars, and the indicator was never clipped to begin with.
   */
  override fun setAutomaticallyAdjustsScrollIndicatorInsets(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setAutomaticallyAdjustKeyboardInsets(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.setAdjustsForKeyboard(value)
  }

  /** A superset of `automaticallyAdjustKeyboardInsets`; the focus-following half lands in M7. */
  override fun setKeyboardAware(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.setKeyboardAware(value)
  }

  override fun setKeyboardAwareOffset(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.keyboardAwareOffset = value.toInt()
  }

  override fun setKeyboardShouldPersistTaps(
    view: RNGUICollectionViewView,
    value: String?,
  ) {
    view.persistTaps = RNGUICollectionViewView.PersistTaps.from(value)
  }

  override fun setKeyboardDismissMode(
    view: RNGUICollectionViewView,
    value: String?,
  ) {
    view.setKeyboardDismissMode(KeyboardDismissMode.from(value))
  }

  override fun setScrollEnabled(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.setScrollEnabled(value)
  }

  override fun setDecelerationRate(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.setDecelerationRate(value)
  }

  override fun setTracksScroll(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.setTracksScroll(value)
  }

  override fun setTracksVisibleRange(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.tracksVisibleRange = value
  }

  // ---------------------------------------------------------------------------------------------
  // Pull to refresh
  //
  // Two of these are stashed rather than applied, for the same reason `tree` and `revision` are —
  // see `commitProps`. The rest are order-independent.
  // ---------------------------------------------------------------------------------------------

  override fun setRefreshEnabled(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.pendingRefreshEnabled = value
  }

  override fun setRefreshing(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) {
    view.pendingRefreshing = value
  }

  override fun setRefreshProgressViewOffset(
    view: RNGUICollectionViewView,
    value: Float,
  ) {
    view.setRefreshProgressViewOffset(value)
  }

  /**
   * A **documented no-op on Android**, and the one that most looks like an oversight.
   *
   * `tintColor` is `RefreshControl`'s iOS-only prop, and `SwipeRefreshLayout`'s equivalent is
   * `colors` — which is a *list*, because Material's indicator cycles through them. Rather than
   * quietly promoting one to the other, an unset `colors` resolves through the list's own
   * `appearance.tintColor` instead, which is the value a themed screen already set and the one it
   * would have wanted. See `applyRefreshColors`.
   */
  override fun setRefreshTintColor(
    view: RNGUICollectionViewView,
    value: Int?,
  ) = Unit

  /**
   * A **documented no-op on Android**. Material's indicator is a bare circle with no room for a
   * caption, and there is no M3 affordance that adds one.
   */
  override fun setRefreshTitle(
    view: RNGUICollectionViewView,
    value: String?,
  ) = Unit

  /** A **documented no-op on Android**, for the same reason as `refreshTitle`. */
  override fun setRefreshTitleColor(
    view: RNGUICollectionViewView,
    value: Int?,
  ) = Unit

  /**
   * The colours arrive pre-processed: codegen emits `processColorArray` into the view config, so
   * every element is already an integer by the time it reaches here.
   */
  override fun setRefreshColors(
    view: RNGUICollectionViewView,
    value: ReadableArray?,
  ) {
    if (value == null || value.size() == 0) {
      view.setRefreshColors(null)
      return
    }
    val colors = IntArray(value.size()) { value.getInt(it) ?: 0 }
    view.setRefreshColors(colors)
  }

  override fun setRefreshProgressBackgroundColor(
    view: RNGUICollectionViewView,
    value: Int?,
  ) {
    view.setRefreshProgressBackgroundColor(value)
  }

  override fun setRefreshSize(
    view: RNGUICollectionViewView,
    value: String?,
  ) {
    view.setRefreshSize(value == "large")
  }

  /**
   * A command rather than a prop, because the caller is reanimated rather than React.
   *
   * Called from a worklet on every frame of a bottom-sheet drag, so it has to be cheap. `x` is
   * ignored: the list scrolls on one axis, and M9's chip strips scroll themselves.
   */
  override fun scrollTo(
    view: RNGUICollectionViewView,
    x: Double,
    y: Double,
    animated: Boolean,
  ) {
    view.scrollTo(y, animated)
  }

  /**
   * A command rather than a prop, because the case it corrects is the one where *no prop changed*.
   *
   * A pull starts the spinner natively while JavaScript's `refreshing` is still `false`. A caller
   * who does nothing in `onRefresh` therefore produces no prop update at all, so
   * `onAfterUpdateTransaction` never runs and the spinner would never stop. `Root` notices the
   * disagreement after its own render and sends this. Same name, same reason, as React Native's
   * own `RefreshControl`.
   */
  override fun setNativeRefreshing(
    view: RNGUICollectionViewView,
    refreshing: Boolean,
  ) {
    view.setNativeRefreshing(refreshing)
  }

  companion object {
    /**
     * Shared with `codegenConfig.name` and with the component name in the spec. Not the npm
     * package name — renaming on npm leaves generated code untouched, renaming this does not.
     */
    const val NAME = "RNGUICollectionView"
  }
}

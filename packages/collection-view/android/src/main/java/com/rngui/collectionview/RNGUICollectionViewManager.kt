package com.rngui.collectionview

import com.facebook.react.common.MapBuilder
import com.facebook.react.module.annotations.ReactModule
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
      VisibleRangeChangeEvent.NAME to MapBuilder.of("registrationName", "onVisibleRangeChange"),
    )

  override fun setShowsSectionIndex(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setSectionIndexRowHeight(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setSectionIndexShowsCallout(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setShowsVerticalScrollIndicator(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setContentInsetTop(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setContentInsetLeft(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setContentInsetBottom(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setContentInsetRight(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setContentInsetAdjustmentBehavior(
    view: RNGUICollectionViewView,
    value: String?,
  ) = Unit

  override fun setAutomaticallyAdjustContentInsets(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setAutomaticallyAdjustsScrollIndicatorInsets(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setAutomaticallyAdjustKeyboardInsets(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setKeyboardAware(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setKeyboardAwareOffset(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setKeyboardDismissMode(
    view: RNGUICollectionViewView,
    value: String?,
  ) = Unit

  override fun setScrollEnabled(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setDecelerationRate(
    view: RNGUICollectionViewView,
    value: Float,
  ) = Unit

  override fun setTracksScroll(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

  override fun setTracksVisibleRange(
    view: RNGUICollectionViewView,
    value: Boolean,
  ) = Unit

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

  companion object {
    /**
     * Shared with `codegenConfig.name` and with the component name in the spec. Not the npm
     * package name — renaming on npm leaves generated code untouched, renaming this does not.
     */
    const val NAME = "RNGUICollectionView"
  }
}

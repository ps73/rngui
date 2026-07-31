package com.rngui.collectionview

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.RNGUICollectionViewManagerDelegate
import com.facebook.react.viewmanagers.RNGUICollectionViewManagerInterface

/**
 * The Android view manager for `RNGUICollectionView` — a stub that accepts the full prop contract
 * and renders nothing.
 *
 * Implementing `RNGUICollectionViewManagerInterface` is what makes this a stub rather than a
 * guess: that interface is generated from `src/specs/RNGUICollectionViewNativeComponent.ts`, so
 * the Kotlin compiler fails the build the moment a prop is added, removed or retyped on the
 * JavaScript side. The no-op bodies are the deliberate part; the signatures are not ours to
 * choose.
 *
 * No `getExportedCustomDirectEventTypeConstants`: the stub never dispatches an event, and
 * declaring event names nothing can emit would only suggest otherwise.
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

  override fun setTree(
    view: RNGUICollectionViewView,
    value: String?,
  ) = Unit

  override fun setRevision(
    view: RNGUICollectionViewView,
    value: Int,
  ) = Unit

  override fun setColorScheme(
    view: RNGUICollectionViewView,
    value: String?,
  ) = Unit

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
   * A command rather than a prop, and the delegate routes it here whether or not there is
   * anything to scroll. Nothing does — but reanimated calls this from a worklet on every frame
   * of a bottom-sheet drag, so it has to exist and has to be cheap.
   */
  override fun scrollTo(
    view: RNGUICollectionViewView,
    x: Double,
    y: Double,
    animated: Boolean,
  ) = Unit

  companion object {
    /**
     * Shared with `codegenConfig.name` and with the component name in the spec. Not the npm
     * package name — renaming on npm leaves generated code untouched, renaming this does not.
     */
    const val NAME = "RNGUICollectionView"
  }
}

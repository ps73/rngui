package com.rngui.collectionview

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.NativeGestureUtil
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The `SwipeRefreshLayout` wrapped around the list, with the four corrections it needs to be safe
 * here.
 *
 * **Subclassing is not decoration.** Stock `SwipeRefreshLayout` is actively hostile to a view tree
 * with anything else in it, and this library's tree has plenty: swipe action trays, chip strips,
 * a fast-scroller thumb, and react-native-gesture-handler coordinating with a bottom sheet. Each
 * override below is load-bearing, and each is the same one React Native makes in
 * `ReactSwipeRefreshLayout` — the shape is deliberately close to it, because a reader who knows
 * that class should recognise this one.
 *
 * The wrapper exists on every list, disabled until a `refreshControl` asks for it. Creating it
 * lazily would mean re-parenting a live `RecyclerView`, which resets nested-scrolling
 * registration, re-runs `onAttachedToWindow` and cancels any smooth scroller in flight — a lot of
 * moving parts to reopen in order to save one `ViewGroup` that, while disabled, returns `false`
 * from the first line of both `onInterceptTouchEvent` and `onStartNestedScroll`.
 */
class PullToRefreshLayout(context: ThemedReactContext) : SwipeRefreshLayout(context) {
  private var didLayout = false
  private var wantsRefreshing = false
  private var offsetDip = 0f

  /**
   * How far down the window the list's content actually starts.
   *
   * The list is edge-to-edge — `clipToPadding = false`, with the system bars folded into the
   * `RecyclerView`'s padding by [InsetController] — but this wrapper's own origin is the top of
   * the window. Left alone, the indicator would come to rest under the status bar. React Native
   * has no equivalent because its `ScrollView` is not doing the inset resolution this list is.
   */
  var topInsetPx: Int = 0
    set(value) {
      if (field == value) return
      field = value
      applyProgressViewOffset()
    }

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var downX = 0f
  private var horizontal = false
  private var nativeGestureStarted = false

  /**
   * Deferred until the first layout, because before it `SwipeRefreshLayout` silently drops this.
   *
   * A known AOSP bug (b/77712), and the reason `refreshControl={<RefreshControl refreshing />}` on
   * first mount would otherwise do nothing at all.
   */
  override fun setRefreshing(refreshing: Boolean) {
    wantsRefreshing = refreshing
    if (didLayout) super.setRefreshing(refreshing)
  }

  /** Points, as the prop is expressed. Folded together with [topInsetPx]. */
  fun setProgressViewOffsetDip(dip: Float) {
    offsetDip = dip
    applyProgressViewOffset()
  }

  /**
   * `progressCircleDiameter` is only meaningful once the circle has been measured, so this is a
   * no-op before the first layout and is re-run from [onLayout].
   */
  private fun applyProgressViewOffset() {
    if (!didLayout) return
    val diameter = progressCircleDiameter
    val offsetPx = (offsetDip * resources.displayMetrics.density).roundToInt() + topInsetPx
    val targetPx = (CIRCLE_TARGET_DIP * resources.displayMetrics.density).roundToInt()
    setProgressViewOffset(false, offsetPx - diameter, offsetPx + targetPx - diameter)
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (didLayout) return
    didLayout = true
    applyProgressViewOffset()
    setRefreshing(wantsRefreshing)
  }

  /** The `RecyclerView`, whose `canScrollVertically` runs through `LockableLayoutManager` — so a
   * list locked by a bottom sheet reports "cannot scroll up" and the pull is refused with it. */
  override fun canChildScrollUp(): Boolean =
    getChildAt(0)?.canScrollVertically(-1) ?: super.canChildScrollUp()

  /**
   * Forwards instead of swallowing, which is what `SwipeRefreshLayout` does with this.
   *
   * Swallowing it means nothing *above* this view ever hears "stop intercepting" — and above this
   * view are the `FrameLayout` that answers react-native-gesture-handler's probe and, through it,
   * `@gorhom/bottom-sheet`. Below it, `ItemTouchHelper` and `ChipStripView` are the ones asking.
   */
  override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    if (!isVerticalEnough(ev) || !super.onInterceptTouchEvent(ev)) return false
    // Tells React Native's touch dispatcher a native view has taken the gesture, so a JS responder
    // higher up releases rather than tracking a drag it will never see the end of.
    NativeGestureUtil.notifyNativeGestureStarted(this, ev)
    nativeGestureStarted = true
    // Without this, a parent that starts intercepting mid-pull leaves the indicator stuck
    // on-screen with no gesture left to finish it.
    parent?.requestDisallowInterceptTouchEvent(true)
    return true
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(ev: MotionEvent): Boolean {
    if (ev.actionMasked == MotionEvent.ACTION_UP && nativeGestureStarted) {
      NativeGestureUtil.notifyNativeGestureEnded(this, ev)
      nativeGestureStarted = false
    }
    return super.onTouchEvent(ev)
  }

  /**
   * Refuses a drag that is going sideways, which `SwipeRefreshLayout` would otherwise claim.
   *
   * It never calls `super.onInterceptTouchEvent`, so `disallowIntercept` does not reach its own
   * interception either — leaving it to grab horizontal drags. In this library every swipe action
   * and every chip strip is a horizontal drag, so without this check the wrapper would break them
   * on every list, whether or not anyone asked for pull-to-refresh.
   */
  private fun isVerticalEnough(ev: MotionEvent): Boolean {
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = ev.x
        horizontal = false
      }
      MotionEvent.ACTION_MOVE -> {
        if (horizontal || abs(ev.x - downX) > touchSlop) {
          horizontal = true
          return false
        }
      }
    }
    return true
  }

  private companion object {
    /** `SwipeRefreshLayout.DEFAULT_CIRCLE_TARGET`, which is not public. */
    const val CIRCLE_TARGET_DIP = 64f
  }
}

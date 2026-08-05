package com.rngui.collectionview

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The list itself, with the two scroll behaviours that have no property to set.
 *
 * Both exist for `@gorhom/bottom-sheet`, which drives them from a worklet on every frame of a
 * drag — so neither can cost a React commit.
 */
class CollectionRecyclerView(context: Context) : RecyclerView(context) {

  /**
   * `UIScrollView.decelerationRate`, raw. Negative means "leave the platform's own value alone".
   *
   * **`0` is exact and the rest are approximations, in that order of importance.** Zero is the
   * value the sheet actually sends — stop dead, so the drag that follows moves the sheet rather
   * than continuing an inherited fling — and it maps perfectly, because refusing to fling is
   * something `RecyclerView` can do precisely. Other values scale the fling velocity, which is a
   * different curve from iOS's friction coefficient and is documented as approximate rather than
   * quietly presented as a port.
   */
  var decelerationRate: Float = UNSET

  override fun fling(velocityX: Int, velocityY: Int): Boolean {
    if (decelerationRate == 0f) return false
    if (decelerationRate < 0f) return super.fling(velocityX, velocityY)

    // iOS's normal rate is 0.998 and fast is 0.99 — a *lower* number coasts less. Mapping the
    // useful part of that range onto a velocity scale keeps the two ends behaving the same way
    // round, which is as much fidelity as this can honestly claim.
    val scale = ((decelerationRate - 0.99f) / 0.008f).coerceIn(0.2f, 1f)
    return super.fling(velocityX, (velocityY * scale).toInt())
  }

  companion object {
    const val UNSET = -1f
  }
}

/**
 * A layout manager whose scrolling can be switched off.
 *
 * `scrollEnabled` is how `@rngui/collection-view/bottom-sheet` keeps a sheet and its list from
 * fighting: rather than correcting the list after the fact, the second gesture is removed for as
 * long as the sheet owns the drag.
 *
 * Answering `canScrollVertically()` is the right lever rather than swallowing touch events,
 * because it is also the question `react-native-gesture-handler`'s `NativeViewGestureHandler` asks
 * when deciding whether a view is scrollable — so turning it off tells RNGH the same thing it
 * tells the touch dispatcher, instead of leaving the two with different answers.
 *
 * Programmatic scrolling is deliberately *not* gated on it. `scrollToPosition` sets a pending
 * position rather than scrolling, so `scrollTo(0, 0)` still lands even while the list is locked —
 * which is exactly the combination a sheet drag needs.
 */
class LockableLayoutManager(context: Context) : LinearLayoutManager(context) {
  var scrollEnabled: Boolean = true

  override fun canScrollVertically(): Boolean = scrollEnabled && super.canScrollVertically()
}

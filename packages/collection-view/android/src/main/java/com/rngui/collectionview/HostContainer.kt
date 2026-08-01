package com.rngui.collectionview

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * The parking bay for hosted React children, and the holder that claims one.
 *
 * A `host` row is the one kind that cannot recycle: every other row is a description that any
 * pooled cell can render, while a hosted row is a distinct React subtree with its own state. So
 * React mounts the children into [ParkingView] — off screen, but *mounted*, because React owns
 * their lifetime and will unmount them on its own schedule — and a holder borrows the one it needs
 * for as long as it is on screen.
 */
class ParkingView(context: Context) : FrameLayout(context) {
  init {
    // Present in the hierarchy so React's mounting layer has somewhere to put children, and
    // invisible so nothing it holds is drawn while parked. Zero-sized rather than GONE: a GONE
    // parent skips layout for its children, and Fabric has already laid these out.
    visibility = INVISIBLE
    isEnabled = false
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(0, 0)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
}

/**
 * The cell a hosted child is displayed in.
 *
 * Sized from `RowSpec.height`, which is always a number JavaScript decided — either the caller
 * stated it or `Root` measured the mounted subtree with `onLayout` and sent it back. Native never
 * measures a hosted subtree: Fabric lays it out with Yoga, so the view has no intrinsic size and
 * asking for one measures it as zero.
 */
class HostContainer(context: Context) : FrameLayout(context) {

  /** The child this container currently displays, if any. */
  var hosted: View? = null
    private set

  /**
   * Takes a child out of the bay and displays it.
   *
   * Nothing happens if this container already has it, which keeps a rebind of an unchanged row
   * from removing and re-adding a live React subtree.
   */
  fun claim(child: View, parking: ParkingView) {
    if (hosted === child && child.parent === this) return
    release(parking)
    (child.parent as? android.view.ViewGroup)?.removeView(child)
    child.visibility = VISIBLE
    addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    hosted = child
  }

  /**
   * Returns the child to the bay — **only if this container still owns it.**
   *
   * That guard is load-bearing, and it cost two wrong diagnoses on iOS before anyone looked
   * properly. `onBindViewHolder` for the *incoming* holder can run before `onViewRecycled` for the
   * outgoing one, exactly as UIKit configures a replacement cell before recycling its predecessor
   * — so by the time this runs, the view may already have been claimed by the holder that replaced
   * this one. Removing it then blanks a row that is on screen and correct, and the symptom is
   * "hosted rows randomly go empty after a reload", which reads like a React problem.
   */
  fun release(parking: ParkingView) {
    val child = hosted ?: return
    hosted = null
    if (child.parent !== this) return

    removeView(child)
    child.visibility = INVISIBLE
    // Back to the bay rather than left orphaned. React still owns this view; a view with no parent
    // at all is one mounting transaction away from a crash, and the bay is also where the next
    // holder to claim it will look.
    parking.addView(child)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    // Fabric assigned the subtree's internal frames already; all this has to do is fill the cell.
    hosted?.layout(0, 0, r - l, b - t)
  }
}

package com.rngui.collectionview

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.ColorUtils

/**
 * One row's Material 3 container — and the transition between two of its states.
 *
 * ```
 *  unselected                        selected
 *  ╭──────────────────────────╮      ⟨──────────────────────────⟩
 *  │ Flag                  ◯──│  →   │ Flag                  ──◉ │
 *  ╰──────────────────────────╯      ⟨──────────────────────────⟩
 *   surfaceContainer, 16dp            secondaryContainer, 28dp
 * ```
 *
 * **The change has to be animated, because the shape is the signal.** M3 grows a selected item's
 * corners to a stadium, and a shape that arrives instantly reads as a redraw — the eye registers
 * that something is different without registering *what*. Every other part of the row already
 * moves: `MaterialSwitch` slides its thumb, `MaterialCheckBox` draws its tick on, and the container
 * jumping under both of them was the one thing on the row that did not.
 *
 * **One drawable per row, held for the row's life, rather than a fresh one per bind.** That is what
 * makes an animation possible at all — you cannot tween between two drawables one of which has just
 * replaced the other — and it also stops a fling allocating three drawables per row per bind, which
 * is the difference between a list that drops frames and one that does not.
 */
class RowContainer(private val view: View) {

  /**
   * The live radii, in the order `GradientDrawable.cornerRadii` wants.
   *
   * **One array, held by both drawables.** `GradientDrawable` stores the reference rather than a
   * copy, so mutating this in place and re-assigning marks each path dirty without allocating on
   * every animation frame. Sharing it between the fill and the mask is deliberate: the mask must
   * match the fill *exactly*, and a mask a frame behind paints ripple ink outside the corner —
   * which is the tell this whole class exists to avoid.
   */
  private val radii = FloatArray(8)

  @ColorInt private var fillColor: Int = Color.TRANSPARENT

  @ColorInt private var rippleColor: Int = Color.TRANSPARENT

  private val fill = GradientDrawable().apply { setColor(Color.TRANSPARENT) }

  private val mask = GradientDrawable().apply { setColor(Color.WHITE) }

  private val ripple = RippleDrawable(ColorStateList.valueOf(Color.TRANSPARENT), fill, mask)

  private var animator: ValueAnimator? = null

  /** The endpoints of an in-flight transition. Fields so a frame allocates nothing. */
  private val from = FloatArray(8)
  private val to = FloatArray(8)

  init {
    view.background = ripple
  }

  /**
   * Puts the container into the state this row is in now.
   *
   * `animate` is the caller's answer to "is this the *same* row changing state?", and it has to be,
   * because this class cannot tell the difference. A holder recycled onto a different row arrives
   * with a different shape for a reason that has nothing to do with state — and tweening into it
   * would mean every row on screen morphing during a fling, which is exactly the garbage the
   * animation is meant not to look like. See [RowView.bind].
   */
  fun apply(
    position: Item.Position,
    listStyle: ListStyle,
    selected: Boolean,
    animate: Boolean,
  ) {
    val nextRadii =
      GroupShape.radii(view.context, position, listStyle.style, listStyle.grouped, selected)
    val nextFill =
      GroupShape.fill(
        style = listStyle.style,
        grouped = listStyle.grouped,
        selected = selected,
        rowBackground = listStyle.rowBackground,
        container = listStyle.containerColor,
        selectedContainer = listStyle.selectedContainer,
      )

    setRipple(GroupShape.ripple(listStyle.labelColor))

    // Whatever was in flight is about to be contradicted, whichever branch follows.
    animator?.cancel()
    animator = null

    if (!animate) {
      write(nextRadii, nextFill)
      return
    }

    System.arraycopy(radii, 0, from, 0, radii.size)
    System.arraycopy(nextRadii, 0, to, 0, to.size)
    val fromFill = fillColor

    animator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        // The system's animator scale applies to this, so "Remove animations" in the developer
        // options and the accessibility shortcut that turns motion off both land the row on its end
        // state immediately, with no branch here to keep in step.
        duration = Motion.stateChangeDuration(view.context)
        interpolator = Motion.stateChangeEasing(view.context)
        addUpdateListener { running ->
          // Already through the interpolator.
          val t = running.animatedFraction
          for (i in radii.indices) radii[i] = from[i] + (to[i] - from[i]) * t
          write(radii, ColorUtils.blendARGB(fromFill, nextFill, t))
        }
        start()
      }
  }

  private fun setRipple(@ColorInt color: Int) {
    if (color == rippleColor) return
    rippleColor = color
    ripple.setColor(ColorStateList.valueOf(color))
  }

  private fun write(next: FloatArray, @ColorInt color: Int) {
    if (next !== radii) System.arraycopy(next, 0, radii, 0, radii.size)
    fill.cornerRadii = radii
    mask.cornerRadii = radii
    if (color != fillColor) {
      fillColor = color
      fill.setColor(color)
    }
  }

  /** The radius currently drawn at the top-left corner. */
  @VisibleForTesting fun currentRadius(): Float = radii[0]

  /** The colour currently filling the container. */
  @ColorInt @VisibleForTesting fun currentFill(): Int = fillColor

  /** Jumps an in-flight transition to its end, so a test never waits on wall-clock time. */
  @VisibleForTesting fun settle() = animator?.end() ?: Unit
}

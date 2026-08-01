package com.rngui.collectionview

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.ListAppearance

/**
 * The grouped-card look, and the reason `insetGrouped` is a *native* Android style now rather than
 * an iOS import.
 *
 * Pixel Settings draws a group as one rounded card with the rows stacked inside it: the first row
 * takes large leading corners, the last takes large trailing corners, and the middles are nearly
 * square. That is the M3 Expressive treatment, it is what an Android user expects a settings group
 * to look like, and it happens to be exactly the shape `UICollectionLayoutListConfiguration`
 * produces on iOS. The two platforms agree here by coincidence rather than by imitation, which is
 * the best kind of parity this package can have.
 *
 * `GradientDrawable` rather than Material Components' `MaterialShapeDrawable`. It draws the same
 * per-corner radii, and pulling in `com.google.android.material` for a rounded rectangle would put
 * a large dependency — and a theme requirement, since Material views need a Material theme — into
 * an AAR that has no other use for one. M4's stretch goal, the interactive shape morph, is the
 * thing that would justify it.
 */
object GroupShape {
  /** iOS uses 10pt; Pixel Settings is squarer at the joins and rounder at the ends. */
  private const val LARGE_DP = 20f
  private const val SMALL_DP = 4f

  /** The horizontal breathing room `insetGrouped` puts around each card. */
  const val INSET_DP = 16

  /**
   * Per-corner radii, in the order `GradientDrawable.cornerRadii` wants: top-left, top-right,
   * bottom-right, bottom-left, each as an x/y pair.
   */
  private fun radii(context: Context, position: Item.Position, rounded: Boolean): FloatArray {
    if (!rounded) return FloatArray(8)
    val large = LARGE_DP * context.resources.displayMetrics.density
    val small = SMALL_DP * context.resources.displayMetrics.density
    val (top, bottom) =
      when (position) {
        Item.Position.only -> large to large
        Item.Position.first -> large to small
        Item.Position.middle -> small to small
        Item.Position.last -> small to large
      }
    return floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
  }

  /**
   * The row's background, with press feedback clipped to its shape.
   *
   * **The mask is the whole point.** A `RippleDrawable` with no mask paints ink over the rounded
   * corner and out into the gap between cards, and an unclipped ripple on a rounded card is the
   * single most obvious tell that a list was not built for this platform — more obvious than the
   * wrong radius, because it only appears under the finger and so reads as a glitch rather than as
   * a style.
   */
  fun background(
    context: Context,
    position: Item.Position,
    appearance: ListAppearance,
    @ColorInt rowBackground: Int?,
    @ColorInt labelColor: Int,
  ): Drawable {
    val rounded = appearance != ListAppearance.plain
    val corners = radii(context, position, rounded)

    val fill =
      GradientDrawable().apply {
        cornerRadii = corners
        // `plain` is edge-to-edge with no card, so an unthemed plain list draws nothing at all and
        // lets whatever is behind it show through — which is what "the platform's own colour"
        // means for a style that has no card.
        setColor(rowBackground ?: if (rounded) defaultCard(labelColor) else Color.TRANSPARENT)
      }

    val mask = GradientDrawable().apply {
      cornerRadii = corners
      setColor(Color.WHITE)
    }

    // Derived from the label colour rather than fixed, so the ripple lightens a dark row and
    // darkens a light one from one rule — the same trick `rnguiOverlaid(with:alpha:)` plays on iOS
    // by overlaying the dynamic `label` colour.
    val ripple = (labelColor and 0x00FFFFFF) or (RIPPLE_ALPHA shl 24)

    return RippleDrawable(ColorStateList.valueOf(ripple), fill, mask)
  }

  /**
   * The card colour when `appearance.rowBackground` is unset.
   *
   * Read off the label colour, which already encodes the mode: a white label means a dark list, so
   * the card is a light-on-dark surface. One rule, no second source of truth about which mode is
   * in force.
   */
  @ColorInt
  private fun defaultCard(@ColorInt labelColor: Int): Int =
    if (isLight(labelColor)) SURFACE_DARK else SURFACE_LIGHT

  /** The background behind the cards. */
  @ColorInt
  fun defaultBackground(@ColorInt labelColor: Int): Int =
    if (isLight(labelColor)) BACKGROUND_DARK else BACKGROUND_LIGHT

  private fun isLight(@ColorInt color: Int): Boolean =
    (Color.red(color) + Color.green(color) + Color.blue(color)) > 3 * 128

  private const val RIPPLE_ALPHA = 0x1F
  private const val SURFACE_LIGHT = 0xFFFFFFFF.toInt()
  private const val SURFACE_DARK = 0xFF1C1C1E.toInt()
  private const val BACKGROUND_LIGHT = 0xFFF2F2F7.toInt()
  private const val BACKGROUND_DARK = 0xFF000000.toInt()
}

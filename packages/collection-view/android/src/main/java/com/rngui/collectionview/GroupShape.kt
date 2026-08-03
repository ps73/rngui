package com.rngui.collectionview

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.AndroidListStyle

/**
 * The Material 3 list item container: its shape, its fill and its press feedback.
 *
 * The [M3 spec](https://m3.material.io/components/lists/specs) defines two styles, and they differ
 * in more than corner radius:
 *
 * ```
 *  standard                        segmented
 *  ┌──────────────────────────┐    ╭──────────────────────────╮
 *  │ List item                │    │ List item                │
 *  ├──────────────────────────┤    ╰──────────────────────────╯
 *  │ List item                │      ← 4dp gap
 *  └──────────────────────────┘    ╭──────────────────────────╮
 *   flush, divider between         │ List item                │
 *                                  ╰──────────────────────────╯
 * ```
 *
 * **A selected item takes `secondaryContainer` and a larger radius in either style**, and that is
 * the part that reads as Expressive rather than as a rounded rectangle: the *shape* changes to
 * signal state, not only the colour. It is why `selected` is a parameter here rather than something
 * the row paints on top afterwards.
 */
object GroupShape {
  /** `shape.corner.large`, for the ends of a grouped run. */
  private const val LARGE_DP = 16f

  /** `shape.corner.extraSmall`, for the joins inside one. */
  private const val SMALL_DP = 4f

  /** A segmented item is a container in its own right, so every corner is the same. */
  private const val SEGMENTED_DP = 12f

  /**
   * The selected state.
   *
   * M3 Expressive grows the container when it is chosen — a full stadium is what the spec's own
   * selected-item illustrations show, and it is what makes selection legible without relying on
   * colour alone.
   */
  private const val SELECTED_DP = 28f

  /** The horizontal breathing room `insetGrouped` puts around each container. */
  const val INSET_DP = 16

  /** The vertical gap between segmented items. */
  const val SEGMENT_GAP_DP = 4

  /**
   * Per-corner radii, in the order `GradientDrawable.cornerRadii` wants: top-left, top-right,
   * bottom-right, bottom-left, each as an x/y pair.
   */
  fun radii(
    context: Context,
    position: Item.Position,
    style: AndroidListStyle,
    grouped: Boolean,
    selected: Boolean,
  ): FloatArray {
    val density = context.resources.displayMetrics.density

    // A selected item is a container of its own whatever the surrounding style, so it rounds every
    // corner rather than inheriting its neighbours' joins.
    if (selected) return FloatArray(8) { SELECTED_DP * density }
    if (style == AndroidListStyle.segmented) return FloatArray(8) { SEGMENTED_DP * density }
    if (!grouped) return FloatArray(8)

    val large = LARGE_DP * density
    val small = SMALL_DP * density
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
   * The item's background, with press feedback clipped to its shape.
   *
   * **The mask is the whole point.** A `RippleDrawable` with no mask paints ink over the rounded
   * corner and out into the gap between items — the single most obvious tell that a list was not
   * built for this platform, and one that only appears under the finger, so it reads as a glitch
   * rather than as a style.
   */
  fun background(
    context: Context,
    position: Item.Position,
    style: AndroidListStyle,
    grouped: Boolean,
    selected: Boolean,
    @ColorInt rowBackground: Int?,
    @ColorInt container: Int,
    @ColorInt selectedContainer: Int,
    @ColorInt rippleSource: Int,
  ): Drawable {
    val corners = radii(context, position, style, grouped, selected)

    val fill =
      GradientDrawable().apply {
        cornerRadii = corners
        setColor(
          when {
            selected -> selectedContainer
            rowBackground != null -> rowBackground
            // A `standard` item on a `plain` list draws nothing and lets the list surface show
            // through, which is what "the platform's own colour" means for a style that has no
            // container of its own.
            !grouped && style == AndroidListStyle.standard -> Color.TRANSPARENT
            else -> container
          }
        )
      }

    val mask =
      GradientDrawable().apply {
        cornerRadii = corners
        setColor(Color.WHITE)
      }

    // Derived from `onSurface` rather than fixed, so one rule lightens a dark row and darkens a
    // light one — M3 states the pressed state as 12% of the content colour.
    val ripple = (rippleSource and 0x00FFFFFF) or (RIPPLE_ALPHA shl 24)

    return RippleDrawable(ColorStateList.valueOf(ripple), fill, mask)
  }

  /** M3's pressed-state opacity. */
  private const val RIPPLE_ALPHA = 0x1F
}

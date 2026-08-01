package com.rngui.collectionview

import android.content.Context
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.ListAppearance

/**
 * The list-wide geometry: what the decoration needs and what shapes a row's card.
 *
 * Separate from [RowStyle], which is the per-row colour set. The split follows what consumes each:
 * `RowStyle` is read on every bind and is nothing but colours, while this is read once per draw
 * pass by the decoration and once per bind for the background shape. Resolving both in one place —
 * [of] — keeps them from disagreeing about which mode is in force.
 */
data class ListStyle(
  val appearance: ListAppearance,
  val insetPx: Int,
  val sectionSpacingPx: Int,
  @ColorInt val separatorColor: Int,
  val separatorInsetPx: Int,
  val separatorHeightPx: Int,
  @ColorInt val backgroundColor: Int,
  @ColorInt val rowBackground: Int?,
  @ColorInt val labelColor: Int,
) {
  companion object {
    /**
     * `sectionSpacing` unset keeps the platform's own gap, which for a grouped list is the space a
     * header sits in and for `plain` is nothing at all.
     */
    private const val DEFAULT_SECTION_SPACING_DP = 22
    private const val PLAIN_SECTION_SPACING_DP = 0

    /** Where a divider starts, measured from the card's leading edge. Lines up under the label. */
    private const val SEPARATOR_INSET_DP = 16

    fun of(
      context: Context,
      resolver: AppearanceResolver,
      rowStyle: RowStyle,
      appearance: ListAppearance,
    ): ListStyle {
      val grouped = appearance != ListAppearance.plain
      val spacing =
        resolver.dimension { it.sectionSpacing }?.toInt()
          ?: if (grouped) DEFAULT_SECTION_SPACING_DP else PLAIN_SECTION_SPACING_DP

      return ListStyle(
        appearance = appearance,
        insetPx = context.dp(GroupShape.INSET_DP),
        sectionSpacingPx = context.dp(spacing),
        separatorColor = rowStyle.separatorColor,
        separatorInsetPx = context.dp(SEPARATOR_INSET_DP),
        // A hairline, not a dp: on a 3x screen 1dp is three physical pixels and reads as a rule
        // rather than as a separator. Every platform list draws these at one pixel.
        separatorHeightPx = 1,
        backgroundColor =
          resolver.color({ it.background }, GroupShape.defaultBackground(rowStyle.labelColor)),
        rowBackground = rowStyle.rowBackground,
        labelColor = rowStyle.labelColor,
      )
    }
  }
}

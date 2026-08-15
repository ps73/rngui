package com.rngui.collectionview

import android.content.Context
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.AndroidListStyle
import com.rngui.collectionview.generated.ListAppearance

/**
 * The list-wide geometry and Material 3 surface roles.
 *
 * Separate from [RowStyle], which is the per-row colour set, because the two are consumed
 * differently: `RowStyle` is read on every bind, this is read once per draw pass by the decoration
 * and once per bind for the container shape. Both are resolved in one place so they cannot disagree
 * about which mode is in force.
 */
data class ListStyle(
  val appearance: ListAppearance,
  /** The M3 arrangement: flush items, or individually contained ones with gaps. */
  val style: AndroidListStyle,
  val insetPx: Int,
  val sectionSpacingPx: Int,
  /**
   * The gap above the first section, which defaults to [sectionSpacingPx] and is only its own
   * number when the caller says so. See `Appearance.firstSectionSpacing`: on iOS this gap is
   * reserved by UIKit and looks wrong on a screen without a large title, and a cross-platform
   * screen that closes it there has to be able to close it here too.
   */
  val firstSectionSpacingPx: Int,
  /** The gap between segmented items. Zero in `standard`, where items sit flush. */
  val itemGapPx: Int,
  @ColorInt val separatorColor: Int,
  val separatorInsetPx: Int,
  val separatorHeightPx: Int,
  /** `surface` — behind everything. */
  @ColorInt val backgroundColor: Int,
  /** `surfaceContainer` — the item container in a grouped or segmented list. */
  @ColorInt val containerColor: Int,
  @ColorInt val rowBackground: Int?,
  @ColorInt val labelColor: Int,
  @ColorInt val selectedContainer: Int,
) {
  /** Whether items sit in a container at all. `plain` + `standard` is the one case that does not. */
  val grouped: Boolean
    get() = appearance != ListAppearance.plain

  /** Dividers belong to `standard` only; a segmented list is separated by its gaps. */
  val drawsSeparators: Boolean
    get() = style == AndroidListStyle.standard

  companion object {
    /** M3's list subheader leaves this much air above the group it introduces. */
    private const val DEFAULT_SECTION_SPACING_DP = 16
    private const val PLAIN_SECTION_SPACING_DP = 0

    /** Where a divider starts, measured from the container's leading edge. Under the label. */
    private const val SEPARATOR_INSET_DP = 16

    /**
     * `segmented` for the grouped appearances, `standard` for `plain`.
     *
     * The mapping that makes an unchanged cross-platform screen look right on both: a screen asking
     * for `insetGrouped` wants distinct containers, which on Android is what segmented means, while
     * `plain` wants an unbroken run of items, which is standard.
     */
    fun defaultStyleFor(appearance: ListAppearance): AndroidListStyle =
      if (appearance == ListAppearance.plain) AndroidListStyle.standard
      else AndroidListStyle.segmented

    fun of(
      context: Context,
      resolver: AppearanceResolver,
      rowStyle: RowStyle,
      appearance: ListAppearance,
      requested: AndroidListStyle?,
    ): ListStyle {
      val style =
        requested?.takeIf { it != AndroidListStyle.unknown } ?: defaultStyleFor(appearance)
      val grouped = appearance != ListAppearance.plain
      val dark = resolver.isDark

      // Kept as the `Double` the tree sends rather than rounded on the way in. `dp` takes a
      // `Number` and converts once, so truncating here would only throw away a caller's `12.5`
      // before the conversion that knows what to do with it — and iOS, reading the same field,
      // would keep it.
      val spacing: Double =
        resolver.dimension { it.sectionSpacing }
          ?: (if (grouped) DEFAULT_SECTION_SPACING_DP else PLAIN_SECTION_SPACING_DP).toDouble()

      return ListStyle(
        appearance = appearance,
        style = style,
        insetPx = if (appearance == ListAppearance.insetGrouped) context.dp(GroupShape.INSET_DP) else 0,
        sectionSpacingPx = context.dp(spacing),
        firstSectionSpacingPx =
          context.dp(resolver.dimension { it.firstSectionSpacing } ?: spacing),
        itemGapPx =
          if (style == AndroidListStyle.segmented) context.dp(GroupShape.SEGMENT_GAP_DP) else 0,
        separatorColor = rowStyle.separatorColor,
        separatorInsetPx = context.dp(SEPARATOR_INSET_DP),
        // A hairline, not a dp: on a 3x screen 1dp is three physical pixels and reads as a rule
        // rather than as a divider. Every platform list draws these at one pixel.
        separatorHeightPx = 1,
        backgroundColor =
          resolver.color(
            { it.background },
            AppearanceResolver.COLOR_SURFACE,
            if (dark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt(),
          ),
        containerColor =
          resolver.token(
            AppearanceResolver.COLOR_SURFACE_CONTAINER,
            if (dark) 0xFF211F26.toInt() else 0xFFF3EDF7.toInt(),
          ),
        rowBackground = rowStyle.rowBackground,
        labelColor = rowStyle.labelColor,
        selectedContainer = rowStyle.selectedContainer,
      )
    }
  }
}

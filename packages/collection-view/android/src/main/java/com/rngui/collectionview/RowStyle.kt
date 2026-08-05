package com.rngui.collectionview

import android.graphics.Typeface
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.FontSpec

/**
 * The colours and type a row binds against, resolved once per tree.
 *
 * Every default is a **Material 3 colour role** rather than a transliterated iOS colour, so an
 * unthemed list follows the consuming app's Material theme — dynamic colour included — instead of
 * looking like an iOS list running on the wrong phone. Anything set in `appearance` still wins.
 *
 * Resolved once per tree rather than per row: a token lookup is a `TypedValue` resolution, and it
 * has no business happening 2,000 times during a fling. The literal fallbacks are the M3 baseline
 * palette, used only when the themed context somehow carries no Material theme at all.
 */
data class RowStyle(
  @ColorInt val labelColor: Int,
  @ColorInt val secondaryColor: Int,
  @ColorInt val tintColor: Int,
  @ColorInt val disabledColor: Int,
  @ColorInt val rowBackground: Int?,
  @ColorInt val separatorColor: Int,
  @ColorInt val headerTextColor: Int,
  @ColorInt val footerTextColor: Int,
  /** `secondaryContainer` — what M3 puts behind a selected list item. */
  @ColorInt val selectedContainer: Int,
  @ColorInt val onSelectedContainer: Int,
  /** `errorContainer` — what a destructive swipe action's button is filled with. */
  @ColorInt val errorContainer: Int,
  @ColorInt val onErrorContainer: Int,
  val labelTypeface: Typeface?,
  /** The list's default font. A row's own `font` falls back to this field by field. */
  val font: FontSpec?,
  val headerFont: FontSpec?,
  val footerFont: FontSpec?,
) {
  companion object {
    fun of(resolver: AppearanceResolver): RowStyle {
      val dark = resolver.isDark
      val onSurface = if (dark) 0xFFE6E0E9.toInt() else 0xFF1D1B20.toInt()
      val secondary =
        resolver.color(
          { it.secondaryLabelColor },
          AppearanceResolver.COLOR_ON_SURFACE_VARIANT,
          if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt(),
        )

      return RowStyle(
        labelColor = resolver.color({ it.labelColor }, AppearanceResolver.COLOR_ON_SURFACE, onSurface),
        secondaryColor = secondary,
        tintColor =
          resolver.color(
            { it.tintColor },
            AppearanceResolver.COLOR_PRIMARY,
            if (dark) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt(),
          ),
        // Not a themed field of its own: "disabled" is the platform's treatment of whatever the
        // label colour happens to be, and M3 specifies it as 38% of `onSurface`.
        disabledColor =
          (resolver.token(AppearanceResolver.COLOR_ON_SURFACE, onSurface) and 0x00FFFFFF) or
            (0x61 shl 24),
        rowBackground = resolver.optionalColor { it.rowBackground },
        separatorColor =
          resolver.color(
            { it.separator },
            AppearanceResolver.COLOR_OUTLINE_VARIANT,
            if (dark) 0xFF49454F.toInt() else 0xFFCAC4D0.toInt(),
          ),
        // M3 draws a list subheader in the primary role, which is what makes a section header read
        // as structure rather than as faint body text.
        headerTextColor =
          resolver.color({ it.headerTextColor }, AppearanceResolver.COLOR_PRIMARY, secondary),
        footerTextColor =
          resolver.color({ it.footerTextColor }, AppearanceResolver.COLOR_ON_SURFACE_VARIANT, secondary),
        selectedContainer =
          resolver.token(
            AppearanceResolver.COLOR_SECONDARY_CONTAINER,
            if (dark) 0xFF4A4458.toInt() else 0xFFE8DEF8.toInt(),
          ),
        onSelectedContainer =
          resolver.token(
            AppearanceResolver.COLOR_ON_SECONDARY_CONTAINER,
            if (dark) 0xFFE8DEF8.toInt() else 0xFF1D192B.toInt(),
          ),
        errorContainer =
          resolver.token(
            AppearanceResolver.COLOR_ERROR_CONTAINER,
            if (dark) 0xFF8C1D18.toInt() else 0xFFF9DEDC.toInt(),
          ),
        onErrorContainer =
          resolver.token(
            AppearanceResolver.COLOR_ON_ERROR_CONTAINER,
            if (dark) 0xFFF9DEDC.toInt() else 0xFF410E0B.toInt(),
          ),
        labelTypeface = null,
        font = resolver.font { it.font },
        headerFont = resolver.font { it.headerFont } ?: resolver.font { it.font },
        footerFont = resolver.font { it.footerFont } ?: resolver.font { it.font },
      )
    }
  }
}

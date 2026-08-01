package com.rngui.collectionview

import android.graphics.Typeface
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.FontSpec

/**
 * The resolved appearance a row binds against.
 *
 * Computed once per tree rather than per row — every field is a property of the list, and
 * resolving a theme attribute is a `TypedValue` lookup that has no business happening 2,000 times
 * during a fling.
 */
data class RowStyle(
  val labelColor: Int,
  val secondaryColor: Int,
  val tintColor: Int,
  val disabledColor: Int,
  val rowBackground: Int?,
  val separatorColor: Int,
  val headerTextColor: Int,
  val footerTextColor: Int,
  val labelTypeface: Typeface?,
  /** The list's default font. A row's own `font` falls back to this field by field. */
  val font: FontSpec?,
  val headerFont: FontSpec?,
  val footerFont: FontSpec?,
) {
  companion object {
    fun of(resolver: AppearanceResolver): RowStyle {
      val dark = resolver.isDark
      val secondary =
        resolver.color(
          { it.secondaryLabelColor },
          if (dark) SECONDARY_LABEL_DARK else SECONDARY_LABEL_LIGHT,
        )
      return RowStyle(
        labelColor = resolver.color({ it.labelColor }, if (dark) LABEL_DARK else LABEL_LIGHT),
        secondaryColor = secondary,
        tintColor = resolver.color({ it.tintColor }, if (dark) TINT_DARK else TINT_LIGHT),
        // Not a themed field of its own: "disabled" is the platform's own treatment of whatever
        // the label colour happens to be, so deriving it keeps a themed list coherent.
        disabledColor = secondary,
        rowBackground = resolver.optionalColor { it.rowBackground },
        separatorColor =
          resolver.color({ it.separator }, if (dark) SEPARATOR_DARK else SEPARATOR_LIGHT),
        headerTextColor = resolver.color({ it.headerTextColor }, secondary),
        footerTextColor = resolver.color({ it.footerTextColor }, secondary),
        labelTypeface = null,
        font = resolver.font { it.font },
        headerFont = resolver.font { it.headerFont } ?: resolver.font { it.font },
        footerFont = resolver.font { it.footerFont } ?: resolver.font { it.font },
      )
    }

    // The values `Appearance` falls back to when a field is unset — the Android spelling of the
    // iOS semantic colours the tree is written against (`label`, `secondaryLabel`, `separator`,
    // `tintColor`). Constants rather than theme attributes: see `AppearanceResolver.isDark` for
    // why reading the theme does not work here, and M4 for where the M3 Expressive surface
    // colours arrive.
    private const val LABEL_LIGHT = 0xFF000000.toInt()
    private const val LABEL_DARK = 0xFFFFFFFF.toInt()
    private const val SECONDARY_LABEL_LIGHT = 0x993C3C43.toInt()
    private const val SECONDARY_LABEL_DARK = 0x99EBEBF5.toInt()
    private const val TINT_LIGHT = 0xFF007AFF.toInt()
    private const val TINT_DARK = 0xFF0A84FF.toInt()
    private const val SEPARATOR_LIGHT = 0x1F000000
    private const val SEPARATOR_DARK = 0x1FFFFFFF
  }
}

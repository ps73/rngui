package com.rngui.collectionview

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec

/** Points, which the tree speaks, to Android pixels. dp and iOS points are the same unit. */
fun Context.dp(value: Number): Int =
  TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      resources.displayMetrics,
    )
    .toInt()

/**
 * The three stock row kinds, drawn with Views.
 *
 * **Views rather than a `ComposeView`, and M1 is the reason.** The spike could not settle the
 * frame-budget question on available hardware, and the plan's rule is that a verdict needs a
 * reproduction — so this takes the plan's own documented fallback rather than paying Compose's
 * build-system coupling for three kinds a `LinearLayout` renders in 0.055 ms. The control-bearing
 * kinds at M7 are a separate decision and are expected to go the other way.
 *
 * That decision is meant to be revisitable, which is why every kind goes through one holder rather
 * than one class per kind: swapping the renderer for `default` / `value` / `subtitle` is a change
 * to [bind] and to nothing else.
 *
 * ```
 *  ┌──────────────────────────────────────────────┐
 *  │ label                                  value │   value
 *  │ secondaryLabel                               │   subtitle
 *  └──────────────────────────────────────────────┘
 * ```
 *
 * The two text columns are one horizontal `LinearLayout` with a vertical one inside it, because a
 * `value` row and a `subtitle` row differ only in *which* of the two secondary slots is used —
 * building them as separate layouts would mean two view types, two pools, and a row that cannot
 * change kind without being recreated.
 */
class RowView(context: Context) : LinearLayout(context) {
  private val labelView =
    TextView(context).apply {
      textSize = 17f
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, WRAP)
    }

  private val secondaryView =
    TextView(context).apply {
      textSize = 15f
      visibility = View.GONE
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, WRAP).apply { topMargin = context.dp(2) }
    }

  private val textColumn =
    LinearLayout(context).apply {
      orientation = VERTICAL
      layoutParams = LayoutParams(0, WRAP, 1f)
      addView(labelView)
      addView(secondaryView)
    }

  private val valueView =
    TextView(context).apply {
      textSize = 17f
      visibility = View.GONE
      layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) }
      // A long value must not push the label off the row; the label wins the space and the value
      // takes what is left. iOS's list configuration does the same, and a value that is allowed to
      // grow without bound is the classic way a Settings row loses its title.
      maxLines = 1
    }

  init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = context.dp(44)
    setPadding(context.dp(16), context.dp(11), context.dp(16), context.dp(11))
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP)
    addView(textColumn)
    addView(valueView)
  }

  /**
   * Fully specifies every field this view can show.
   *
   * **The reuse rule, and it is not optional.** Anything assigned to a recycled view has to be
   * assigned on every pass — a row that only sets `value` when the spec has one inherits the
   * previous occupant's when it does not. On iOS this bit through `UIListContentConfiguration`;
   * here it would be a Settings row showing the wrong detail text, which reads as a data bug
   * rather than a recycling one. So every branch below has an `else`.
   */
  fun bind(row: RowSpec, style: RowStyle) {
    labelView.text = row.label.orEmpty()
    labelView.setTextColor(if (row.disabled == true) style.disabledColor else style.labelColor)
    labelView.typeface = style.labelTypeface

    val secondary = if (row.kind == RowKind.subtitle) row.secondaryLabel else null
    secondaryView.visibility = if (secondary != null) View.VISIBLE else View.GONE
    secondaryView.text = secondary.orEmpty()
    secondaryView.setTextColor(
      when {
        row.disabled == true -> style.disabledColor
        // The "Today" / "15:00" treatment: the tint is what marks the value as the row's current
        // setting rather than as an explanatory second line.
        row.secondaryLabelTinted == true -> parseRnguiHex(row.tintColor) ?: style.tintColor
        else -> style.secondaryColor
      }
    )

    val value = if (row.kind == RowKind.value) row.value else null
    valueView.visibility = if (value != null) View.VISIBLE else View.GONE
    valueView.text = value.orEmpty()
    valueView.setTextColor(
      if (row.disabled == true) style.disabledColor else style.secondaryColor
    )

    isEnabled = row.disabled != true
    isClickable = row.selectable == true && row.disabled != true
    alpha = if (row.disabled == true) DISABLED_ALPHA else 1f
  }

  private companion object {
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /**
     * Applied on top of the greyed text colours rather than instead of them.
     *
     * A disabled row has to read as disabled from across the screen, and colour alone does not do
     * that on a themed list where the caller may have set `labelColor` to something already faint.
     */
    const val DISABLED_ALPHA = 0.4f
  }
}

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
        // M6 resolves `FontSpec` properly, through ReactFontManager. Until then a row uses the
        // platform face, which is what an unset `font` means anyway.
        labelTypeface = null,
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

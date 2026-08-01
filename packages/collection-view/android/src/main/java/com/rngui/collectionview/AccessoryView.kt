package com.rngui.collectionview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.rngui.collectionview.generated.AccessoryKind
import com.rngui.collectionview.generated.MaterialSymbols

/**
 * The trailing accessory: a chevron, a tick, a checkbox, a radio dot or a spinner.
 *
 * Accessories rather than row kinds, which is the tree's decision and the right one: they are
 * decorations on an otherwise ordinary row, and treating them as kinds would mean a `subtitle` row
 * could not have one.
 *
 * Four of the five are glyphs from the bundled Material Symbols face, drawn the same way
 * [IconView] draws a leading icon — one asset, one code path, and they scale with the row's text.
 * The spinner is the exception, because an indeterminate progress animation is a view with a
 * lifecycle rather than a character.
 */
class AccessoryView(context: Context) : FrameLayout(context) {

  private val glyph = GlyphView(context)
  private val spinner =
    ProgressBar(context).apply {
      isIndeterminate = true
      visibility = GONE
    }

  init {
    addView(
      glyph,
      LayoutParams(context.dp(GLYPH_BOX_DP), context.dp(GLYPH_BOX_DP)),
    )
    addView(
      spinner,
      LayoutParams(context.dp(SPINNER_DP), context.dp(SPINNER_DP)),
    )
  }

  /** @return false when the row has no accessory, so the caller can hide this. */
  fun bind(kind: AccessoryKind?, on: Boolean, style: RowStyle, disabled: Boolean): Boolean {
    // Every branch assigns both children's visibility. A recycled holder that only *showed* what
    // it needed would keep the previous row's spinner spinning behind a chevron.
    val resolved = kind ?: AccessoryKind.none
    if (resolved == AccessoryKind.none || resolved == AccessoryKind.unknown) {
      visibility = GONE
      glyph.visibility = GONE
      spinner.visibility = GONE
      return false
    }

    visibility = VISIBLE

    if (resolved == AccessoryKind.spinner) {
      glyph.visibility = GONE
      spinner.visibility = VISIBLE
      return true
    }

    spinner.visibility = GONE
    glyph.visibility = VISIBLE

    val codepoint =
      when (resolved) {
        AccessoryKind.disclosure -> MaterialSymbols.byMaterialName["chevron_right"]
        AccessoryKind.checkmark -> MaterialSymbols.byMaterialName["check"]
        // `checkbox` is a filled circle when on and a hollow one when off — the multi-select
        // affordance. `radio` looks the same and differs only in meaning, which is the caller's to
        // enforce; the tree says so explicitly.
        AccessoryKind.checkbox,
        AccessoryKind.radio ->
          MaterialSymbols.byMaterialName[if (on) "check_circle" else "circle"]
        else -> null
      }

    glyph.set(
      codepoint = codepoint,
      color =
        when {
          disabled -> style.disabledColor
          // A disclosure chevron is chrome, not an action: grey like the platform's, not tinted.
          resolved == AccessoryKind.disclosure -> style.secondaryColor
          else -> style.tintColor
        },
      sizePx = context.dp(if (resolved == AccessoryKind.disclosure) 20 else 24).toFloat(),
    )
    return true
  }

  /** A single glyph from the bundled face, drawn centred. */
  private class GlyphView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var codepoint: Int? = null

    fun set(codepoint: Int?, color: Int, sizePx: Float) {
      this.codepoint = codepoint
      paint.color = color
      paint.textSize = sizePx
      paint.typeface = IconView.typeface(context)
      invalidate()
    }

    override fun onDraw(canvas: Canvas) {
      val point = codepoint ?: return
      val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
      canvas.drawText(String(Character.toChars(point)), width / 2f, baseline, paint)
    }
  }

  private companion object {
    const val GLYPH_BOX_DP = 24
    const val SPINNER_DP = 20
  }
}

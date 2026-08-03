package com.rngui.collectionview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.rngui.collectionview.generated.AccessoryKind
import com.rngui.collectionview.generated.MaterialSymbols

/**
 * The trailing accessory: a chevron, a tick, a checkbox, a radio button or a spinner.
 *
 * Accessories rather than row kinds, which is the tree's decision and the right one: they are
 * decorations on an otherwise ordinary row, and treating them as kinds would mean a `subtitle` row
 * could not have one.
 *
 * **`checkbox` and `radio` are real Material widgets, not drawn glyphs**, and that is the fix for
 * the most visible thing this list was getting wrong. The
 * [M3 list guidance](https://m3.material.io/components/lists/guidelines) is explicit that a list
 * item's selection control *is* a checkbox, a switch or a radio button — those components carry
 * their own state layer, ripple, animated check and touch target, and a glyph that merely looks
 * like one has none of it. A filled circle standing in for a checkbox is exactly the kind of
 * approximation that reads as "not really Android".
 *
 * The chevron stays a glyph from the bundled symbol font: it is chrome rather than a control, it
 * has no states, and it scales with the row's text.
 */
class AccessoryView(context: Context) : FrameLayout(context) {

  private val glyph = GlyphView(context)

  private val spinner =
    ProgressBar(context).apply {
      isIndeterminate = true
      visibility = GONE
    }

  /**
   * Both are `isClickable = false` and `isFocusable = false` on purpose.
   *
   * The *row* owns the tap — that is how the tree models it, and how iOS behaves — so the control
   * is a display of state rather than an input. Leaving them clickable would give a row two
   * independent hit targets that disagree about what a tap means, and would let the widget toggle
   * itself without ever telling JavaScript.
   */
  private val checkBox =
    MaterialCheckBox(context).apply {
      isClickable = false
      isFocusable = false
      visibility = GONE
    }

  private val radio =
    MaterialRadioButton(context).apply {
      isClickable = false
      isFocusable = false
      visibility = GONE
    }

  init {
    val box = context.dp(GLYPH_BOX_DP)
    addView(glyph, LayoutParams(box, box, Gravity.CENTER))
    addView(
      spinner,
      LayoutParams(context.dp(SPINNER_DP), context.dp(SPINNER_DP), Gravity.CENTER),
    )
    addView(checkBox, LayoutParams(WRAP, WRAP, Gravity.CENTER))
    addView(radio, LayoutParams(WRAP, WRAP, Gravity.CENTER))
  }

  /** @return false when the row has no accessory, so the caller can hide this. */
  fun bind(kind: AccessoryKind?, on: Boolean, style: RowStyle, disabled: Boolean): Boolean {
    // Every branch assigns *all four* children's visibility. A recycled holder that only showed
    // what it needed would keep the previous row's spinner turning behind a chevron.
    val resolved = kind ?: AccessoryKind.none
    glyph.visibility = GONE
    spinner.visibility = GONE
    checkBox.visibility = GONE
    radio.visibility = GONE

    if (resolved == AccessoryKind.none || resolved == AccessoryKind.unknown) {
      visibility = GONE
      return false
    }
    visibility = VISIBLE

    when (resolved) {
      AccessoryKind.spinner -> spinner.visibility = VISIBLE

      AccessoryKind.checkbox -> {
        checkBox.visibility = VISIBLE
        checkBox.isChecked = on
        checkBox.isEnabled = !disabled
      }

      // Visually a radio button, semantically exclusive. The difference is the caller's to
      // enforce — the tree says so — but the *control* has to be the right one, because a radio
      // button and a checkbox mean different things to anyone looking at the screen and to
      // TalkBack, which announces them differently.
      AccessoryKind.radio -> {
        radio.visibility = VISIBLE
        radio.isChecked = on
        radio.isEnabled = !disabled
      }

      else -> {
        glyph.visibility = VISIBLE
        glyph.set(
          codepoint =
            when (resolved) {
              AccessoryKind.disclosure -> MaterialSymbols.byMaterialName["chevron_right"]
              AccessoryKind.checkmark -> MaterialSymbols.byMaterialName["check"]
              else -> null
            },
          color =
            when {
              disabled -> style.disabledColor
              // A disclosure chevron is chrome, not an action: `onSurfaceVariant`, not the tint.
              resolved == AccessoryKind.disclosure -> style.secondaryColor
              else -> style.tintColor
            },
          sizePx = context.dp(if (resolved == AccessoryKind.disclosure) 20 else 24).toFloat(),
        )
      }
    }

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
    const val WRAP = LayoutParams.WRAP_CONTENT
  }
}

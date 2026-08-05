package com.rngui.collectionview

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.widget.TextView
import com.facebook.react.views.text.ReactFontManager
import com.rngui.collectionview.generated.FontDesign
import com.rngui.collectionview.generated.FontSpec

/**
 * Turns a `FontSpec` into a typeface, a size and a set of variation settings.
 *
 * `family` goes through `ReactFontManager`, which is the whole point: a face registered by
 * `expo-font` resolves here exactly the way React Native's own `fontFamily` prop resolves it, so a
 * list and a `<Text>` beside it end up with the same file. Reimplementing the lookup would work
 * until the first app that loads a font at runtime.
 */
object FontResolver {

  /**
   * Applies a spec to a text view, falling back field by field to what the view already has.
   *
   * "Field by field" is the contract: `font: { size: 20 }` must change the size and nothing else,
   * which is why this takes the view's current values as the fallback rather than a default spec.
   */
  fun apply(view: TextView, spec: FontSpec?, defaultSizeSp: Float, context: Context) {
    val sizeSp = spec?.size?.toFloat() ?: defaultSizeSp

    // `scaled` defaults to true, matching Dynamic Type on iOS. SP already folds in `fontScale`, so
    // the two branches are "respect the user's text size" and "do not" rather than two formulas.
    val unit =
      if (spec?.scaled == false) TypedValue.COMPLEX_UNIT_DIP else TypedValue.COMPLEX_UNIT_SP
    view.setTextSize(unit, sizeSp)

    view.typeface = typeface(spec, context)
    applyVariations(view, spec)
  }

  fun typeface(spec: FontSpec?, context: Context): Typeface? {
    val weight = spec?.weight?.let(::weightOf)
    val style = if ((weight ?: 400) >= 600) Typeface.BOLD else Typeface.NORMAL

    val family = spec?.family
    if (family != null) {
      return ReactFontManager.getInstance().getTypeface(family, style, context.assets)
    }

    return when (spec?.design) {
      FontDesign.monospaced -> Typeface.create(Typeface.MONOSPACE, style)
      FontDesign.serif -> Typeface.create(Typeface.SERIF, style)
      // `rounded` is SF Rounded, and Android has no counterpart in the system faces. Falling back
      // to the default face is the honest degradation: Roboto is not rounded, and substituting an
      // unrelated family to be *different* would be worse than being plain.
      else -> Typeface.create(Typeface.DEFAULT, style)
    }
  }

  /**
   * `'wght=620,wdth=110'` → `Paint.setFontVariationSettings("'wght' 620, 'wdth' 110")`.
   *
   * **Available from API 26, and it fails silently below — which is the whole reason M6 carries an
   * instrument.** On iOS a descriptor carrying a *name* attribute is matched by name and the
   * variation attribute is ignored entirely, with no error; Android's failure mode is the same
   * shape, dropping an unsupported axis without complaint. Both produce a list where every weight
   * looks identical and nothing anywhere says why.
   *
   * `RowBackendSpike`'s sibling — `FontVariationTest` — renders `wght=350` and `wght=900` and
   * asserts the ink coverage differs. A visual check is not sufficient; it already failed once on
   * iOS.
   */
  fun applyVariations(view: TextView, spec: FontSpec?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val settings = spec?.variations?.let(::toAndroidVariationSettings)
    // Cleared rather than skipped when absent. A recycled `TextView` keeps the last row's axes, so
    // a row with no `variations` would inherit whatever weight its predecessor asked for — the
    // reuse rule, in a place that looks like configuration rather than content.
    view.paint.fontVariationSettings = settings
  }

  /** `'wght=620,wdth=110'` → `"'wght' 620, 'wdth' 110"`, or null if nothing parses. */
  fun toAndroidVariationSettings(compact: String): String? {
    val parts =
      compact
        .split(',')
        .mapNotNull { entry ->
          val (tag, value) = entry.split('=', limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
          if (tag == null || value == null) return@mapNotNull null
          val trimmedTag = tag.trim()
          val trimmedValue = value.trim().toFloatOrNull() ?: return@mapNotNull null
          if (trimmedTag.length != 4) return@mapNotNull null
          "'$trimmedTag' $trimmedValue"
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
  }

  /** `'regular' | 'medium' | … | '100'…'900'` → a numeric weight. */
  fun weightOf(weight: String): Int =
    weight.toIntOrNull()
      ?: when (weight) {
        "ultraLight", "thin" -> 100
        "light" -> 300
        "regular", "normal" -> 400
        "medium" -> 500
        "semibold" -> 600
        "bold" -> 700
        "heavy" -> 800
        "black" -> 900
        else -> 400
      }
}

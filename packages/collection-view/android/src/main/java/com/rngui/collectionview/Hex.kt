package com.rngui.collectionview

import androidx.annotation.ColorInt

/**
 * Parses the `#RRGGBBAA` form JavaScript normalises every appearance colour into.
 *
 * The counterpart of `UIColor(rnguiHex:)`, and deliberately as forgiving in the same places: the
 * shorter CSS forms are accepted purely so a hand-written value in a test or a debugging session
 * behaves the way anyone would expect. Production input is always eight digits, because
 * `resolveColor` in `appearance.ts` runs React Native's own colour parser first — which is what
 * lets a caller write `'red'`, `'rgba(0,0,0,.5)'` or whatever their theming library resolves to.
 *
 * Not `android.graphics.Color.parseColor`: that reads `#AARRGGBB`, silently mis-ordering every
 * eight-digit value this library produces, and it *throws* on anything it cannot read rather than
 * returning null. A theme colour that fails to parse must degrade to the platform default, not
 * take the app down.
 */
@ColorInt
fun parseRnguiHex(hex: String?): Int? {
  var digits = hex?.trim() ?: return null
  if (digits.startsWith("#")) digits = digits.substring(1)

  // Expand the shorthand forms by doubling each digit: #1a2 -> #11aa22.
  if (digits.length == 3 || digits.length == 4) {
    digits = buildString { for (c in digits) { append(c); append(c) } }
  }
  if (digits.length != 6 && digits.length != 8) return null

  val value = digits.toLongOrNull(16) ?: return null
  val hasAlpha = digits.length == 8

  val red = ((value shr if (hasAlpha) 24 else 16) and 0xFF).toInt()
  val green = ((value shr if (hasAlpha) 16 else 8) and 0xFF).toInt()
  val blue = ((value shr if (hasAlpha) 8 else 0) and 0xFF).toInt()
  val alpha = if (hasAlpha) (value and 0xFF).toInt() else 0xFF

  // Android packs colours as ARGB, which is the reason this cannot just hand back the parsed
  // integer for the eight-digit case.
  return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

/**
 * Composites `overlay` at `fraction` over `base`.
 *
 * The pressed state of a *themed* row. A `RippleDrawable` supplies this for the platform's own
 * row background, but a caller who sets `appearance.rowBackground` replaces that drawable, so the
 * highlight has to be reconstructed — the same reconstruction `rnguiOverlaid(with:alpha:)` does on
 * iOS, and for the same reason.
 */
@ColorInt
fun overlayColor(@ColorInt base: Int, @ColorInt overlay: Int, fraction: Float): Int {
  fun mix(shift: Int): Int {
    val b = (base shr shift) and 0xFF
    val o = (overlay shr shift) and 0xFF
    return (b + (o - b) * fraction).toInt().coerceIn(0, 255)
  }
  return (((base shr 24) and 0xFF) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

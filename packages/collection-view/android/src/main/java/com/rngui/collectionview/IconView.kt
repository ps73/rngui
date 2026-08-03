package com.rngui.collectionview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.view.View
import com.rngui.collectionview.generated.MaterialSymbols
import com.rngui.collectionview.generated.RowSpec

/**
 * A leading icon: a Material Symbol glyph, optionally on a coloured rounded tile.
 *
 * **Drawn as text from a bundled variable font, not as a drawable.** One face covers every icon the
 * map names, it takes a colour and a size the way text does, and its `wght` axis lets a glyph match
 * the weight of the label beside it — which a `VectorDrawable` cannot do at all. It is also one
 * asset rather than three hundred.
 *
 * `imageBackground` replaces `imageColor` rather than combining with it. That is the tree's rule
 * and it is right: the point of a tile is that the *colour is the background*, and a Settings row
 * has never had a tinted glyph on a tinted square.
 */
class IconView(context: Context) : View(context) {

  private val glyphPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

  private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)

  private var codepoint: Int? = null

  /** The letters drawn instead of a glyph, already truncated. Null unless this is an avatar. */
  private var monogram: String? = null

  private var tileColor: Int? = null
  private var glyphSizePx = 0f

  /** Nothing to draw. The row hides this rather than leaving a gap the width of an icon. */
  val isEmpty: Boolean
    get() = codepoint == null && monogram == null

  /**
   * Fully specifies the icon, or reports that there is none.
   *
   * @return false when the row has no icon, or names one this build cannot draw.
   */
  fun bind(row: RowSpec, style: RowStyle): Boolean {
    tileColor = parseRnguiHex(row.imageBackground)
    monogram = resolveMonogram(row)
    codepoint = if (monogram != null) null else resolve(row)

    if (codepoint == null && monogram == null) {
      visibility = GONE
      return false
    }

    val tinted = parseRnguiHex(row.imageColor)

    // The face is the icon font for a glyph and the system's own for letters — Material Symbols
    // carries no Latin alphabet, so a monogram set in it would render two blanks.
    glyphPaint.typeface = if (monogram != null) MONOGRAM_TYPEFACE else typeface(context)
    glyphPaint.color =
      when {
        // In a container the content is always white, because the container carries the colour.
        tileColor != null -> Color.WHITE
        tinted != null -> tinted
        else -> style.tintColor
      }

    val requested = row.imageSize?.toFloat() ?: DEFAULT_GLYPH_DP
    glyphSizePx =
      when {
        monogram != null -> context.dp(MONOGRAM_DP).toFloat()
        tileColor != null -> context.dp(TILE_GLYPH_DP).toFloat()
        else -> context.dp(requested).toFloat()
      }
    glyphPaint.textSize = glyphSizePx
    tileColor?.let { tilePaint.color = it }

    visibility = VISIBLE
    return true
  }

  /**
   * The letters to draw, or null if this row is not an avatar.
   *
   * Refused without a container, and loudly. Two letters floating where an icon belongs looks like
   * a label that lost its row rather than like an avatar, so the failure has to be reportable —
   * silently drawing them would send someone hunting through their layout.
   */
  private fun resolveMonogram(row: RowSpec): String? {
    val raw = row.imageMonogram?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (tileColor == null) {
      warnOnce(
        "monogram-without-background",
        "[@rngui/collection-view] a monogram needs `background` to sit in — letters with nothing " +
          "behind them are not an avatar. That row's icon renders nothing.",
      )
      return null
    }
    // By code point rather than by `char`, so an initial outside the BMP counts as one letter
    // instead of being cut in half into two replacement glyphs.
    val available = raw.codePointCount(0, raw.length)
    return raw.substring(0, raw.offsetByCodePoints(0, minOf(MONOGRAM_MAX, available)))
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val size =
      if (tileColor != null) context.dp(TILE_SIZE_DP)
      else maxOf(glyphSizePx.toInt(), context.dp(DEFAULT_GLYPH_DP))
    setMeasuredDimension(size, size)
  }

  override fun onDraw(canvas: Canvas) {
    tileColor?.let {
      canvas.drawCircle(width / 2f, height / 2f, width / 2f, tilePaint)
    }

    val content = monogram ?: codepoint?.let { String(Character.toChars(it)) } ?: return
    val baseline = height / 2f - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
    canvas.drawText(content, width / 2f, baseline, glyphPaint)
  }

  /**
   * A glyph named directly, outside a row's leading slot.
   *
   * For the icons a *control* lays out rather than the row — the two suns flanking a brightness
   * slider, which `UISlider` has properties for and Material's has not. Never a container, never a
   * monogram: those belong to the leading slot, and this one is a decoration on a control.
   */
  fun bindStandalone(systemImage: String?, style: RowStyle, disabled: Boolean) {
    monogram = null
    tileColor = null
    codepoint = systemImage?.let { resolve(materialSymbol = null, systemImage = it) }
    if (codepoint == null) {
      visibility = GONE
      return
    }
    glyphPaint.typeface = typeface(context)
    // `onSurfaceVariant`, which is what M3 puts either side of a slider — the accent belongs to the
    // track, and a second tinted thing beside it competes with the control for attention.
    glyphPaint.color = if (disabled) style.disabledColor else style.secondaryColor
    glyphSizePx = context.dp(DEFAULT_GLYPH_DP).toFloat()
    glyphPaint.textSize = glyphSizePx
    visibility = VISIBLE
  }

  /**
   * Which glyph, if any.
   *
   * `materialSymbol` wins over `systemImage` when both are set: it is the escape hatch, and an
   * escape hatch that loses to the thing it exists to override is not one.
   */
  private fun resolve(row: RowSpec): Int? =
    resolve(row.materialSymbol, row.systemImage)

  private fun resolve(materialSymbol: String?, systemImage: String?): Int? {
    materialSymbol?.let { name ->
      MaterialSymbols.byMaterialName[name]?.let { return it }
      warnOnce(
        "materialSymbol:$name",
        "[@rngui/collection-view] '$name' is not in the bundled Material Symbols subset, so " +
          "the icon on that row renders nothing. The face ships subset — see " +
          "scripts/symbol-map.mjs to add it.",
      )
      return null
    }

    val sfName = systemImage ?: return null
    MaterialSymbols.bySfName[sfName]?.let { return it }
    warnOnce(
      "systemImage:$sfName",
      "[@rngui/collection-view] no Material Symbol is mapped to the SF Symbol '$sfName', so " +
        "the icon on that row renders nothing on Android. Set `materialSymbol` on the row to " +
        "choose one, or add the mapping in scripts/symbol-map.mjs.",
    )
    return null
  }

  companion object {
    private const val DEFAULT_GLYPH_DP = 22f

    /**
     * M3's leading avatar: a 40dp **circle** with a 24dp glyph in it.
     *
     * **Not iOS's 29pt rounded square, and this is one of the differences worth being deliberate
     * about.** A squircle tile with a white glyph is the Settings look, and it is the Settings look
     * *because Apple invented it there* — Android has never had one. M3's leading element in a list
     * item is either a bare icon or a circular avatar, so `imageBackground` resolves to the circle
     * here and to the tile on iOS. Same prop, same meaning ("give this icon the platform's own
     * container"), two shapes, which is the whole argument this package makes.
     *
     * A consequence worth stating: a Settings-style screen that sets `imageBackground` on twenty
     * rows gets twenty coloured circles on Android. That is not a bug, it is what the prop means —
     * and a screen that wants Pixel Settings should set no background at all and let the bare
     * monochrome glyph through, which is what Pixel Settings actually draws.
     */
    private const val TILE_SIZE_DP = 40
    private const val TILE_GLYPH_DP = 24

    /** Letters read larger than a glyph at the same nominal size, so they are set smaller. */
    private const val MONOGRAM_DP = 16
    private const val MONOGRAM_MAX = 2

    /** M3 sets an avatar's initials in the medium weight, against the label's regular. */
    private val MONOGRAM_TYPEFACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private var cached: Typeface? = null
    private val warned = HashSet<String>()

    /**
     * The bundled face, loaded once per process.
     *
     * From `assets/` rather than `res/font/`: a resource font would be merged into the consuming
     * app's resources and could collide by name, and `res/font` has never accepted a variable
     * font's axes on older API levels.
     */
    fun typeface(context: Context): Typeface? {
      cached?.let { return it }
      return runCatching { Typeface.createFromAsset(context.assets, MaterialSymbols.FONT_ASSET) }
        .onFailure {
          Log.w("rngui", "the Material Symbols asset is missing; icons will not render", it)
        }
        .getOrNull()
        ?.also { cached = it }
    }

    /**
     * Once per name per process.
     *
     * A silently missing icon reads as a layout bug, so it has to say something — but a list of
     * 2,000 rows all naming the same unmapped symbol would say it 2,000 times, and a warning that
     * floods is a warning people turn off.
     */
    private fun warnOnce(key: String, message: String) {
      if (!warned.add(key)) return
      Log.w("rngui", message)
    }
  }
}

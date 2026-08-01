package com.rngui.collectionview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
  private var tileColor: Int? = null
  private var glyphSizePx = 0f

  /** Nothing to draw. The row hides this rather than leaving a gap the width of an icon. */
  val isEmpty: Boolean
    get() = codepoint == null

  /**
   * Fully specifies the icon, or reports that there is none.
   *
   * @return false when the row has no icon, or names one this build cannot draw.
   */
  fun bind(row: RowSpec, style: RowStyle): Boolean {
    codepoint = resolve(row)
    if (codepoint == null) {
      visibility = GONE
      return false
    }

    tileColor = parseRnguiHex(row.imageBackground)
    val tinted = parseRnguiHex(row.imageColor)

    glyphPaint.typeface = typeface(context)
    glyphPaint.color =
      when {
        // On a tile the glyph is always white, because the tile carries the colour.
        tileColor != null -> Color.WHITE
        tinted != null -> tinted
        else -> style.tintColor
      }

    val requested = row.imageSize?.toFloat() ?: DEFAULT_GLYPH_DP
    glyphSizePx =
      if (tileColor != null) context.dp(TILE_GLYPH_DP).toFloat()
      else context.dp(requested).toFloat()
    glyphPaint.textSize = glyphSizePx
    tileColor?.let { tilePaint.color = it }

    visibility = VISIBLE
    return true
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val size =
      if (tileColor != null) context.dp(TILE_SIZE_DP)
      else maxOf(glyphSizePx.toInt(), context.dp(DEFAULT_GLYPH_DP))
    setMeasuredDimension(size, size)
  }

  override fun onDraw(canvas: Canvas) {
    val point = codepoint ?: return

    tileColor?.let {
      val radius = context.dp(TILE_RADIUS_DP).toFloat()
      canvas.drawRoundRect(
        RectF(0f, 0f, width.toFloat(), height.toFloat()),
        radius,
        radius,
        tilePaint,
      )
    }

    val baseline = height / 2f - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
    canvas.drawText(String(Character.toChars(point)), width / 2f, baseline, glyphPaint)
  }

  /**
   * Which glyph, if any.
   *
   * `materialSymbol` wins over `systemImage` when both are set: it is the escape hatch, and an
   * escape hatch that loses to the thing it exists to override is not one.
   */
  private fun resolve(row: RowSpec): Int? {
    row.materialSymbol?.let { name ->
      MaterialSymbols.byMaterialName[name]?.let { return it }
      warnOnce(
        "materialSymbol:$name",
        "[@rngui/collection-view] '$name' is not in the bundled Material Symbols subset, so " +
          "the icon on that row renders nothing. The face ships subset — see " +
          "scripts/symbol-map.mjs to add it.",
      )
      return null
    }

    val sfName = row.systemImage ?: return null
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
    private const val TILE_SIZE_DP = 29
    private const val TILE_GLYPH_DP = 18
    private const val TILE_RADIUS_DP = 7

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

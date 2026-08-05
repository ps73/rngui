package com.rngui.collectionview.spike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.FontResolver
import com.rngui.collectionview.generated.FontSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that `FontSpec.variations` actually reaches the rasterizer.
 *
 * **A visual check is not sufficient, and this is the milestone where that is known rather than
 * suspected.** On iOS a font descriptor carrying a *name* attribute is matched by name and the
 * variation attribute is ignored **entirely** — silently, with no error and no warning, so every
 * weight renders identically and the screenshots look plausible. Android's failure mode is the same
 * shape: `Paint.setFontVariationSettings` drops an axis the face does not expose, and returns
 * without complaint.
 *
 * So the assertion is on pixels. Render the same glyphs at `wght=350` and at `wght=900`, count the
 * ink, and require that the two differ. That is a claim no silent fallback can satisfy.
 */
@RunWith(AndroidJUnit4::class)
class FontVariationTest {

  @Test
  fun weightAxisChangesInkCoverage() {
    // API 26 is the floor for `setFontVariationSettings`, and M1 decided to keep minSdk at 24
    // rather than raise it for this. Below 26 the nearest static weight is used, which is a
    // degradation rather than a bug — so this asserts nothing there instead of failing.
    assumeTrue(
      "font variations need API 26; below that M6 falls back to the nearest static weight",
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
    )

    val light = inkCoverage("wght=350")
    val heavy = inkCoverage("wght=900")

    assertTrue("nothing was drawn at wght=350", light > 0)
    assertTrue("nothing was drawn at wght=900", heavy > 0)
    assertTrue(
      "wght=350 and wght=900 produced the same ink coverage ($light vs $heavy) — the variation " +
        "axis is being dropped silently, which is exactly the iOS failure this test exists for",
      heavy > light * 1.05,
    )
  }

  /** The compact `tag=value` form has to survive translation to the platform's own spelling. */
  @Test
  fun variationsAreTranslatedToThePlatformSpelling() {
    assertEquals(
      "'wght' 620.0, 'wdth' 110.0",
      FontResolver.toAndroidVariationSettings("wght=620,wdth=110"),
    )
    // Garbage in one axis must not take the others with it — the same leniency the tree decoders
    // have, for the same reason.
    assertEquals("'wght' 620.0", FontResolver.toAndroidVariationSettings("wght=620,nonsense"))
    assertNotNull(FontResolver.toAndroidVariationSettings("wght=620"))
    assertEquals(null, FontResolver.toAndroidVariationSettings("not-an-axis"))
  }

  /** Fraction of pixels that are not the background, for text rendered at the given axes. */
  private fun inkCoverage(variations: String): Int {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var count = 0

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val view =
        TextView(context).apply {
          text = SAMPLE
          setTextColor(Color.BLACK)
          FontResolver.apply(
            this,
            FontSpec(variations = variations, scaled = false),
            defaultSizeSp = 48f,
            context = context,
          )
        }

      val widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY)
      val heightSpec = View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
      view.measure(widthSpec, heightSpec)
      view.layout(0, 0, WIDTH, HEIGHT)

      val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(Color.WHITE)
      view.draw(Canvas(bitmap))

      val pixels = IntArray(WIDTH * HEIGHT)
      bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
      // Anything meaningfully darker than the white background. A threshold rather than
      // `!= WHITE`, so antialiasing on the glyph edges does not dominate the count — the claim is
      // about stroke thickness, not about how many pixels the rasterizer touched.
      count = pixels.count { Color.red(it) < 128 }

      bitmap.recycle()
    }

    return count
  }

  private companion object {
    /** Letters with long strokes, where a weight change moves the most ink. */
    const val SAMPLE = "HHHHIIIIMMMM"
    const val WIDTH = 600
    const val HEIGHT = 120
  }
}

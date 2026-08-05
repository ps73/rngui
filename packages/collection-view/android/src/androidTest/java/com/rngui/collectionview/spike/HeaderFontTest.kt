package com.rngui.collectionview.spike

import android.graphics.Typeface
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.CollectionAdapter
import com.rngui.collectionview.FlattenedTree
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.ParkingView
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.generated.Appearance
import com.rngui.collectionview.generated.FontSpec
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `headerFont` reached `RowStyle` and stopped there.
 *
 * `RowStyle.of` has resolved `headerFont` and `footerFont` since M6, and `LabelHolder.bind` set
 * the text and the colour and nothing else — so every font field was applied to rows and silently
 * ignored by section headers and footers. It survived the M3 Expressive pass and the whole device
 * matrix because a header is small, grey and short, and the difference between Roboto and Roboto
 * Serif at 13sp is not something a screenshot review catches. It was found by hashing the pixels
 * of one header across two families and getting the same hash twice.
 *
 * Rows are covered by their own tests; this is the supplementary path, which is the one that had
 * no assertion at all.
 */
@RunWith(AndroidJUnit4::class)
class HeaderFontTest {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  private val noEvents =
    object : RowEvents {
      override fun onRowPress(rowId: String) = Unit

      override fun onSwitchChange(rowId: String, value: Boolean) = Unit

      override fun onTextChange(rowId: String, value: String) = Unit

      override fun onFocusChange(rowId: String, focused: Boolean) = Unit

      override fun onDateChange(rowId: String, millis: Double) = Unit

      override fun onMenuSelect(rowId: String, itemId: String) = Unit

      override fun onSwipeAction(rowId: String, actionId: String) = Unit

      override fun onSliderChange(rowId: String, value: Double) = Unit

      override fun onSliderCommit(rowId: String, value: Double) = Unit
    }

  private val tree =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            header = "Header",
            rows = (0 until 4).map { RowSpec(id = "r$it", label = "row $it") },
          )
        )
    )

  /** The typeface the header is actually drawn with, for an appearance carrying [font]. */
  private fun headerTypeface(font: FontSpec?): Typeface? {
    var typeface: Typeface? = null

    activityRule.scenario.onActivity { activity ->
      val themed = AppearanceResolver.themedContext(activity, isDark = false)
      val resolver =
        AppearanceResolver(
          context = themed,
          isDark = false,
          light = Appearance(headerFont = font),
          dark = null,
        )
      val rowStyle = RowStyle.of(resolver)
      val listStyle =
        ListStyle.of(themed, resolver, rowStyle, ListAppearance.insetGrouped, requested = null)

      val built =
        CollectionAdapter(themed, rowStyle, listStyle, noEvents, ParkingView(activity), { null })
      built.submitList(FlattenedTree.of(tree).items)

      val list =
        RecyclerView(activity).apply {
          layoutManager = LinearLayoutManager(activity)
          adapter = built
        }
      activity.setContentView(list)

      // The header is item 0 in a grouped list, and a `LabelHolder`'s item view *is* the TextView.
      list.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
      )
      list.layout(0, 0, 1080, 1920)
      typeface = (list.getChildAt(0) as? TextView)?.typeface
    }

    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    return typeface
  }

  @Test
  fun theHeaderTakesTheFontItWasGiven() {
    assertEquals(
      Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL),
      headerTypeface(FontSpec(family = "ui-monospace")),
    )
    assertEquals(
      Typeface.create(Typeface.SERIF, Typeface.NORMAL),
      headerTypeface(FontSpec(family = "ui-serif")),
    )
  }

  /**
   * The reuse rule, on the one holder that had no font code at all: a header arriving in a list
   * that names no font must not keep the last one's face.
   */
  @Test
  fun anUnsetFontRestoresTheDefaultFace() {
    assertEquals(
      Typeface.create(Typeface.DEFAULT, Typeface.NORMAL),
      headerTypeface(null),
    )
  }
}

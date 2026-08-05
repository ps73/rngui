package com.rngui.collectionview.spike

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.rngui.collectionview.StickyHeaderDecoration
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pinned header has to repaint when the mode flips, and it did not.
 *
 * **Every other appearance test in this package asserts on rows**, which is exactly why this one
 * was missing. `RNGUICollectionViewView.restyle()` rebound the rows and stopped there;
 * `StickyHeaderDecoration` caches its header view and short-circuits on `position == boundPosition`,
 * true on every frame after the first. So a system dark flip repainted the whole list and left the
 * header pinned above it still drawing the light palette — visible only on `plain`, and only while
 * a header is actually pinned, which is why it went unnoticed.
 *
 * The decoration draws into the `RecyclerView`'s canvas rather than adding a view, so there is
 * nothing in the hierarchy to query. The test asks the decoration what colour it is *currently*
 * drawing the title in — see [StickyHeaderDecoration.pinnedHeaderTextColor] — which is the thing
 * the eye would see.
 */
@RunWith(AndroidJUnit4::class)
class HeaderRestyleTest {
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

  private lateinit var list: RecyclerView
  private lateinit var adapter: CollectionAdapter
  private lateinit var decoration: StickyHeaderDecoration

  private fun styles(activity: android.app.Activity, isDark: Boolean): Triple<android.content.Context, RowStyle, ListStyle> {
    val themed = AppearanceResolver.themedContext(activity, isDark = isDark)
    val resolver = AppearanceResolver(context = themed, isDark = isDark, light = null, dark = null)
    val rowStyle = RowStyle.of(resolver)
    return Triple(
      themed,
      rowStyle,
      ListStyle.of(themed, resolver, rowStyle, ListAppearance.plain, requested = null),
    )
  }

  private val tree =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            header = "A",
            rows = (0 until 40).map { RowSpec(id = "r$it", label = "row $it") },
          )
        )
    )

  /** Draws the list, which is the only thing that builds or rebuilds the pinned header. */
  private fun draw() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      if (list.childCount == 0 && list.width > 0) {
        list.measure(
          android.view.View.MeasureSpec.makeMeasureSpec(list.width, android.view.View.MeasureSpec.EXACTLY),
          android.view.View.MeasureSpec.makeMeasureSpec(list.height, android.view.View.MeasureSpec.EXACTLY),
        )
        list.layout(list.left, list.top, list.right, list.bottom)
      }
      val bitmap =
        Bitmap.createBitmap(
          list.width.coerceAtLeast(1),
          list.height.coerceAtLeast(1),
          Bitmap.Config.ARGB_8888,
        )
      list.draw(Canvas(bitmap))
      bitmap.recycle()
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  private fun headerTextColor(): Int {
    var color: Int? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      color = decoration.pinnedHeaderTextColor()
    }
    return requireNotNull(color) { "no pinned header was built" }
  }

  @Test
  fun theHeaderRepaintsWhenTheModeFlips() {
    var darkHeaderColor = 0

    activityRule.scenario.onActivity { activity ->
      val (themed, rowStyle, listStyle) = styles(activity, isDark = false)
      // A local: inside `apply`, a bare `adapter` is `RecyclerView.adapter`, not this property.
      val built =
        CollectionAdapter(themed, rowStyle, listStyle, noEvents, ParkingView(activity), { null })
      adapter = built
      built.submitList(FlattenedTree.of(tree).items)

      decoration = StickyHeaderDecoration(FlattenedTree.of(tree), enabled = true)
      list =
        RecyclerView(activity).apply {
          layoutManager = LinearLayoutManager(activity)
          this.adapter = built
          itemAnimator = null
          addItemDecoration(decoration)
        }
      activity.setContentView(list)

      darkHeaderColor = styles(activity, isDark = true).second.headerTextColor
    }

    draw()
    val light = headerTextColor()

    // The fixture has to be able to tell the two apart, or nothing below means anything.
    assertNotEquals(
      "light and dark header colours coincide, so this test cannot fail",
      darkHeaderColor,
      light,
    )

    // Exactly what `RNGUICollectionViewView.restyle()` does on a mode flip.
    activityRule.scenario.onActivity { activity ->
      val (themed, rowStyle, listStyle) = styles(activity, isDark = true)
      adapter.retheme(themed, rowStyle, listStyle)
      decoration.restyle()
    }
    draw()

    assertEquals(darkHeaderColor, headerTextColor())
  }
}

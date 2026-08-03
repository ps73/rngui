package com.rngui.collectionview.spike

import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.CollectionAdapter
import com.rngui.collectionview.FlattenedTree
import com.rngui.collectionview.GroupDecoration
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.ParkingView
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A grouped list has to be bounded at both ends, and a plain one must not be.
 *
 * **Android gets neither of these for free, where iOS gets both.** `insetGrouped` insets its first
 * and last sections on iOS, and the scroll view's safe-area inset keeps content clear of the tab
 * bar; here an opaque toolbar reserves its own space and stops, and a native tab bar is a sibling
 * view rather than an overlay. Without the decoration supplying the air, the first card sits flush
 * against the toolbar and the last against the tab bar — which is what shipped, and which reads as
 * a missing margin rather than as a missing inset.
 *
 * The plain half is the one worth guarding hardest. `plain` is what Contacts uses, its headers pin
 * to the top of the viewport, and spacing above the first one would leave the pinned letter
 * floating in a gap.
 */
@RunWith(AndroidJUnit4::class)
class ListEdgeSpacingTest {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  private val silent =
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

  @Test
  fun aGroupedListHasAirAtBothEnds() {
    val top = decorationAt(ListAppearance.insetGrouped, Edge.TOP)
    val bottom = decorationAt(ListAppearance.insetGrouped, Edge.BOTTOM)

    assertTrue("the first card sits flush against the toolbar", top > 0)
    assertTrue("the last card sits flush against the tab bar", bottom > 0)
    // The same value as the gap between sections, so the list is bounded by the rhythm it already
    // has rather than by a second number that has to be kept in step with it.
    assertEquals("the two ends are spaced differently", top, bottom)
  }

  @Test
  fun aPlainListStaysFlush() {
    assertEquals(
      "a pinned header would float in a gap under the toolbar",
      0,
      decorationAt(ListAppearance.plain, Edge.TOP),
    )
    assertEquals(0, decorationAt(ListAppearance.plain, Edge.BOTTOM))
  }

  private enum class Edge {
    TOP,
    BOTTOM,
  }

  /** The decoration inset actually applied to the first or last laid-out child. */
  private fun decorationAt(appearance: ListAppearance, edge: Edge): Int {
    lateinit var list: RecyclerView
    var result = -1

    onMain { activity ->
      val themed = AppearanceResolver.themedContext(activity, isDark = false)
      val resolver =
        AppearanceResolver(context = themed, isDark = false, light = null, dark = null)
      val rowStyle = RowStyle.of(resolver)
      val listStyle =
        ListStyle.of(themed, resolver, rowStyle, appearance, requested = null)

      val adapter =
        CollectionAdapter(
          themed,
          rowStyle,
          listStyle,
          silent,
          ParkingView(activity),
          hostChildAt = { null },
        )
      adapter.submitList(FlattenedTree.of(tree()).items)

      list =
        RecyclerView(activity).apply {
          layoutManager = LinearLayoutManager(activity)
          addItemDecoration(GroupDecoration(listStyle))
          this.adapter = adapter
        }
      activity.setContentView(
        FrameLayout(activity).apply { addView(list, MATCH, MATCH) }
      )
    }

    // Short enough to fit on one screen, so the first and last children are both laid out and
    // neither measurement depends on a scroll having settled.
    onMain {
      val manager = requireNotNull(list.layoutManager)
      result =
        when (edge) {
          Edge.TOP -> manager.getTopDecorationHeight(requireNotNull(list.getChildAt(0)))
          Edge.BOTTOM ->
            manager.getBottomDecorationHeight(
              requireNotNull(list.getChildAt(list.childCount - 1))
            )
        }
    }
    return result
  }

  /** Two rows in one section — enough to have a first and a last, and short enough to fit. */
  private fun tree(): Tree =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            rows =
              listOf(
                RowSpec(id = "a", label = "First"),
                RowSpec(id = "b", label = "Last"),
              ),
          )
        )
    )

  private fun onMain(body: (android.app.Activity) -> Unit) {
    activityRule.scenario.onActivity { activity -> body(activity) }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  private companion object {
    const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
  }
}

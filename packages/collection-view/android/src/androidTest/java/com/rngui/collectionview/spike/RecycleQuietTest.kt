package com.rngui.collectionview.spike

import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.CollectionAdapter
import com.rngui.collectionview.FlattenedTree
import com.rngui.collectionview.Item
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.ColorScheme
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M7's done-when, and the one regression test this milestone genuinely needs.
 *
 * **A recycled `Switch` keeps its `OnCheckedChangeListener`, and assigning `isChecked` fires it.**
 * So a list of switches scrolled far enough to force recycling reports a stream of changes the user
 * never made — and `Root` writes every one of them back as state, which means the *data* changes.
 * It is not a cosmetic bug and it does not look like a recycling bug from JavaScript; it looks like
 * switches spontaneously flipping.
 *
 * The same shape applies to `EditText`'s `TextWatcher`, so this asserts on both. The assertion is
 * **zero**, not "few": there is no acceptable number of events for a scroll that changed nothing.
 */
@RunWith(AndroidJUnit4::class)
class RecycleQuietTest {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  private val switchEvents = CopyOnWriteArrayList<String>()
  private val textEvents = CopyOnWriteArrayList<String>()

  private val events =
    object : RowEvents {
      override fun onRowPress(rowId: String) = Unit

      override fun onSwitchChange(rowId: String, value: Boolean) {
        switchEvents += "$rowId=$value"
      }

      override fun onTextChange(rowId: String, value: String) {
        textEvents += "$rowId=$value"
      }

      override fun onFocusChange(rowId: String, focused: Boolean) = Unit

      override fun onDateChange(rowId: String, millis: Double) = Unit

      override fun onMenuSelect(rowId: String, itemId: String) = Unit

      override fun onSwipeAction(rowId: String, actionId: String) = Unit
    }

  @Test
  fun recyclingSwitchesAndFieldsEmitsNothing() {
    lateinit var list: RecyclerView
    var boundRows = 0

    activityRule.scenario.onActivity { activity ->
      val resolver = AppearanceResolver(isDark = false, light = null, dark = null)
      val rowStyle = RowStyle.of(resolver)
      val listStyle =
        ListStyle.of(activity, resolver, rowStyle, ListAppearance.insetGrouped)

      val adapter = CollectionAdapter(rowStyle, listStyle, events)
      adapter.submitList(FlattenedTree.of(tree()).items)

      list =
        RecyclerView(activity).apply {
          layoutManager = LinearLayoutManager(activity)
          this.adapter = adapter
          setHasFixedSize(true)
        }
      activity.setContentView(
        FrameLayout(activity).apply {
          addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        }
      )
    }

    // Let the first layout settle. Binding the initially visible rows is *not* recycling, and
    // events from it would be a different bug — but there should be none from that either.
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    assertTrue("the list never laid out", list.childCount > 0)

    // Scroll far enough that every holder is reused many times over. 200 passes of a screen's
    // worth guarantees the pool has cycled, which is the condition the bug needs.
    repeat(SCROLL_PASSES) {
      InstrumentationRegistry.getInstrumentation().runOnMainSync { list.scrollBy(0, SCROLL_STEP) }
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      boundRows = list.adapter?.itemCount ?: 0
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    assertEquals(
      "scrolling a list of switches emitted change events for rows nobody touched: " +
        switchEvents.take(5),
      emptyList<String>(),
      switchEvents.toList(),
    )
    assertEquals(
      "scrolling a list of text fields emitted change events for text nobody typed: " +
        textEvents.take(5),
      emptyList<String>(),
      textEvents.toList(),
    )
    assertTrue("the fixture was empty", boundRows > 0)
  }

  /**
   * Alternating switches and text fields, with *different* states either side of the alternation.
   *
   * Uniform rows would hide the bug: assigning `isChecked = false` to a switch that is already
   * false does not fire the listener, so a list where every switch is off stays silent whether or
   * not the listener was detached. Alternating the state is what makes every rebind a real change.
   */
  private fun tree(): Tree =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            rows =
              (0 until ROW_COUNT).map { index ->
                if (index % 2 == 0) {
                  RowSpec(
                    id = "switch-$index",
                    kind = RowKind.switch,
                    label = "Switch $index",
                    on = index % 4 == 0,
                  )
                } else {
                  RowSpec(
                    id = "field-$index",
                    kind = RowKind.textField,
                    text = "value $index",
                    placeholder = "Placeholder",
                  )
                }
              },
          )
        )
    )

  private companion object {
    const val ROW_COUNT = 400
    const val SCROLL_PASSES = 200
    const val SCROLL_STEP = 120
    const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
  }
}

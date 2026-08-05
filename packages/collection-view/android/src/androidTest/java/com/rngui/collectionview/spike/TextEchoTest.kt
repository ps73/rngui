package com.rngui.collectionview.spike

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.Item
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.RowView
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A commit that is one keystroke behind must not be allowed to rewrite the field.
 *
 * Every keystroke goes to JavaScript and comes back as a new tree, and on a real device the trip is
 * slower than a person types. So the value arriving is routinely *stale*, and a field that assigns
 * whatever it is handed both loses characters and — because `EditText.setText` selects offset zero
 * on the fresh `Editable` it installs — drops the caret to the front, so everything typed afterwards
 * lands in front of what came before. Typing `Rehearsal-on-Thursday` into the naive version produced
 * `-on-ThursdayRehear`, which is what this exists to prevent.
 *
 * The fixture types the way a person does — one character at a time, each firing its own change —
 * and then delivers the commits out of step with it, which is the only way to reproduce a race
 * whose ingredients are both inside the same method.
 */
@RunWith(AndroidJUnit4::class)
class TextEchoTest {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  private val sent = CopyOnWriteArrayList<String>()

  private val events =
    object : RowEvents {
      override fun onRowPress(rowId: String) = Unit

      override fun onSwitchChange(rowId: String, value: Boolean) = Unit

      override fun onTextChange(rowId: String, value: String) {
        sent += value
      }

      override fun onFocusChange(rowId: String, focused: Boolean) = Unit

      override fun onDateChange(rowId: String, millis: Double) = Unit

      override fun onMenuSelect(rowId: String, itemId: String) = Unit

      override fun onSwipeAction(rowId: String, actionId: String) = Unit

      override fun onSliderChange(rowId: String, value: Double) = Unit

      override fun onSliderCommit(rowId: String, value: Double) = Unit
    }

  @Test
  fun aStaleCommitLeavesTheFieldAlone() {
    lateinit var view: RowView
    lateinit var field: EditText
    lateinit var style: Pair<RowStyle, ListStyle>
    var afterStale = ""
    var afterCurrent = ""
    var caretAfterTyping = 0

    onMain { activity ->
      style = styles(activity)
      view = RowView(themed(activity), RowKind.textField, events)
      activity.setContentView(FrameLayout(activity).apply { addView(view) })
    }

    onMain {
      view.bind(row(text = ""), style.first, style.second, Item.Position.only)
      field = requireNotNull(view.editText()) { "the textField row has no EditText" }
      assertTrue("the field never took focus", field.requestFocus())
    }

    onMain {
      // One character at a time, because that is what makes each one its own change event — and the
      // pending list is keyed on exactly those values.
      "abc".forEach { field.append(it.toString()) }
      caretAfterTyping = field.selectionEnd
    }

    onMain {
      // The commit carrying the *first* keystroke arrives while the field already holds `abc`.
      view.bind(row(text = "a"), style.first, style.second, Item.Position.only)
      afterStale = field.text.toString()

      // And then the one that has caught up. Also an echo, and also a no-op.
      view.bind(row(text = "abc"), style.first, style.second, Item.Position.only)
      afterCurrent = field.text.toString()
    }

    assertEquals("typing did not report each keystroke", listOf("a", "ab", "abc"), sent.toList())
    assertEquals("the caret did not follow the typing", 3, caretAfterTyping)
    assertEquals("a stale commit overwrote what the user had typed", "abc", afterStale)
    assertEquals("the field lost text to a commit that agreed with it", "abc", afterCurrent)
  }

  /**
   * The echo list must not swallow a value JavaScript genuinely means.
   *
   * A clear, an input mask, a value set from another screen — all of them arrive by the same route
   * as an echo, and a field that ignored them would be a field JavaScript cannot write to at all.
   * The bug this guards is the over-correction of the one above.
   */
  @Test
  fun aValueTheFieldNeverSentIsApplied() {
    lateinit var view: RowView
    lateinit var field: EditText
    lateinit var style: Pair<RowStyle, ListStyle>
    var afterInstruction = ""
    var caret = 0

    onMain { activity ->
      style = styles(activity)
      view = RowView(themed(activity), RowKind.textField, events)
      activity.setContentView(FrameLayout(activity).apply { addView(view) })
    }

    onMain {
      view.bind(row(text = ""), style.first, style.second, Item.Position.only)
      field = requireNotNull(view.editText())
      assertTrue(field.requestFocus())
    }

    onMain { "abc".forEach { field.append(it.toString()) } }

    onMain {
      view.bind(row(text = "MASKED"), style.first, style.second, Item.Position.only)
      afterInstruction = field.text.toString()
      caret = field.selectionEnd
    }

    assertEquals("a value JavaScript set was ignored as an echo", "MASKED", afterInstruction)
    // The caret was at the end when the value arrived, so the end is where it belongs — not at zero,
    // which is where `setText` leaves it.
    assertEquals("the caret was left at the start of the new value", "MASKED".length, caret)
  }

  private fun row(text: String) =
    RowSpec(id = "title", kind = RowKind.textField, text = text, placeholder = "Title")

  private fun themed(base: Context): Context = AppearanceResolver.themedContext(base, isDark = false)

  private fun styles(base: Context): Pair<RowStyle, ListStyle> {
    val context = themed(base)
    val resolver = AppearanceResolver(context = context, isDark = false, light = null, dark = null)
    val rowStyle = RowStyle.of(resolver)
    return rowStyle to
      ListStyle.of(context, resolver, rowStyle, ListAppearance.insetGrouped, requested = null)
  }

  /** The row builds its own control, so the test finds it rather than being handed one. */
  private fun View.editText(): EditText? =
    when {
      this is EditText -> this
      this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).editText() }
      else -> null
    }

  private fun onMain(body: (android.app.Activity) -> Unit) {
    activityRule.scenario.onActivity { activity -> body(activity) }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }
}

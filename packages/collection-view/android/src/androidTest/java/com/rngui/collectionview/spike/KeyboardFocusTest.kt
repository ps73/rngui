package com.rngui.collectionview.spike

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.CollectionAdapter
import com.rngui.collectionview.FlattenedTree
import com.rngui.collectionview.FocusScroller
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.ParkingView
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `keyboardAware`'s second half: a field focused behind the keyboard has to come out from under it.
 *
 * **The keyboard is a bottom padding here, and that is not a shortcut.** In production
 * `InsetController` turns the IME's height into exactly that padding, and `RecyclerView` scrolls
 * against `getHeight() - getPaddingBottom()` — so a list padded by 800px is, as far as every line
 * of code under test is concerned, a list with an 800px keyboard over it. Simulating the inset is
 * what makes the assertion possible at all: nothing in this suite can construct
 * `RNGUICollectionViewView`, which needs a live React instance, and the emulator's own IME renders
 * floating and contributes no window inset to react to.
 *
 * What that leaves untested is the wiring — that `onFocusChange` and the inset callback actually
 * call this. Those are two one-line hooks; this is the part with arithmetic in it.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardFocusTest {
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
  fun aFieldBehindTheKeyboardIsScrolledClearOfIt() {
    lateinit var list: RecyclerView
    var field: EditText? = null
    var coveredBefore = false
    var bottomAfter = 0
    var visibleBottomAfter = 0

    onMain { activity ->
      list = buildList(activity)
      activity.setContentView(
        FrameLayout(activity).apply { addView(list, MATCH, MATCH) }
      )
    }

    onMain {
      // The far end of the list, which is where the field is — the case `keyboardAware` exists for.
      list.scrollToPosition(ROW_COUNT)
    }

    onMain {
      field = list.findEditText()
      // The keyboard arrives: `InsetController` would set exactly this.
      list.setPadding(0, 0, 0, KEYBOARD_PX)
      list.clipToPadding = false
    }

    onMain {
      val input = requireNotNull(field) { "the fixture never laid out its text field" }
      input.requestFocus()
      // Precondition, not decoration: if the field were already clear of the padding the assertion
      // below would pass without anything having moved.
      coveredBefore = input.bottomIn(list) > list.height - KEYBOARD_PX
    }

    onMain {
      val input = requireNotNull(field)
      FocusScroller.scrollIntoView(input, extraPx = 0, immediate = true)
    }

    onMain {
      val input = requireNotNull(field)
      bottomAfter = input.bottomIn(list)
      visibleBottomAfter = list.height - KEYBOARD_PX
    }

    assertNotNull("the fixture never laid out its text field", field)
    assertTrue(
      "the field was not behind the keyboard to begin with, so this proved nothing",
      coveredBefore,
    )
    assertTrue(
      "the field is still under the keyboard: bottom $bottomAfter, keyboard starts at " +
        "$visibleBottomAfter",
      bottomAfter <= visibleBottomAfter,
    )
  }

  /** The caret, not the row — see [FocusScroller.caretRect]. */
  @Test
  fun theTargetIsTheCaretRatherThanTheWholeField() {
    lateinit var list: RecyclerView
    var caretHeight = 0
    var fieldHeight = 0

    onMain { activity ->
      list = buildList(activity)
      activity.setContentView(
        FrameLayout(activity).apply { addView(list, MATCH, MATCH) }
      )
    }

    onMain { list.scrollToPosition(ROW_COUNT) }

    onMain {
      val input = requireNotNull(list.findEditText())
      val caret = requireNotNull(FocusScroller.caretRect(input)) {
        "an EditText with a layout produced no caret rect"
      }
      caretHeight = caret.height()
      fieldHeight = input.height
    }

    assertTrue("the caret rect had no height", caretHeight > 0)
    assertTrue(
      "the caret rect is the whole field ($caretHeight of $fieldHeight), so bringing it into " +
        "view would centre a tall row and leave the line being typed under the keyboard",
      caretHeight < fieldHeight,
    )
  }

  private fun buildList(activity: android.app.Activity): RecyclerView {
    val themed = AppearanceResolver.themedContext(activity, isDark = false)
    val resolver = AppearanceResolver(context = themed, isDark = false, light = null, dark = null)
    val rowStyle = RowStyle.of(resolver)
    val listStyle =
      ListStyle.of(themed, resolver, rowStyle, ListAppearance.insetGrouped, requested = null)

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

    return RecyclerView(activity).apply {
      layoutManager = LinearLayoutManager(activity)
      this.adapter = adapter
    }
  }

  /**
   * Plain rows, then one text field at the very end.
   *
   * The same shape as the example's Reminders screen, and for the same reason: a field at the top
   * of a short list is never behind the keyboard, so it would test nothing.
   */
  private fun tree(): Tree =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            rows =
              (0 until ROW_COUNT).map {
                RowSpec(id = "row-$it", label = "Row $it")
              } +
                RowSpec(
                  id = "tail",
                  // A `textArea` rather than a `textField`, and a grown one: a single-line field's
                  // caret *is* its height, so it could not show the difference the caret path
                  // exists for.
                  kind = RowKind.textArea,
                  text = TALL_TEXT,
                ),
          )
        )
    )

  private fun RecyclerView.findEditText(): EditText? {
    fun search(view: View): EditText? =
      when {
        view is EditText -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { search(view.getChildAt(it)) }
        else -> null
      }
    return search(this)
  }

  /** The view's bottom edge in the list's coordinates, however deeply it is nested. */
  private fun View.bottomIn(list: RecyclerView): Int {
    val mine = IntArray(2)
    val theirs = IntArray(2)
    getLocationOnScreen(mine)
    list.getLocationOnScreen(theirs)
    return mine[1] - theirs[1] + height
  }

  private fun onMain(body: (android.app.Activity) -> Unit) {
    activityRule.scenario.onActivity { activity -> body(activity) }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  private companion object {
    const val ROW_COUNT = 40
    /** Roughly a phone keyboard, and more than a screen's worth of rows away from the top. */
    const val KEYBOARD_PX = 800

    /** Long enough to wrap to several lines at any sane width. */
    const val TALL_TEXT =
      "Bring the sheet music, a spare stand, the long cable, the tuner, and the folder of " +
        "arrangements from last term, plus whatever is left of the rosin."
    const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
  }
}

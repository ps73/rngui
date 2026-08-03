package com.rngui.collectionview.spike

import android.content.Context
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.GroupShape
import com.rngui.collectionview.Item
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.RowView
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A selected row's container has to *grow* into its shape, and only when it is the same row.
 *
 * Two failures, and they look nothing alike. Snapping straight to the stadium is the one the eye
 * calls cheap — the shape is what M3 uses to signal selection, and a shape that appears fully formed
 * reads as a redraw rather than as a response to the tap. Tweening on a *recycled* holder is far
 * worse: every row scrolling into view would morph out of whatever the previous occupant looked
 * like, so a fling becomes a screen full of rows changing shape for no reason.
 *
 * The distinction lives in one line of [RowView.bind] — same id, changed selection — so it is worth
 * a test that fails if either half of it goes.
 *
 * **No wall-clock waiting anywhere.** `ValueAnimator.start()` lands on fraction 0 synchronously, so
 * reading the container immediately afterwards says whether an animation was started or the value
 * was simply assigned. That holds whether or not the device has animations turned off, which keeps
 * this from being a test that quietly stops asserting anything on a machine with the animator scale
 * at zero.
 */
@RunWith(AndroidJUnit4::class)
class SelectionTransitionTest {
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

  /**
   * Every assertion below is "did this land on A or on B", so a fixture where A and B coincide
   * would pass all of them without testing anything. Checked once, loudly, rather than restated
   * defensively in each test.
   */
  @Before
  fun theTwoStatesAreDistinguishable() {
    onMain { context ->
      val light = Fixture(context)
      val dark = Fixture(context, isDark = true)
      assertTrue(
        "selected and unselected draw the same radius",
        abs(light.radius(selected = true) - light.radius(selected = false)) > TOLERANCE_PX,
      )
      assertTrue(
        "light and dark fill a selected row with the same colour",
        light.fill(selected = true) != dark.fill(selected = true),
      )
    }
  }

  @Test
  fun theSameRowChangingStateGrowsIntoItsShape() {
    var before = 0f
    var immediatelyAfter = 0f
    var settled = 0f
    var target = 0f

    onMain { context ->
      val fixture = Fixture(context)
      val view = RowView(fixture.context, RowKind.switch, silent)
      target = fixture.radius(selected = true)

      view.bind(row(on = false), fixture.rowStyle, fixture.listStyle, Item.Position.only)
      before = view.containerForTest.currentRadius()

      view.bind(row(on = true), fixture.rowStyle, fixture.listStyle, Item.Position.only)
      immediatelyAfter = view.containerForTest.currentRadius()

      view.containerForTest.settle()
      settled = view.containerForTest.currentRadius()
    }

    assertEquals(
      "the container jumped to the selected shape instead of animating into it",
      before,
      immediatelyAfter,
      TOLERANCE_PX,
    )
    assertEquals("the transition never reached the selected shape", target, settled, TOLERANCE_PX)
  }

  @Test
  fun aRecycledHolderSnapsToItsNewShape() {
    var afterRecycle = 0f
    var target = 0f

    onMain { context ->
      val fixture = Fixture(context)
      val view = RowView(fixture.context, RowKind.switch, silent)
      target = fixture.radius(selected = false)

      view.bind(row(on = true), fixture.rowStyle, fixture.listStyle, Item.Position.only)
      view.containerForTest.settle()

      // A different id: this is the holder being reused for another row, not that row changing.
      view.bind(
        row(id = "other", on = false),
        fixture.rowStyle,
        fixture.listStyle,
        Item.Position.only,
      )
      afterRecycle = view.containerForTest.currentRadius()
    }

    assertEquals(
      "a recycled holder animated into its new shape — during a fling that is every row on screen",
      target,
      afterRecycle,
      TOLERANCE_PX,
    )
  }

  /**
   * A restyle is not a state change either, and only the colour can show it.
   *
   * A theme flip rebinds every item with the same ids and the same selection, so the radius does
   * not move and the fill does. If that were treated as a transition, the flip would be as many
   * simultaneous colour animations as there are visible rows — and the row's text, which has no
   * animation, would arrive a quarter of a second before its container.
   */
  @Test
  fun aRestyleSnaps() {
    var afterRestyle = 0
    var target = 0

    onMain { context ->
      val light = Fixture(context)
      val dark = Fixture(context, isDark = true)
      val view = RowView(light.context, RowKind.switch, silent)
      target = dark.fill(selected = true)

      view.bind(row(on = true), light.rowStyle, light.listStyle, Item.Position.only)
      view.containerForTest.settle()

      view.bind(row(on = true), dark.rowStyle, dark.listStyle, Item.Position.only)
      afterRestyle = view.containerForTest.currentFill()
    }

    assertEquals("a restyle animated the container's fill", target, afterRestyle)
  }

  private fun row(id: String = "flag", on: Boolean) =
    RowSpec(id = id, kind = RowKind.switch, label = "Flag", on = on)

  private fun onMain(body: (Context) -> Unit) {
    activityRule.scenario.onActivity { activity -> body(activity) }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  /** Exactly what the real view resolves, so the expected values come from the same place. */
  private class Fixture(base: Context, isDark: Boolean = false) {
    val context: Context = AppearanceResolver.themedContext(base, isDark)
    private val resolver =
      AppearanceResolver(context = context, isDark = isDark, light = null, dark = null)
    val rowStyle: RowStyle = RowStyle.of(resolver)
    val listStyle: ListStyle =
      ListStyle.of(context, resolver, rowStyle, ListAppearance.insetGrouped, requested = null)

    fun radius(selected: Boolean): Float =
      GroupShape.radii(context, Item.Position.only, listStyle.style, listStyle.grouped, selected)[0]

    fun fill(selected: Boolean): Int =
      GroupShape.fill(
        style = listStyle.style,
        grouped = listStyle.grouped,
        selected = selected,
        rowBackground = listStyle.rowBackground,
        container = listStyle.containerColor,
        selectedContainer = listStyle.selectedContainer,
      )
  }

  private companion object {
    /** Radii are floats derived from density; a half-pixel is well below anything visible. */
    const val TOLERANCE_PX = 0.5f
  }
}

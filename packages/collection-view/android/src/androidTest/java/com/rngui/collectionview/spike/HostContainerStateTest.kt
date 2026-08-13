package com.rngui.collectionview.spike

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.HostContainer
import com.rngui.collectionview.Item
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.ParkingView
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.generated.ListAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two pieces of [HostContainer] state a recycled holder can carry into the wrong row.
 *
 * [HostReloadTest] covers the ownership race. These cover what the holder *keeps*: the child's own
 * visibility, which this library must never write, and the card background, which it must write in
 * both directions.
 */
@RunWith(AndroidJUnit4::class)
class HostContainerStateTest {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  /**
   * **The Android half of the bug that blanked unrelated screens on iOS.**
   *
   * React owns a hosted child's `visibility` — it is how `display: none` is expressed — and React's
   * mounting layer never restores a value this library wrote. So `claim` and `release` move the
   * child between the bay and the holder and touch nothing else. A child handed over `GONE` must
   * still be `GONE` after a full round trip, and one handed over `VISIBLE` must still be `VISIBLE`.
   */
  @Test
  fun claimAndReleaseNeverWriteTheChildsVisibility() {
    for (initial in listOf(View.VISIBLE, View.INVISIBLE, View.GONE)) {
      lateinit var child: View
      lateinit var parking: ParkingView
      lateinit var container: HostContainer

      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        activityRule.scenario.onActivity { activity ->
          parking = ParkingView(activity)
          container = HostContainer(activity)
          child = TextView(activity).apply { visibility = initial }
          parking.addView(child)

          container.claim(child, parking)
          assertSame("claim did not take the child", child, container.hosted)
          assertEquals(
            "claim wrote the child's visibility (was $initial)",
            initial,
            child.visibility,
          )

          container.release(parking)
          assertNull("release did not let the child go", container.hosted)
          assertSame("release did not return the child to the bay", parking, child.parent)
          assertEquals(
            "release wrote the child's visibility (was $initial)",
            initial,
            child.visibility,
          )
        }
      }
    }
  }

  /**
   * A holder is pooled per row *kind*, not per row, so the same [HostContainer] is handed a `card`
   * row and then a `none` row and then a `card` row again. Each bind has to state the answer
   * outright: the `none` case must clear a background it did not install, and the `card` case that
   * follows must put back the drawable the first one built rather than leaving the row bare.
   */
  @Test
  fun cardThenNoneThenCardRestoresTheBackground() {
    lateinit var container: HostContainer

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      activityRule.scenario.onActivity { activity ->
        val themed = AppearanceResolver.themedContext(activity, isDark = false)
        val resolver =
          AppearanceResolver(context = themed, isDark = false, light = null, dark = null)
        val rowStyle = RowStyle.of(resolver)
        val listStyle =
          ListStyle.of(themed, resolver, rowStyle, ListAppearance.insetGrouped, requested = null)
        container = HostContainer(activity)

        assertNull("a fresh holder should draw nothing", container.background)

        container.applyBackground(Item.Position.only, listStyle, card = true)
        val first = container.background
        assertNotNull("card = true installed no background", first)

        container.applyBackground(Item.Position.only, listStyle, card = false)
        assertNull("card = false left the previous row's card behind", container.background)

        container.applyBackground(Item.Position.only, listStyle, card = true)
        assertSame(
          "the second card bind built a new drawable instead of reinstating the first",
          first,
          container.background,
        )
      }
    }
  }

}

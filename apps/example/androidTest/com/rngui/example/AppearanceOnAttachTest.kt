package com.rngui.example

import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.RNGUICollectionViewView
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A list that was off-screen when the theme flipped has to catch up when it comes back.
 *
 * **The bug this covers shipped, and the fix for it had no automated test until now.** Android
 * dispatches `onConfigurationChanged` down the *attached* hierarchy and stops at whatever is not
 * currently in it — and `react-native-screens` detaches every tab except the visible one. So the
 * screen being looked at restyled and every other one came back in the old palette, against a
 * header and tab bar that had already changed.
 *
 * It could only ever be tested here. The handler lives on `RNGUICollectionViewView`, which takes a
 * `ThemedReactContext`, and nothing in the library's own suite can produce one — so the fix was
 * verified by hand on an emulator and left at that.
 *
 * **The detach is the honest part of the fixture.** Removing the view from its parent is exactly
 * what `react-native-screens` does to an unfocused tab, and doing it directly is far more
 * deterministic than driving the tab bar and hoping the framework detached what we think it did.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceOnAttachTest {
  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

  @After
  fun restoreLightMode() {
    LiveApp.shell("cmd uimode night no")
  }

  @Test
  fun aDetachedListRestylesWhenItComesBack() {
    LiveApp.shell("cmd uimode night no")

    var light = 0
    var whileDetached = 0
    var afterReattach = 0

    // The reference is captured on the main thread; the *polling* has to happen off it, because
    // `runOnMainSync` throws when it is already there.
    lateinit var activity: MainActivity
    activityRule.scenario.onActivity { activity = it }
    val view: RNGUICollectionViewView = LiveApp.awaitCollectionView(activity)

    light = LiveApp.await("the list never painted a background") { view.backgroundColorOrNull() }

    // Off-screen, as an unfocused tab is.
    var parent: ViewGroup? = null
    var index = -1
    LiveApp.onMain {
      parent = view.parent as? ViewGroup
      index = parent?.indexOfChild(view) ?: -1
      parent?.removeView(view)
    }
    LiveApp.settle()

    // The mode moves while it is not in the hierarchy, so `onConfigurationChanged` never reaches it.
    LiveApp.shell("cmd uimode night yes")
    awaitNightMode(expected = true)
    whileDetached = view.backgroundColorOrNull() ?: 0

    LiveApp.onMain { parent?.addView(view, index) }
    LiveApp.settle()

    afterReattach =
      LiveApp.await("the list never repainted after coming back") {
        view.backgroundColorOrNull()?.takeIf { it != whileDetached }
      }

    // The precondition, stated rather than assumed: if the detached view had already restyled,
    // the assertion below would pass without the attach handler existing at all.
    assertEquals(
      "the detached view restyled on its own, so this fixture proves nothing",
      light,
      whileDetached,
    )
    assertNotEquals(
      "the list came back in the light palette against a dark system",
      light,
      afterReattach,
    )
  }

  private fun RNGUICollectionViewView.backgroundColorOrNull(): Int? {
    var colour: Int? = null
    LiveApp.onMain { colour = (background as? ColorDrawable)?.color }
    return colour
  }

  /** The configuration is updated on its own message; reading it too early reports the old mode. */
  private fun awaitNightMode(expected: Boolean) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    LiveApp.await("the device never entered night mode = $expected") {
      val night =
        (context.resources.configuration.uiMode and
          android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
          android.content.res.Configuration.UI_MODE_NIGHT_YES
      if (night == expected) true else null
    }
    LiveApp.settle()
  }
}

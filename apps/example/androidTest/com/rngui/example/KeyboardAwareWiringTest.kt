package com.rngui.example

import android.graphics.Rect
import android.widget.EditText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rngui.collectionview.RNGUICollectionViewView
import com.rngui.example.LiveApp.firstEditText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two hooks that turn an IME into a scroll, driven through the real component.
 *
 * `FocusScroller` is already tested in the library, but only as arithmetic — nothing proved that
 * `RNGUICollectionViewView` ever *calls* it. The gap mattered: the inset callback fires for system
 * bars and re-attaches as well as for the keyboard, so the guard that tells those apart is exactly
 * the sort of thing that can be inverted without any test noticing.
 *
 * **The insets are dispatched rather than typed, and that is not a shortcut.** The emulator's
 * Gboard renders floating and contributes no `ime()` inset at all, so a real keyboard cannot drive
 * this on the only hardware available — and even where it can, what the OS delivers is precisely a
 * `WindowInsetsCompat` with `Type.ime()` set. `ViewCompat.dispatchApplyWindowInsets` hands the
 * component the same object through the same listener `InsetController` registered. Everything
 * downstream of that — the padding, the guard, the scroll — is the real path.
 *
 * **What is asserted is the `keyboardAwareOffset` gap, not that the field is visible**, and the
 * first draft got that wrong: it passed with both hooks deleted. Android scrolls a newly focused
 * view into view by itself — `ViewRootImpl.scrollToRectOrFocus` on an inset change, and
 * `requestChildRectangleOnScreen` up the parent chain on a focus change — so "the field can be
 * seen" measures the platform and nothing of ours. The air under it is what could not have happened
 * without this library.
 *
 * **The inset hook is deliberately not tested here, and the measurement is why.** With the hook
 * removed the platform still lands the field 43px clear of the keyboard, against 52px with it — the
 * row's own bottom padding does most of the work, and 9px of separation is not a test, it is a
 * coincidence waiting to be flipped by a padding change. What that hook genuinely adds cannot be
 * seen in an end state at all: it scrolls once per frame of the IME's animation, so the list and
 * the keyboard share one timeline instead of the list jumping when the resize settles. Its
 * arithmetic is covered by `FocusScroller`'s own tests in the library; its timing is not covered by
 * anything, and saying so is better than an assertion that passes either way.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardAwareWiringTest {
  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

  /**
   * The other hook: focus moving between fields with the keyboard already up.
   *
   * No inset changes for that — the keyboard never moves — so the inset callback above cannot cover
   * it, and without the focus hook a caret can end up behind a keyboard that was already there.
   */
  @Test
  fun focusMovingUnderAStandingKeyboardAlsoScrolls() {
    var bottomAfter = 0
    var keyboardTop = 0
    var scrolledAway = false

    LiveApp.openTab("Reminders")
    lateinit var activity: MainActivity
    activityRule.scenario.onActivity { activity = it }
    val view: RNGUICollectionViewView = LiveApp.awaitCollectionView(activity)

    // The keyboard is already up, and stays up for the rest of the test.
    LiveApp.onMain { ViewCompat.dispatchApplyWindowInsets(view, imeInsets(KEYBOARD_PX)) }
    LiveApp.settle()

    LiveApp.onMain { view.list.scrollToPosition(view.flattened.items.size - 1) }
    LiveApp.settle()

    val field =
      LiveApp.await("the Reminders screen never laid out a text field") {
        var found: EditText? = null
        LiveApp.onMain { found = view.firstEditText() }
        found
      }

    // Put it behind the standing keyboard without any inset moving, which is what a scroll does.
    LiveApp.onMain {
      keyboardTop = view.list.height - KEYBOARD_PX
      view.list.scrollBy(0, -(view.list.height / 2))
    }
    LiveApp.settle()
    LiveApp.onMain { scrolledAway = field.bottomIn(view) > keyboardTop }

    LiveApp.onMain { field.requestFocus() }

    // **Polled, because this hook animates and the other one does not.** The inset path scrolls
    // immediately so it rides the IME's own timeline; nothing is animating when focus merely moves,
    // so that path smooth-scrolls instead — and a smooth scroll is several frames that
    // `waitForIdleSync` knows nothing about. Reading once here made the test fail 79px short, which
    // is a scroll caught in flight rather than a scroll that never happened.
    val deadline = System.currentTimeMillis() + SETTLE_MS
    do {
      LiveApp.settle()
      LiveApp.onMain { bottomAfter = field.bottomIn(view) }
    } while (keyboardTop - bottomAfter < requiredGap(view) &&
      System.currentTimeMillis() < deadline)

    assertTrue(
      "the field was already clear of the keyboard, so focusing it proved nothing",
      scrolledAway,
    )
    assertTrue(
      "focus moved under a standing keyboard and the field was left flush against it: field " +
        "bottom $bottomAfter, keyboard starts at $keyboardTop, and the screen asked for " +
        "${OFFSET_DP}dp of air",
      keyboardTop - bottomAfter >= requiredGap(view),
    )
  }

  /**
   * The air the screen asked for, in pixels.
   *
   * Deliberately less than the full `keyboardAwareOffset` + the library's own margin: the point is
   * to sit unambiguously above zero, which is where the platform's own focus scroll stops, without
   * pinning the test to an exact arithmetic it has no business restating.
   */
  private fun requiredGap(view: android.view.View): Int =
    (OFFSET_DP * view.resources.displayMetrics.density).toInt()

  private fun imeInsets(bottom: Int): WindowInsetsCompat =
    WindowInsetsCompat.Builder()
      .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
      .build()

  private fun android.view.View.bottomIn(other: android.view.View): Int {
    val mine = Rect()
    val theirs = Rect()
    getGlobalVisibleRect(mine)
    other.getGlobalVisibleRect(theirs)
    return mine.bottom - theirs.top
  }

  private companion object {
    /** Roughly a phone keyboard. The exact height does not matter; that it is an `ime()` inset does. */
    const val KEYBOARD_PX = 800

    /** Long enough for a smooth scroll to finish, short enough that a failure is not a hang. */
    const val SETTLE_MS = 5_000

    /** What the Reminders screen passes as `keyboardAwareOffset`. */
    const val OFFSET_DP = 12
  }
}

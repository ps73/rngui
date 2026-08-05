package com.rngui.example

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.RNGUICollectionViewView
import com.rngui.collectionview.SectionIndexView
import com.rngui.collectionview.StickyHeaderDecoration
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A theme flip has to reach everything the list draws, not only its rows.
 *
 * **This covers the two call sites, which is a different claim from the two methods.** The library's
 * own `HeaderRestyleTest` proves `StickyHeaderDecoration.restyle()` works when something calls it,
 * and it kills a mutation that empties that method. It does not notice if
 * `RNGUICollectionViewView.restyle()` stops calling it — and *that* was the actual defect. The
 * pinned header and the scrubber thumb both had a working restyle path the moment one existed; what
 * they never had was anything invoking it, because the only route in was `commitProps`, and a system
 * mode change commits nothing.
 *
 * So the mutation this exists to catch is deleting a line, not emptying a method.
 *
 * Like [AppearanceOnAttachTest], it can only live here: the handler is on
 * `RNGUICollectionViewView`, which takes a `ThemedReactContext` that nothing in the library's own
 * suite can produce.
 *
 * **Contacts, because it is the only screen that draws both.** Pinned headers need `plain`, and the
 * scrubber needs `sectionIndex` — Contacts is the screen that asks for both, and the one where the
 * bug was visible.
 */
@RunWith(AndroidJUnit4::class)
class RestyleReachTest {
  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

  @After
  fun restoreLightMode() {
    LiveApp.shell("cmd uimode night no")
  }

  @Test
  fun aModeFlipRepaintsThePinnedHeaderAndTheScrubber() {
    LiveApp.shell("cmd uimode night no")

    lateinit var activity: MainActivity
    activityRule.scenario.onActivity { activity = it }
    LiveApp.awaitCollectionView(activity)

    LiveApp.openTab("Contacts")
    LiveApp.settle()

    val view = LiveApp.awaitCollectionView(activity)
    val decoration =
      LiveApp.await("the list has no sticky-header decoration") { view.stickyHeaderDecoration() }
    val scrubber = LiveApp.await("the list has no section index") { view.sectionIndexView() }

    // The header only exists once something has drawn it, and the app draws on its own schedule.
    val lightHeader =
      LiveApp.await("no header was ever pinned — is Contacts still a `plain` list?") {
        onMainValue { decoration.pinnedHeaderTextColor() }
      }
    val lightThumb = onMainValue { scrubber.thumbColor }

    // Attached this time, so `onConfigurationChanged` does reach the view — the ordinary path, not
    // the detach/reattach one `AppearanceOnAttachTest` covers.
    LiveApp.shell("cmd uimode night yes")

    val darkHeader =
      LiveApp.await("the pinned header never repainted — did `restyle()` stop calling it?") {
        onMainValue { decoration.pinnedHeaderTextColor() }?.takeIf { it != lightHeader }
      }
    val darkThumb =
      LiveApp.await("the scrubber thumb never repainted — did `restyle()` stop calling it?") {
        onMainValue { scrubber.thumbColor }.takeIf { it != lightThumb }
      }

    // Both `await`s above already fail on no-change; these restate the claim so a reader of the
    // failure output sees which value was wrong rather than only which poll timed out.
    assertNotEquals("the pinned header kept its light palette", lightHeader, darkHeader)
    assertNotEquals("the scrubber thumb kept its light palette", lightThumb, darkThumb)
  }

  private fun <T> onMainValue(read: () -> T): T {
    var value: T? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { value = read() }
    @Suppress("UNCHECKED_CAST")
    return value as T
  }

  /**
   * The decoration is registered on the list rather than being a view, so it is reached through
   * `RecyclerView`'s public decoration list rather than by walking children.
   */
  private fun RNGUICollectionViewView.stickyHeaderDecoration(): StickyHeaderDecoration? =
    onMainValue {
      val list = firstOfType(RecyclerView::class.java) ?: return@onMainValue null
      (0 until list.itemDecorationCount)
        .map { list.getItemDecorationAt(it) }
        .filterIsInstance<StickyHeaderDecoration>()
        .firstOrNull()
    }

  private fun RNGUICollectionViewView.sectionIndexView(): SectionIndexView? = onMainValue {
    firstOfType(SectionIndexView::class.java)
  }

  /** Takes a `Class` rather than a reified type, because an inline reified function cannot recurse. */
  private fun <T : View> View.firstOfType(type: Class<T>): T? {
    if (type.isInstance(this)) return type.cast(this)
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
      getChildAt(index).firstOfType(type)?.let {
        return it
      }
    }
    return null
  }
}

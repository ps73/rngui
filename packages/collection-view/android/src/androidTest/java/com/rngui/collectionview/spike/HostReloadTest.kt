package com.rngui.collectionview.spike

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.CollectionAdapter
import com.rngui.collectionview.FlattenedTree
import com.rngui.collectionview.HostContainer
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.ParkingView
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M8's done-when: a reload mid-scroll must leave every hosted view visible.
 *
 * **This is the exact reproduction that caught the iOS bug, and it cost two wrong diagnoses before
 * anyone built it.** `onBindViewHolder` for the incoming holder can run *before* `onViewRecycled`
 * for the outgoing one — UIKit has the same ordering — so a naive `release()` hides a view the
 * replacement holder has already claimed. The symptom is hosted rows going blank after a reload,
 * which reads as a React problem, and "stale Fast Refresh" was blamed twice.
 *
 * The guard is one line in [HostContainer.release]: return unless this container still owns the
 * child. This asserts the behaviour that line buys.
 */
@RunWith(AndroidJUnit4::class)
class HostReloadTest {
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
    }

  @Test
  fun reloadingMidScrollLeavesEveryHostedViewVisible() {
    lateinit var list: RecyclerView
    lateinit var adapter: CollectionAdapter
    lateinit var parking: ParkingView
    val children = mutableListOf<View>()

    activityRule.scenario.onActivity { activity ->
      parking = ParkingView(activity)
      repeat(ROW_COUNT) { index ->
        val child = TextView(activity).apply { text = "hosted $index" }
        children += child
        parking.addView(child)
      }

      val themed = AppearanceResolver.themedContext(activity, isDark = false)
      val resolver =
        AppearanceResolver(context = themed, isDark = false, light = null, dark = null)
      val rowStyle = RowStyle.of(resolver)
      adapter =
        CollectionAdapter(
          themed,
          rowStyle,
          ListStyle.of(themed, resolver, rowStyle, ListAppearance.plain, requested = null),
          noEvents,
          parking,
          hostChildAt = { index -> children.getOrNull(index) },
        )
      adapter.submitList(FlattenedTree.of(tree(generation = 0)).items)

      list =
        RecyclerView(activity).apply {
          layoutManager = LinearLayoutManager(activity)
          this.adapter = adapter
          // Exactly what RNGUICollectionViewView configures. It matters here rather than being
          // incidental: a change animation keeps the *outgoing* holder attached while the incoming
          // one binds, so the list briefly has two holders per item and only one of them can own
          // the hosted child. Asserting against that configuration would be asserting against a
          // list this library never builds.
          (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
        }
      activity.setContentView(
        FrameLayout(activity).apply {
          addView(parking, FrameLayout.LayoutParams(0, 0))
          addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        }
      )
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    // Scroll, then reload, then scroll again — several times. The bug needs a reload to land while
    // holders are being recycled, so one attempt is a coin flip and ten is not.
    repeat(RELOADS) { generation ->
      InstrumentationRegistry.getInstrumentation().runOnMainSync { list.scrollBy(0, SCROLL_STEP) }
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        // A new list with the same ids: exactly what a theme change or an unrelated state update
        // produces, and the case where every holder is rebuilt while its identity survives.
        adapter.submitList(FlattenedTree.of(tree(generation + 1)).items)
      }
      InstrumentationRegistry.getInstrumentation().runOnMainSync { list.scrollBy(0, SCROLL_STEP) }
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    var checked = 0
    val blank = mutableListOf<Int>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      for (index in 0 until list.childCount) {
        val holder = list.getChildViewHolder(list.getChildAt(index))
        val container = (holder as? CollectionAdapter.HostHolder)?.container ?: continue
        checked++
        val hosted = container.hosted
        if (hosted == null || hosted.parent !== container || hosted.visibility != View.VISIBLE) {
          blank += holder.bindingAdapterPosition
        }
      }
    }

    assertTrue("no host rows were on screen, so this proved nothing", checked > 0)
    assertTrue(
      "after $RELOADS reloads mid-scroll, $blank of $checked visible host rows had no hosted " +
        "view — the ownership guard in HostContainer.release is not holding",
      blank.isEmpty(),
    )
  }

  /**
   * Each generation *reassigns which child belongs to which row*.
   *
   * That is the hazard, and a fixture that only changed a label would not reproduce it: the race
   * needs one hosted child to move from one container to another, which is exactly what windowing
   * does when the window slides and `hostIndex` is renumbered. A child then has a claim from its
   * new owner before its old owner has been told to let go.
   */
  private fun tree(generation: Int): Tree =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            rows =
              (0 until ROW_COUNT).map { index ->
                RowSpec(
                  id = "host-$index",
                  kind = RowKind.host,
                  hostIndex = (index + generation) % ROW_COUNT,
                  height = ROW_HEIGHT_DP.toDouble(),
                  label = "generation $generation",
                )
              },
          )
        )
    )

  private companion object {
    const val ROW_COUNT = 120
    const val ROW_HEIGHT_DP = 64
    const val SCROLL_STEP = 700
    const val RELOADS = 10
    const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
  }
}

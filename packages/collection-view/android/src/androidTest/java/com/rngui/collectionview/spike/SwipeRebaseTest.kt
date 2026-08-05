package com.rngui.collectionview.spike

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.CollectionAdapter
import com.rngui.collectionview.FlattenedTree
import com.rngui.collectionview.ListStyle
import com.rngui.collectionview.ParkingView
import com.rngui.collectionview.RowEvents
import com.rngui.collectionview.RowStyle
import com.rngui.collectionview.SwipeActionsCallback
import com.rngui.collectionview.SwipeTrayDecoration
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.SwipeActionSpec
import com.rngui.collectionview.generated.SwipeActionStyle
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An open swipe tray must follow the row it was opened on, not the slot that row was in.
 *
 * **The failure this exists for deletes the wrong contact.** The tray used to remember an adapter
 * position, and nothing invalidated it when the list changed underneath — so any commit at all
 * (a parent re-render, a navigation focus effect, a `colorScheme` flip) that inserted or removed a
 * row above the open one left the tray drawn beside a *different* row, with Delete wired to
 * whichever id had moved into the remembered slot. The delete path itself was always safe, because
 * `close()` runs before the commit that removes the row; that is precisely why this survived every
 * manual pass over the Contacts screen.
 *
 * Instrumented rather than local, because `ItemTouchHelper` and `RecyclerView` are the things being
 * driven. It stops short of `RNGUICollectionViewView`, which needs a live React instance — so the
 * one line not covered here is the `registerAdapterDataObserver` call in that view's `init`. Both
 * it and this test go through [SwipeActionsCallback.dataObserver], which is why that factory exists
 * rather than an anonymous object at the call site.
 *
 * **No gesture is simulated.** The tray is opened by calling the two callback methods a real drag
 * calls, in the order it calls them — `onChildDraw` while the finger is down, then `clearView` when
 * it lifts. Injecting motion events would add flake without adding coverage: everything this is
 * about happens after the finger is gone.
 */
@RunWith(AndroidJUnit4::class)
class SwipeRebaseTest {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  private val dispatched = mutableListOf<Pair<String, String>>()

  private lateinit var list: RecyclerView
  private lateinit var adapter: CollectionAdapter
  private lateinit var swipe: SwipeActionsCallback

  private val noEvents =
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

  private fun build(rowIds: List<String>) {
    activityRule.scenario.onActivity { activity ->
      val themed = AppearanceResolver.themedContext(activity, isDark = false)
      val resolver = AppearanceResolver(context = themed, isDark = false, light = null, dark = null)
      val rowStyle = RowStyle.of(resolver)
      // A local, and it has to be one. Inside the `apply` below, a bare `adapter` resolves to
      // `RecyclerView.adapter` — the receiver's own member outranks this class's property — so
      // `this.adapter = adapter` assigned the list's null adapter to itself and left it empty.
      // The symptom was a full-size RecyclerView with three items and zero children.
      val built =
        CollectionAdapter(
          themed,
          rowStyle,
          ListStyle.of(themed, resolver, rowStyle, ListAppearance.plain, requested = null),
          noEvents,
          ParkingView(activity),
          hostChildAt = { null },
          swipeTranslation = { position -> swipe.translationFor(position) },
        )
      adapter = built
      swipe =
        SwipeActionsCallback(
          actionsAt = built::swipeActionsAt,
          rowIdAt = built::rowIdAt,
          positionOfRow = built::positionOfRow,
          style = built::currentStyle,
          onAction = { rowId, actionId -> dispatched += rowId to actionId },
        )

      list =
        RecyclerView(activity).apply {
          layoutManager = LinearLayoutManager(activity)
          this.adapter = built
          itemAnimator = null
        }
      ItemTouchHelper(swipe).attachToRecyclerView(list)
      list.addOnItemTouchListener(swipe.touchListener())
      // The decoration is what fills `hitRects`, so a test that taps a button needs it — the same
      // three registrations `RNGUICollectionViewView.init` makes, in the same order.
      list.addItemDecoration(SwipeTrayDecoration(swipe))
      list.addOnChildAttachStateChangeListener(swipe.attachStateListener())
      built.registerAdapterDataObserver(swipe.dataObserver(list))

      activity.setContentView(list)
    }
    // Outside `onActivity`: `submit` blocks on the commit callback, which the diff posts *to* the
    // main thread. Waiting for it from the main thread is a deadlock.
    submit(rowIds)
    settle()
  }

  /**
   * Commits a list, exactly as `commitProps` does — through the adapter, so the observer fires.
   *
   * **Waits for the commit, and that is not optional.** `ListAdapter` only takes the synchronous
   * path when it has no previous list; every commit after the first diffs on a background thread
   * and dispatches later. Asserting straight afterwards read the pre-diff state, which failed one
   * of these tests outright and would have made the rest pass or fail on timing — a flake that
   * reports as a real regression. The commit callback is exact, where a poll would be a guess.
   */
  private fun submit(rowIds: List<String>) {
    val committed = java.util.concurrent.CountDownLatch(1)
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
      adapter.submitList(FlattenedTree.of(tree(rowIds)).items) { committed.countDown() }
    }
    check(committed.await(COMMIT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
      "the list never committed"
    }
  }

  /** A commit plus the layout pass that follows it, which is what every assertion below reads. */
  private fun submitAndSettle(rowIds: List<String>) {
    submit(rowIds)
    settle()
  }

  private fun tree(rowIds: List<String>) =
    Tree(
      sections =
        listOf(
          SectionSpec(
            id = "s",
            rows =
              rowIds.map { id ->
                RowSpec(
                  id = id,
                  label = id,
                  trailingActions =
                    listOf(
                      SwipeActionSpec(
                        id = "delete",
                        title = "Delete",
                        style = SwipeActionStyle.destructive,
                      )
                    ),
                )
              },
          )
        )
    )

  /** Opens the tray on a position by driving the two calls a real drag makes. */
  private fun openTrayAt(position: Int) {
    activityRule.scenario.onActivity {
      val holder =
        requireNotNull(list.findViewHolderForAdapterPosition(position)) {
          "no holder at $position — itemCount=${adapter.itemCount} childCount=${list.childCount} " +
            "size=${list.width}x${list.height} attached=${list.isAttachedToWindow} " +
            "parent=${list.parent} lm=${list.layoutManager}"
        }
      // Far enough past the reveal threshold that `clearView` pins rather than springs back; it is
      // clamped to the tray width inside.
      swipe.onChildDraw(
        Canvas(),
        list,
        holder,
        -10_000f,
        0f,
        ItemTouchHelper.ACTION_STATE_SWIPE,
        true,
      )
      swipe.clearView(list, holder)
    }
    settle()
  }

  /**
   * Runs the main looper out, so a posted layout or diff dispatch has happened before asserting.
   *
   * **And then waits for the list to actually have children.** `waitForIdleSync` alone returned
   * before the first traversal had laid the `RecyclerView` out, so every holder lookup came back
   * null and four of these tests failed on the fixture rather than on the thing under test. Polling
   * for the post-condition is the honest fix; a fixed sleep would only move the flake.
   */
  private fun settle() {
    val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    val deadline = android.os.SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
    while (true) {
      instrumentation.waitForIdleSync()
      var laidOut = false
      instrumentation.runOnMainSync {
        if (list.childCount == 0 && list.width > 0) {
          // The window traversal alone does not get there: the `RecyclerView` reaches full size
          // with three items in its adapter and zero children, and no amount of `requestLayout`
          // plus `waitForIdleSync` changes that — measured, by printing the state at the point of
          // failure. Driving measure/layout directly is what the sticky-header decoration already
          // does to size a view nothing else will lay out, and it is synchronous, which is the
          // property a test wants anyway.
          list.measure(
            View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(list.height, View.MeasureSpec.EXACTLY),
          )
          list.layout(list.left, list.top, list.right, list.bottom)
        }
        laidOut = list.childCount > 0
      }
      if (laidOut || android.os.SystemClock.uptimeMillis() > deadline) return
      Thread.sleep(16)
    }
  }

  // -- the fix ----------------------------------------------------------------------------------

  @Test
  fun trayFollowsItsRowWhenRowsAreInsertedAbove() {
    build(listOf("a", "b", "c"))
    openTrayAt(1)
    assertEquals("b", swipe.openRowId)
    assertEquals(1, swipe.openPosition)

    submitAndSettle(listOf("z0", "z1", "a", "b", "c"))

    // Same row, new slot.
    assertEquals("b", swipe.openRowId)
    assertEquals(3, swipe.openPosition)
  }

  @Test
  fun trayFollowsItsRowWhenRowsAreRemovedAbove() {
    build(listOf("a", "b", "c", "d"))
    openTrayAt(2)
    assertEquals("c", swipe.openRowId)

    submitAndSettle(listOf("c", "d"))

    assertEquals("c", swipe.openRowId)
    assertEquals(0, swipe.openPosition)
  }

  /**
   * The whole point, stated as the user-visible outcome.
   *
   * Before the fix this dispatched `"c"` — the id that had moved into slot 1 — and the example's
   * Contacts screen deleted that contact instead.
   */
  @Test
  fun tapDispatchesTheRowTheTrayWasOpenedOn() {
    build(listOf("a", "b", "c"))
    openTrayAt(1)

    submitAndSettle(listOf("z", "a", "b", "c"))

    tapFirstAction()

    assertEquals(listOf("b" to "delete"), dispatched)
  }

  /** A row that leaves the list takes its tray with it, rather than stranding it on a neighbour. */
  @Test
  fun trayIsDroppedWhenItsRowLeavesTheList() {
    build(listOf("a", "b", "c"))
    openTrayAt(1)
    assertEquals("b", swipe.openRowId)

    submitAndSettle(listOf("a", "c"))

    assertNull(swipe.openRowId)
    assertEquals(RecyclerView.NO_POSITION, swipe.openPosition)
    assertTrue(swipe.hitRects.isEmpty())
  }

  /** Nothing open, nothing to correct — and no scan of the list on every unrelated commit. */
  @Test
  fun rebaseIsInertWhenNoTrayIsOpen() {
    build(listOf("a", "b", "c"))

    submitAndSettle(listOf("z", "a", "b", "c"))

    assertNull(swipe.openRowId)
    assertEquals(RecyclerView.NO_POSITION, swipe.openPosition)
  }

  /** Sends a DOWN at the centre of the first drawn action button. */
  private fun tapFirstAction() {
    activityRule.scenario.onActivity {
      // The decoration fills `hitRects` as it draws, so make it draw first — onto a real bitmap,
      // because a canvas with no backing has a zero clip and everything is skipped.
      val bitmap =
        android.graphics.Bitmap.createBitmap(
          list.width.coerceAtLeast(1),
          list.height.coerceAtLeast(1),
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      list.draw(Canvas(bitmap))
      val rect = swipe.hitRects.firstOrNull()?.first
      requireNotNull(rect) { "no action button was drawn, so there is nothing to tap" }
      val event =
        MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY(), 0)
      list.dispatchTouchEvent(event)
      event.recycle()
    }
    settle()
  }

  private companion object {
    const val SETTLE_TIMEOUT_MS = 3_000L
    const val COMMIT_TIMEOUT_MS = 5_000L
  }
}

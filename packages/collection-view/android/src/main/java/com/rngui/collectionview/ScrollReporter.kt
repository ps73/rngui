package com.rngui.collectionview

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.events.Event
import androidx.recyclerview.widget.RecyclerView

/**
 * `UIScrollView` geometry, shaped exactly as `ScrollView` reports it.
 *
 * The shape is not ours to choose: `@gorhom/bottom-sheet` destructures
 * `{ contentOffset: { y } }` inside a worklet, and reanimated's `useAnimatedScrollHandler` types
 * its argument as React Native's `NativeScrollEvent`. Anything narrower would work against those
 * two and then fail in a way that reads as a reanimated bug.
 */
class ScrollEvent(
  surfaceId: Int,
  viewTag: Int,
  private val name: String,
  private val offsetY: Double,
  private val contentHeight: Double,
  private val layoutWidth: Double,
  private val layoutHeight: Double,
  private val insets: Insets,
) : Event<ScrollEvent>(surfaceId, viewTag) {

  data class Insets(val top: Double, val left: Double, val bottom: Double, val right: Double)

  override fun getEventName() = name

  override fun getEventData(): WritableMap =
    Arguments.createMap().apply {
      putMap(
        "contentOffset",
        Arguments.createMap().apply {
          putDouble("x", 0.0)
          putDouble("y", offsetY)
        },
      )
      putMap(
        "contentSize",
        Arguments.createMap().apply {
          putDouble("width", layoutWidth)
          putDouble("height", contentHeight)
        },
      )
      putMap(
        "layoutMeasurement",
        Arguments.createMap().apply {
          putDouble("width", layoutWidth)
          putDouble("height", layoutHeight)
        },
      )
      putMap(
        "contentInset",
        Arguments.createMap().apply {
          putDouble("top", insets.top)
          putDouble("left", insets.left)
          putDouble("bottom", insets.bottom)
          putDouble("right", insets.right)
        },
      )
      putDouble("zoomScale", 1.0)
    }

  companion object {
    const val SCROLL = "topScroll"
    const val BEGIN_DRAG = "topScrollBeginDrag"
    const val END_DRAG = "topScrollEndDrag"
    const val MOMENTUM_BEGIN = "topMomentumScrollBegin"
    const val MOMENTUM_END = "topMomentumScrollEnd"
  }
}

/** The laid-out size of the content, whenever it changes. */
class ContentSizeChangeEvent(
  surfaceId: Int,
  viewTag: Int,
  private val width: Double,
  private val height: Double,
) : Event<ContentSizeChangeEvent>(surfaceId, viewTag) {
  override fun getEventName() = NAME

  override fun getEventData(): WritableMap =
    Arguments.createMap().apply {
      putDouble("width", width)
      putDouble("height", height)
    }

  companion object {
    const val NAME = "topContentSizeChange"
  }
}

/**
 * Turns `RecyclerView` scrolling into the five `UIScrollViewDelegate` events.
 *
 * **`contentOffset.y` is accumulated from `onScrolled`'s `dy`, not read from
 * `computeVerticalScrollOffset()`.** That is the single most important line in this file. The
 * platform's offset is an *estimate*: it multiplies an average item height by the first visible
 * position, so it drifts whenever rows differ in height and — worse — it does not return exactly
 * zero at the top. `@gorhom/bottom-sheet` compares `y` against `0` to decide whether the list may
 * hand the drag back to the sheet, so an estimate that reports `3` at rest produces a sheet that
 * can never be dragged down from the top of its list. Summing `dy` is exact, because `dy` is the
 * number of pixels the list actually moved.
 *
 * `contentSize.height` stays an estimate and says so in the docs. Nothing depends on it being
 * exact, and the only way to know it truly would be to measure every row.
 *
 * Every event is gated on `tracksScroll`. An emitter is always installed and always dispatches, so
 * without the gate a list would post an event on every frame of every scroll — a real cost on the
 * JavaScript thread — for the overwhelming majority of lists that never listen.
 */
class ScrollReporter(
  private val list: RecyclerView,
  private val emit: (Event<*>) -> Unit,
  private val surfaceAndTag: () -> Pair<Int, Int>,
  private val insets: () -> ScrollEvent.Insets,
) : RecyclerView.OnScrollListener() {

  var tracksScroll: Boolean = false

  /** Exact, in pixels. See the class doc for why this is not `computeVerticalScrollOffset()`. */
  var offsetPx: Int = 0
    private set

  private var lastContentHeight = -1
  private var dragging = false
  private var momentum = false

  /** Called when something scrolls the list to a known absolute position. */
  fun resetOffset(px: Int) {
    offsetPx = px
  }

  override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
    offsetPx += dy
    // Clamped rather than trusted: a `scrollToPosition` produces an `onScrolled` whose `dy` is the
    // *visual* delta, which for a jump across 2,000 rows is not the true offset change. Pinning
    // the floor at zero keeps the one value the sheet checks honest even after such a jump.
    if (offsetPx < 0) offsetPx = 0
    if (!view.canScrollVertically(-1)) offsetPx = 0

    reportContentSizeIfChanged()
    if (tracksScroll) dispatch(ScrollEvent.SCROLL)
  }

  override fun onScrollStateChanged(view: RecyclerView, state: Int) {
    if (!tracksScroll) return
    when (state) {
      RecyclerView.SCROLL_STATE_DRAGGING -> {
        dragging = true
        dispatch(ScrollEvent.BEGIN_DRAG)
      }
      RecyclerView.SCROLL_STATE_SETTLING -> {
        if (dragging) {
          dragging = false
          dispatch(ScrollEvent.END_DRAG)
        }
        momentum = true
        dispatch(ScrollEvent.MOMENTUM_BEGIN)
      }
      RecyclerView.SCROLL_STATE_IDLE -> {
        // A drag that ends without a fling goes straight to IDLE, so `endDrag` has to be emitted
        // from here too. Reanimated subscribes to all five whenever a handler object is passed,
        // and a subscription with nothing behind it is a sheet that never learns the drag ended.
        if (dragging) {
          dragging = false
          dispatch(ScrollEvent.END_DRAG)
        }
        if (momentum) {
          momentum = false
          dispatch(ScrollEvent.MOMENTUM_END)
        }
      }
    }
  }

  fun reportContentSizeIfChanged() {
    val height = list.computeVerticalScrollRange()
    if (height == lastContentHeight) return
    lastContentHeight = height
    if (!tracksScroll) return
    val (surfaceId, tag) = surfaceAndTag()
    emit(
      ContentSizeChangeEvent(
        surfaceId,
        tag,
        PixelUtil.toDIPFromPixel(list.width.toFloat()).toDouble(),
        PixelUtil.toDIPFromPixel(height.toFloat()).toDouble(),
      )
    )
  }

  private fun dispatch(name: String) {
    val (surfaceId, tag) = surfaceAndTag()
    emit(
      ScrollEvent(
        surfaceId = surfaceId,
        viewTag = tag,
        name = name,
        offsetY = PixelUtil.toDIPFromPixel(offsetPx.toFloat()).toDouble(),
        contentHeight =
          PixelUtil.toDIPFromPixel(list.computeVerticalScrollRange().toFloat()).toDouble(),
        layoutWidth = PixelUtil.toDIPFromPixel(list.width.toFloat()).toDouble(),
        layoutHeight = PixelUtil.toDIPFromPixel(list.height.toFloat()).toDouble(),
        insets = insets(),
      )
    )
  }
}

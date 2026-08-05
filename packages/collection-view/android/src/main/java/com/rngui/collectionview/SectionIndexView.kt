package com.rngui.collectionview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The fast-scroller thumb, with a bubble showing the section it is over.
 *
 * **Different by design, and this is the clearest case of it in the package.** iOS puts an A–Z rail
 * down the trailing edge and you drag your thumb along the letters. Android has never had one: the
 * platform's own long-list affordance is a draggable thumb that appears while scrolling, with a
 * bubble beside it naming where you are. Porting the rail would produce a control no Android user
 * has seen, in the one place where a wrong idiom is most visible — the edge of the screen, under
 * the thumb.
 *
 * So `showsSectionIndex` turns *this* on, `sectionIndexShowsCallout` controls the bubble, and
 * `sectionIndexRowHeight` is a documented no-op: it exists on iOS to stop a naive implementation
 * stretching the rail across the full height, and there is no rail here to stretch.
 *
 * The letters come from each section's `indexTitle`, and a section that sets none is skipped rather
 * than given a blank stop — the same rule iOS follows, so a list can mix indexed and unindexed
 * sections on both platforms.
 */
@SuppressLint("ViewConstructor")
class SectionIndexView(context: Context, private val list: RecyclerView) : View(context) {

  private var sections: List<FlattenedTree.SectionEntry> = emptyList()
  private var style: ListStyle? = null

  /** Whether to show the letter bubble while dragging. */
  var showsCallout: Boolean = true

  private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val textPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      textSize = context.dp(20).toFloat()
    }

  private val thumbRect = RectF()
  private var dragging = false
  private var fraction = 0f

  /** Non-null only while dragging, and only when there is a letter to show. */
  private var calloutTitle: String? = null

  private val scrollListener =
    object : RecyclerView.OnScrollListener() {
      override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
        if (dragging) return
        val extent = view.computeVerticalScrollExtent()
        val range = view.computeVerticalScrollRange()
        // `computeVerticalScroll*` is an average-item-height estimate, and that is *fine here* in
        // a way it is not for `onScroll`: a thumb one pixel off is invisible, while a
        // `contentOffset.y` one pixel off is a bottom sheet that never settles. M10 keeps the
        // exact accumulator for the event; the thumb can use the cheap number.
        fraction =
          if (range <= extent) 0f
          else view.computeVerticalScrollOffset().toFloat() / (range - extent)
        invalidate()
      }
    }

  init {
    visibility = GONE
  }

  fun update(tree: FlattenedTree, style: ListStyle, enabled: Boolean) {
    // Only sections that offered a letter. An unindexed section is not a blank stop; it simply is
    // not a stop.
    sections = tree.sections.filter { !it.indexTitle.isNullOrEmpty() }
    visibility = if (enabled && sections.size > 1) VISIBLE else GONE
    restyle(style)
  }

  /**
   * Re-resolves the three paints, leaving the sections and visibility alone.
   *
   * **A resolved colour is an `Int` and an `Int` does not know what mode produced it** — the same
   * reason the rows have to be rebound. These were only ever set from [update], which a commit
   * reaches and a system mode flip does not, so the thumb and its bubble went on drawing the old
   * palette over a list that had already repainted underneath them.
   */
  fun restyle(style: ListStyle) {
    this.style = style
    thumbPaint.color = style.labelColor and 0x00FFFFFF or (0x66 shl 24)
    bubblePaint.color = style.labelColor
    textPaint.color = style.backgroundColor
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    if (sections.isEmpty()) return

    val thumbHeight = context.dp(THUMB_HEIGHT_DP).toFloat()
    val thumbWidth = context.dp(THUMB_WIDTH_DP).toFloat()
    val margin = context.dp(THUMB_MARGIN_DP).toFloat()
    val travel = height - thumbHeight - margin * 2
    val top = margin + travel * fraction.coerceIn(0f, 1f)

    thumbRect.set(width - margin - thumbWidth, top, width - margin, top + thumbHeight)
    val radius = thumbWidth / 2
    canvas.drawRoundRect(thumbRect, radius, radius, thumbPaint)

    val title = calloutTitle
    if (dragging && showsCallout && title != null) {
      val size = context.dp(BUBBLE_SIZE_DP).toFloat()
      val centreY = top + thumbHeight / 2
      val right = thumbRect.left - context.dp(8)
      val bubble = RectF(right - size, centreY - size / 2, right, centreY + size / 2)
      canvas.drawRoundRect(bubble, size / 2, size / 2, bubblePaint)
      val baseline = centreY - (textPaint.descent() + textPaint.ascent()) / 2
      canvas.drawText(title, bubble.centerX(), baseline, textPaint)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (sections.isEmpty()) return false

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        // Only a touch that starts on the thumb takes the gesture. Grabbing the whole trailing
        // strip would steal every edge swipe on the list, which is a much worse trade than
        // requiring the thumb to be hit.
        if (!thumbRect.contains(event.x, event.y)) return false
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_MOVE -> if (!dragging) return false
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        dragging = false
        calloutTitle = null
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
        return true
      }
      else -> return dragging
    }

    val thumbHeight = context.dp(THUMB_HEIGHT_DP).toFloat()
    val margin = context.dp(THUMB_MARGIN_DP).toFloat()
    val travel = height - thumbHeight - margin * 2
    fraction = ((event.y - margin - thumbHeight / 2) / travel).coerceIn(0f, 1f)

    val index = (fraction * (sections.size - 1)).toInt().coerceIn(0, sections.lastIndex)
    val section = sections[index]
    calloutTitle = section.indexTitle

    // `scrollToPositionWithOffset(position, 0)` rather than `scrollToPosition`: the latter only
    // guarantees the item is *visible*, so dragging upward lands the target section at the bottom
    // of the screen and the list appears to jump the wrong way.
    (list.layoutManager as? LinearLayoutManager)
      ?.scrollToPositionWithOffset(section.firstAdapterPosition, 0)

    invalidate()
    return true
  }

  /**
   * Subscribes on attach rather than once in `init`, because the detach is not the end.
   *
   * `react-native-screens` detaches every screen that is not on top — see the note on
   * `RNGUICollectionViewView.onAttachedToWindow`, which exists for the same reason. Removing the
   * listener on detach and adding it only in the constructor meant a tab left and returned to came
   * back with a thumb frozen wherever it was when the tab went away, tracking nothing.
   */
  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    list.addOnScrollListener(scrollListener)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    list.removeOnScrollListener(scrollListener)
  }

  private companion object {
    const val THUMB_HEIGHT_DP = 48
    const val THUMB_WIDTH_DP = 8
    const val THUMB_MARGIN_DP = 4
    const val BUBBLE_SIZE_DP = 44
  }
}

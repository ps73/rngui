package com.rngui.collectionview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.rngui.collectionview.generated.HostBackground
import com.rngui.collectionview.generated.ListAppearance
import com.rngui.collectionview.generated.RowKind

/**
 * Card insets, section spacing and separators.
 *
 * An `ItemDecoration` rather than margins and divider views, for two reasons that both come down
 * to recycling. Margins would have to be written on every bind, because a recycled holder keeps
 * the last row's — the reuse rule again, in the one place it is easy to forget because layout
 * params do not look like content. And a separator drawn as a real view would be an extra item in
 * the adapter, which would put it in the flat index that `onVisibleRangeChange` reports.
 *
 * Drawing separators in [onDraw] rather than [onDrawOver] puts them *under* the rows, so a row
 * with an opaque card covers the line at its own edge. That is what makes "no separator after the
 * last row of a group" fall out of the geometry rather than needing to be special-cased at the
 * boundary between two sections.
 */
class GroupDecoration(private var style: ListStyle) : RecyclerView.ItemDecoration() {
  private val paint = Paint().apply { isAntiAlias = false }

  fun restyle(style: ListStyle) {
    this.style = style
  }

  override fun getItemOffsets(
    outRect: Rect,
    view: View,
    parent: RecyclerView,
    state: RecyclerView.State,
  ) {
    val position = parent.getChildAdapterPosition(view)
    val item = (parent.adapter as? CollectionAdapter)?.itemAtOrNull(position) ?: return

    outRect.left = style.insetPx
    outRect.right = style.insetPx

    // In a segmented list every item is its own container, so it needs air above it — that gap is
    // what the M3 spec separates items with instead of a divider. Applied to the top of each item
    // rather than between pairs, so it composes with the section gap below rather than fighting it.
    val segmentGap =
      if (item is Item.Row && !item.positionInSection.isFirst) style.itemGapPx else 0

    // **Air at both ends of the list, and it has to come from here.** On iOS the grouped list gets
    // this for free twice over: `insetGrouped` insets its first and last sections, and the scroll
    // view's safe-area inset keeps the content clear of the tab bar. Android has neither — an
    // opaque toolbar reserves its own space and stops, and a native tab bar is a sibling view
    // rather than an overlay, so without this the first card is flush against the toolbar and the
    // last one against the tab bar. It read as a missing margin, which is exactly what it was.
    //
    // The same value as the gap *between* sections, so the list is bounded by the rhythm it already
    // has rather than by a second number that has to be kept in step. `plain` sets it to zero and
    // therefore stays flush, which is what a plain list should do.
    if (position == state.itemCount - 1) outRect.bottom = style.sectionSpacingPx

    // `sectionSpacing` is the *whole* gap between one section and the next, not a contribution to
    // it — the iOS semantics, restated here because the natural Android implementation is a margin
    // on both sides, which would double it.
    outRect.top =
      segmentGap +
      when {
        // The top of the list is its own number, because iOS's is: UIKit reserves a gap above the
        // first section that only a large title justifies, so a screen that closes it there has to
        // be able to close it here as well. Unset, this is `sectionSpacingPx` and nothing moves.
        position == 0 -> style.firstSectionSpacingPx
        item is Item.Header -> style.sectionSpacingPx
        // A section with no header still needs the gap, and its first row is where it lands.
        // Asked of the previous item rather than tracked on the section, because that keeps the
        // question local: "is there a header above me" is exactly what the gap depends on.
        item is Item.Row &&
          item.positionInSection.isFirst &&
          (parent.adapter as? CollectionAdapter)?.itemAtOrNull(position - 1) !is Item.Header ->
          style.sectionSpacingPx
        else -> 0
      }
  }

  override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    // A segmented list is separated by its gaps. Drawing dividers as well would be saying the same
    // thing twice, and the M3 spec draws one or the other rather than both.
    if (!style.drawsSeparators || style.separatorColor == 0) return
    paint.color = style.separatorColor

    val adapter = parent.adapter as? CollectionAdapter ?: return
    val inset = style.insetPx
    val height = style.separatorHeightPx.toFloat()

    for (index in 0 until parent.childCount) {
      val child = parent.getChildAt(index)
      val position = parent.getChildAdapterPosition(child)
      val item = adapter.itemAtOrNull(position) as? Item.Row ?: continue
      // Only *within* a group. The last row of a section has nothing below it that belongs to the
      // same card, and a line there would draw across the gap.
      if (item.positionInSection.isLast) continue

      // A hosted row that declined the card declines the hairlines on *both* of its edges, which
      // is why the row below is asked about too: each row draws only its own bottom line, so the
      // separator above a hosted row belongs to its predecessor. Fencing a row that paints no
      // background between two lines is not opting out of anything. iOS says the same thing in one
      // step, through `itemSeparatorHandler`'s top and bottom visibility.
      if (item.isBackgroundlessHost) continue
      if ((adapter.itemAtOrNull(position + 1) as? Item.Row)?.isBackgroundlessHost == true) continue

      val left = (inset + style.separatorInsetPx).toFloat()
      val right = (parent.width - inset).toFloat()
      val bottom = child.bottom.toFloat()
      canvas.drawRect(left, bottom - height, right, bottom, paint)
    }
  }
}

private val Item.Position.isFirst: Boolean
  get() = this == Item.Position.first || this == Item.Position.only

private val Item.Position.isLast: Boolean
  get() = this == Item.Position.last || this == Item.Position.only

/** A `host` row that did not ask for the section's card, and so draws no separators either. */
private val Item.Row.isBackgroundlessHost: Boolean
  get() = row.kind == RowKind.host && row.hostBackground != HostBackground.card

package com.rngui.collectionview

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Pins the current section's header to the top of the viewport, and pushes it off when the next
 * one arrives.
 *
 * `RecyclerView` does not do this, and iOS gets it free from compositional layout — so this is one
 * of the two places (M5's other being the scrubber) where Android has to hand-write what UIKit
 * supplies. Only `plain` pins; a grouped list's headers scroll away with their cards, which is what
 * both platforms do.
 *
 * **The header is drawn, not laid out**, which is the cheap way and the reason the next paragraph
 * exists. A decoration draws into the `RecyclerView`'s canvas; it adds no view, so nothing is
 * measured, nothing is recycled, and nothing costs a layout pass. It also means the pinned header
 * is not in the view hierarchy and therefore cannot be touched — see [PinnedHeaderTouchListener],
 * which is how M7's header action button will still work.
 *
 * The header *view* is a real one, created through the adapter and reused across frames. It is
 * measured and laid out once per header change rather than once per frame: a `Contacts` fling
 * crosses 26 sections, not 26 sections per frame.
 */
class StickyHeaderDecoration(
  private var tree: FlattenedTree,
  private var enabled: Boolean,
) : RecyclerView.ItemDecoration() {

  /** The rect the pinned header currently occupies, for hit testing. Empty when nothing is pinned. */
  val pinnedRect = Rect()

  /** Which section is pinned, or null. */
  var pinnedSectionId: String? = null
    private set

  private var headerHolder: RecyclerView.ViewHolder? = null
  private var boundPosition = RecyclerView.NO_POSITION

  /**
   * The colour the pinned header is drawing its title in right now, or null if none is pinned.
   *
   * **The only way to ask.** The header is drawn into the `RecyclerView`'s canvas rather than added
   * to the hierarchy, so no view traversal finds it and no screenshot distinguishes its text from
   * the rows scrolling underneath — the `plain` header is transparent by design. Two tests need the
   * answer: `HeaderRestyleTest` in this module, and `RestyleReachTest` in the example app, which is
   * a separate Gradle module and so cannot see `internal`.
   *
   * Public and read-only rather than exposing [headerHolder], which would hand a test a mutable
   * view it has no business touching.
   */
  @androidx.annotation.VisibleForTesting
  fun pinnedHeaderTextColor(): Int? =
    (headerHolder?.itemView as? android.widget.TextView)?.currentTextColor

  fun update(tree: FlattenedTree, enabled: Boolean) {
    this.tree = tree
    this.enabled = enabled
    // The cached header belongs to the tree that produced it. Keeping it across a tree change is
    // how a list ends up pinning a section that no longer exists.
    boundPosition = RecyclerView.NO_POSITION
    pinnedRect.setEmpty()
    pinnedSectionId = null
  }

  /**
   * Throws the cached header away so the next draw builds a new one against the current mode.
   *
   * **Without this the pinned header keeps the palette it was created with.** [headerView]
   * short-circuits on `position == boundPosition`, which is true on every frame after the first —
   * so a system dark-mode flip repainted every row and left the header pinned above them still
   * drawing the light one. It was only ever reached through `update`, and `update` is only reached
   * from a commit; the flip arrives on `onConfigurationChanged` and `onAttachedToWindow`, which
   * commit nothing.
   *
   * The holder is dropped rather than just rebound, for the same reason `CollectionAdapter.retheme`
   * rebuilds instead of rebinding: the view was inflated against a themed context that carries the
   * old mode, and no amount of rebinding changes what it was constructed with. One view, once per
   * flip.
   */
  fun restyle() {
    headerHolder = null
    boundPosition = RecyclerView.NO_POSITION
  }

  override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    pinnedRect.setEmpty()
    pinnedSectionId = null
    if (!enabled || parent.childCount == 0) return

    val topChild = parent.getChildAt(0) ?: return
    val topPosition = parent.getChildAdapterPosition(topChild)
    if (topPosition == RecyclerView.NO_POSITION) return

    val headerPosition = tree.headerPositionAbove(topPosition)
    if (headerPosition < 0) return

    val adapter = parent.adapter as? CollectionAdapter ?: return
    val header = headerView(parent, adapter, headerPosition) ?: return

    // Pinned below the content inset, not at the RecyclerView's edge. `clipToPadding = false` lets
    // rows scroll *through* the top inset, which is the whole point of it — but a header pinned at
    // y=0 would pin inside the status bar, under the navigation bar, where the rows are
    // deliberately allowed to pass. iOS pins below the safe area for the same reason.
    val top = parent.paddingTop

    // Push-off: once the *next* section's header reaches the pinned one's bottom edge, the pinned
    // header slides up by exactly the overlap rather than cross-fading or jumping. Reading the
    // offset off the arriving header's own top is what keeps the two moving as one object — a
    // computed animation would drift from the scroll by a frame.
    val offset = top + pushOffOffset(parent, adapter, top + header.height, headerPosition)

    canvas.save()
    canvas.translate(0f, offset.toFloat())
    header.draw(canvas)
    canvas.restore()

    pinnedRect.set(0, offset, header.width, offset + header.height)
    pinnedSectionId = (tree.items.getOrNull(headerPosition) as? Item.Header)?.sectionId
  }

  /**
   * How far up the pinned header has been pushed, as a non-positive number.
   *
   * Only the header *immediately* below counts. Scanning every visible child for "the next header"
   * would also find the one two sections down on a short-section list, and the pinned header would
   * start sliding off long before its replacement arrived.
   */
  private fun pushOffOffset(
    parent: RecyclerView,
    adapter: CollectionAdapter,
    /** The y the pinned header's *bottom* edge sits at, inset included. */
    contactPoint: Int,
    pinnedPosition: Int,
  ): Int {
    for (index in 0 until parent.childCount) {
      val child = parent.getChildAt(index)
      val position = parent.getChildAdapterPosition(child)
      if (position == RecyclerView.NO_POSITION || position <= pinnedPosition) continue
      if (adapter.itemAtOrNull(position) !is Item.Header) continue
      // The first header below the pinned one, which is the only one that can push it.
      return if (child.top < contactPoint) child.top - contactPoint else 0
    }
    return 0
  }

  /** Creates, binds, measures and lays out the header for [position], reusing it across frames. */
  private fun headerView(
    parent: RecyclerView,
    adapter: CollectionAdapter,
    position: Int,
  ): View? {
    if (position != boundPosition || headerHolder == null) {
      val holder =
        headerHolder
          ?: adapter.createViewHolder(parent, adapter.getItemViewType(position)).also {
            headerHolder = it
          }
      adapter.bindViewHolder(holder, position)

      val view = holder.itemView
      // A decoration draws outside the layout pass, so nothing else will ever measure this view.
      val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
      val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.AT_MOST)
      view.measure(widthSpec, heightSpec)
      view.layout(0, 0, view.measuredWidth, view.measuredHeight)
      boundPosition = position
    }
    return headerHolder?.itemView
  }
}

/**
 * Makes the pinned header touchable again.
 *
 * A drawn header is pixels, not a view, so it receives nothing — a tap lands on whichever row
 * happens to be scrolled underneath it, which is worse than the header being inert. This
 * intercepts touches inside the pinned rect before the `RecyclerView` routes them to a child.
 *
 * M7's section `action` button is what this exists for; today it reports the section and swallows
 * the touch, which is already the difference between "tapping a pinned header does nothing" and
 * "tapping a pinned header activates the row hiding behind it".
 */
class PinnedHeaderTouchListener(
  private val decoration: StickyHeaderDecoration,
  private val onHeaderTap: (sectionId: String) -> Unit,
) : RecyclerView.OnItemTouchListener {
  private var claimed = false

  override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      claimed =
        decoration.pinnedSectionId != null &&
          decoration.pinnedRect.contains(event.x.toInt(), event.y.toInt())
    }
    return claimed
  }

  override fun onTouchEvent(view: RecyclerView, event: MotionEvent) {
    if (!claimed) return
    if (event.actionMasked == MotionEvent.ACTION_UP) {
      decoration.pinnedSectionId
        ?.takeIf { decoration.pinnedRect.contains(event.x.toInt(), event.y.toInt()) }
        ?.let(onHeaderTap)
    }
    if (event.actionMasked == MotionEvent.ACTION_UP ||
      event.actionMasked == MotionEvent.ACTION_CANCEL
    ) {
      claimed = false
    }
  }

  override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit
}

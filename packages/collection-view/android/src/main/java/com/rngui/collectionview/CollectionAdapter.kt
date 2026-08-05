package com.rngui.collectionview

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rngui.collectionview.generated.RowKind

/**
 * The adapter, and the Android analogue of `UICollectionViewDiffableDataSource`.
 *
 * `ListAdapter` rather than a plain adapter with `notifyDataSetChanged`, because it brings the two
 * things the diffable data source brings: identity-based diffing, so a row that moved animates
 * instead of being torn down, and *background* diffing, so a 2,000-row tree update does not walk
 * the list on the UI thread.
 *
 * The comparison is split exactly as `DiffUtil` intends and as the generated model makes cheap:
 * [Item.id] answers "is this the same row", and `data class` equality answers "did anything about
 * it change". Nobody writes a field-by-field comparison, and nobody forgets to update one when
 * `tree.ts` grows a field — which is the whole reason the generator emits data classes.
 */
class CollectionAdapter(
  /**
   * The context every item view is created against — Material-themed, and carrying the mode.
   *
   * **Not `parent.context`.** A `MaterialSwitch` reads `switchTextAppearance`, `colorPrimary` and a
   * dozen shape attributes at construction time, and inflating one against React Native's plain
   * `ThemedReactContext` crashes inside `SwitchCompat.onMeasure` — a `StaticLayout` built from a
   * text appearance that is not there. Material widgets are not optional about their theme.
   *
   * It is a `var` because the mode is baked into it: a theme flip needs new widgets, not just new
   * colours, so [retheme] swaps this and rebuilds every holder.
   */
  private var themedContext: android.content.Context,
  private var style: RowStyle,
  private var listStyle: ListStyle,
  private val events: RowEvents,
  /** Where hosted React children live while no holder is showing them. */
  private val parking: ParkingView,
  /** The mounted React children, in mount order — which is what `hostIndex` indexes into. */
  private val hostChildAt: (Int) -> View?,
  /**
   * How far the row at a position is displaced by an open swipe tray.
   *
   * Applied on bind because `ItemAnimator` clears `translationX` on any layout pass that rebinds a
   * row, so the open tray's row would spring shut on the next unrelated commit.
   */
  private val swipeTranslation: (Int) -> Float = { 0f },
) : ListAdapter<Item, RecyclerView.ViewHolder>(DIFF) {

  /**
   * Restyles without re-diffing.
   *
   * A theme flip changes how every row draws but nothing about *which* rows there are, so pushing
   * a new list through `submitList` would be asking `DiffUtil` to prove that 2,000 unchanged items
   * are unchanged. `notifyItemRangeChanged` rebinds them and says so.
   */
  fun restyle(style: RowStyle, listStyle: ListStyle) {
    this.style = style
    this.listStyle = listStyle
    notifyItemRangeChanged(0, itemCount)
  }

  /**
   * Swaps the inflation context and rebuilds every holder.
   *
   * Rebinding is enough for anything this library draws itself, but not for a Material widget: its
   * thumb, track and check colours come from the theme it was *constructed* with, so a switch built
   * in light mode stays light however many times it is rebound. Recreating them is the only honest
   * answer, and a theme flip is rare enough to afford it.
   */
  fun retheme(context: android.content.Context, style: RowStyle, listStyle: ListStyle) {
    themedContext = context
    this.style = style
    this.listStyle = listStyle
    // The pool holds views built against the old theme, and they would come straight back.
    chipPool.clear()
    notifyDataSetChanged()
  }

  /** For the decoration, which reads items by adapter position while drawing. */
  fun itemAtOrNull(position: Int): Item? =
    if (position in 0 until itemCount) getItem(position) else null

  /**
   * One view type per row *kind*, so each gets its own pool.
   *
   * The direct analogue of what `DatePickerStyle` forces on iOS — a cell configured as a compact
   * pill cannot be reconfigured into a calendar — generalised to every kind. A holder built for a
   * `switch` never has to become a `textField`, which is what lets `RowView` create its control
   * once in the constructor instead of tearing one down and building another on every bind.
   */
  override fun getItemViewType(position: Int): Int =
    when (val item = getItem(position)) {
      is Item.Header -> TYPE_HEADER
      is Item.Footer -> TYPE_FOOTER
      is Item.ChipStrip -> TYPE_CHIPS
      is Item.Row -> TYPE_ROW_BASE + item.row.kind.ordinal
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
    when (viewType) {
      TYPE_HEADER -> LabelHolder(supplementaryView(themedContext, isHeader = true))
      TYPE_FOOTER -> LabelHolder(supplementaryView(themedContext, isHeader = false))
      TYPE_CHIPS -> ChipHolder(ChipStripView(themedContext, chipPool))
      TYPE_ROW_BASE + RowKind.host.ordinal -> HostHolder(HostContainer(themedContext))
      else ->
        RowHolder(
          RowView(
            themedContext,
            RowKind.entries.getOrElse(viewType - TYPE_ROW_BASE) { RowKind.default },
            events,
          )
        )
    }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val item = getItem(position)) {
      is Item.ChipStrip ->
        (holder as ChipHolder).strip.bind(item.rows, style, events::onRowPress)
      is Item.Header -> (holder as LabelHolder).bind(item.title, style.headerTextColor)
      is Item.Footer -> (holder as LabelHolder).bind(item.text, style.footerTextColor)
      is Item.Row -> {
        if (holder is HostHolder) {
          bindHost(holder, item)
          return
        }
        val view = (holder as RowHolder).view
        // The container goes with the row rather than being assigned here, because its shape has to
        // *move* between states rather than jump — which needs a drawable that outlives the bind.
        // `positionInSection` still has to be passed on every one: a recycled holder arriving in
        // the middle of a section would otherwise keep the previous occupant's corners.
        view.bind(item.row, style, listStyle, item.positionInSection)
        // Set unconditionally, including to null: a recycled holder keeps the listener the last
        // row installed, and a non-selectable row inheriting one is a row that reports a press
        // nobody can see it accepting.
        // A `menu` row installs its own click listener (it opens the popup), so this must not
        // overwrite it. Every other kind gets the press listener set unconditionally, including to
        // null: a recycled holder keeps whatever the last row installed, and a non-selectable row
        // inheriting one reports a press nobody can see it accepting.
        view.translationX = swipeTranslation(position)

        val pressable = item.row.selectable == true && item.row.disabled != true
        if (item.row.kind != RowKind.menu) {
          view.setOnClickListener(if (pressable) ({ events.onRowPress(item.row.id) }) else null)
          view.isClickable = pressable
        }
      }
    }
  }

  /**
   * Claims the hosted child for this row, and reserves the height JavaScript stated.
   *
   * `height` is never measured here. Fabric lays the subtree out with Yoga, so the view has no
   * intrinsic size and an "estimated" cell would measure it as zero — either the caller stated a
   * height or `Root` read it off the mounted subtree with `onLayout` and sent it back through the
   * tree.
   */
  private fun bindHost(holder: HostHolder, item: Item.Row) {
    val container = holder.container
    container.layoutParams =
      (container.layoutParams ?: RecyclerView.LayoutParams(MATCH, WRAP)).apply {
        width = MATCH
        height =
          item.row.height?.let { container.context.dp(it) } ?: WRAP
      }

    val child = item.row.hostIndex?.let(hostChildAt)
    if (child == null) {
      container.release(parking)
      return
    }
    container.claim(child, parking)
  }

  override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
    super.onViewRecycled(holder)
    // The ownership guard lives inside `release`; see HostContainer for why it has to.
    (holder as? HostHolder)?.container?.release(parking)
  }

  class RowHolder(val view: RowView) : RecyclerView.ViewHolder(view)

  class HostHolder(val container: HostContainer) : RecyclerView.ViewHolder(container)

  class ChipHolder(val strip: ChipStripView) : RecyclerView.ViewHolder(strip)

  /**
   * Shared by every chip strip in the list.
   *
   * Ten chip sections then share one set of pooled pills rather than each keeping its own, which
   * is the entire reason to nest a `RecyclerView` rather than lay out a `HorizontalScrollView`.
   */
  private val chipPool = RecyclerView.RecycledViewPool()

  /** The swipe actions for a position, as `(leading, trailing)`. */
  fun swipeActionsAt(position: Int): Pair<List<com.rngui.collectionview.generated.SwipeActionSpec>, List<com.rngui.collectionview.generated.SwipeActionSpec>> {
    val row = (itemAtOrNull(position) as? Item.Row)?.row ?: return emptyList<com.rngui.collectionview.generated.SwipeActionSpec>() to emptyList()
    return row.leadingActions.orEmpty() to row.trailingActions.orEmpty()
  }

  fun rowIdAt(position: Int): String? = (itemAtOrNull(position) as? Item.Row)?.row?.id

  /**
   * Where a row id sits now, or `NO_POSITION` if it is no longer in the list.
   *
   * The inverse of [rowIdAt], and a linear scan on purpose: the only caller is
   * `SwipeActionsCallback.rebase`, which runs when the list changes *and* a tray is open — rare
   * enough that an index kept up to date on every commit would cost more than it saved. `FlattenedTree`
   * already guarantees ids are unique, so the first match is the only match.
   */
  fun positionOfRow(rowId: String): Int {
    for (position in 0 until itemCount) {
      val item = itemAtOrNull(position)
      if (item is Item.Row && item.row.id == rowId) return position
    }
    return RecyclerView.NO_POSITION
  }

  fun currentStyle(): RowStyle = style

  class LabelHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
    fun bind(value: String?, color: Int) {
      text.text = value.orEmpty()
      text.setTextColor(color)
    }
  }

  private companion object {
    const val TYPE_HEADER = 1
    const val TYPE_FOOTER = 2
    const val TYPE_CHIPS = 3

    /** Row view types are `TYPE_ROW_BASE + RowKind.ordinal`, so they never collide with these. */
    const val TYPE_ROW_BASE = 100

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /**
     * A section header or footer.
     *
     * Both are a single line of small caps-ish text against the list background rather than
     * against a card, which is what puts the visual break between two groups. The vertical
     * padding is deliberately asymmetric — a header hugs the card below it and a footer the card
     * above — because that is what makes a footer read as belonging to the group it explains
     * rather than to the one that follows.
     */
    fun supplementaryView(context: Context, isHeader: Boolean): TextView =
      TextView(context).apply {
        textSize = 13f
        gravity = Gravity.BOTTOM
        setPadding(
          context.dp(GroupShape.INSET_DP + 4),
          context.dp(if (isHeader) 0 else 6),
          context.dp(GroupShape.INSET_DP + 4),
          context.dp(if (isHeader) 6 else 0),
        )
        layoutParams =
          LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
      }

    val DIFF =
      object : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(old: Item, new: Item) = old.id == new.id

        override fun areContentsTheSame(old: Item, new: Item) = old == new
      }
  }
}

/** A row view is always the item view of a [CollectionAdapter.RowHolder]. */
val RecyclerView.ViewHolder.rowViewOrNull: View?
  get() = (this as? CollectionAdapter.RowHolder)?.view

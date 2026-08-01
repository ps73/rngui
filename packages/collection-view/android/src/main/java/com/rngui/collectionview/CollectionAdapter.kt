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
  private var style: RowStyle,
  private val onRowPress: (rowId: String) -> Unit,
) : ListAdapter<Item, RecyclerView.ViewHolder>(DIFF) {

  /**
   * Restyles without re-diffing.
   *
   * A theme flip changes how every row draws but nothing about *which* rows there are, so pushing
   * a new list through `submitList` would be asking `DiffUtil` to prove that 2,000 unchanged items
   * are unchanged. `notifyItemRangeChanged` rebinds them and says so.
   */
  fun restyle(style: RowStyle) {
    this.style = style
    notifyItemRangeChanged(0, itemCount)
  }

  override fun getItemViewType(position: Int): Int =
    when (getItem(position)) {
      is Item.Header -> TYPE_HEADER
      is Item.Footer -> TYPE_FOOTER
      is Item.Row -> TYPE_ROW
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
    when (viewType) {
      TYPE_HEADER -> LabelHolder(supplementaryView(parent.context, isHeader = true))
      TYPE_FOOTER -> LabelHolder(supplementaryView(parent.context, isHeader = false))
      else -> RowHolder(RowView(parent.context))
    }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val item = getItem(position)) {
      is Item.Header -> (holder as LabelHolder).bind(item.title, style.headerTextColor)
      is Item.Footer -> (holder as LabelHolder).bind(item.text, style.footerTextColor)
      is Item.Row -> {
        val view = (holder as RowHolder).view
        view.bind(item.row, style)
        style.rowBackground?.let(view::setBackgroundColor)
        // Set unconditionally, including to null: a recycled holder keeps the listener the last
        // row installed, and a non-selectable row inheriting one is a row that reports a press
        // nobody can see it accepting.
        view.setOnClickListener(
          if (item.row.selectable == true && item.row.disabled != true) {
            { onRowPress(item.row.id) }
          } else {
            null
          }
        )
        view.isClickable = item.row.selectable == true && item.row.disabled != true
      }
    }
  }

  class RowHolder(val view: RowView) : RecyclerView.ViewHolder(view)

  class LabelHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
    fun bind(value: String?, color: Int) {
      text.text = value.orEmpty()
      text.setTextColor(color)
    }
  }

  private companion object {
    const val TYPE_HEADER = 1
    const val TYPE_FOOTER = 2
    const val TYPE_ROW = 3

    /**
     * Headers and footers, unstyled beyond their colour.
     *
     * M4 owns their typography, their spacing against the group above and the grouped-card shape
     * around the rows between them. This is the minimum that puts them on screen in the right
     * order, which is what M3 is for.
     */
    fun supplementaryView(context: Context, isHeader: Boolean): TextView =
      TextView(context).apply {
        textSize = if (isHeader) 13f else 13f
        gravity = Gravity.BOTTOM
        setPadding(
          context.dp(16),
          context.dp(if (isHeader) 24 else 6),
          context.dp(16),
          context.dp(if (isHeader) 6 else 16),
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

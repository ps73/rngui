package com.rngui.collectionview

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rngui.collectionview.generated.RowSpec

/**
 * A horizontally scrolling strip of pills, inside a vertically scrolling list.
 *
 * **The Play Store shelf pattern, and the one place where the iOS design and the Android idiom
 * genuinely coincide.** Compositional layout's orthogonal scrolling section is the thing a
 * `UITableView` simply cannot do; Android has no equivalent *layout*, but it has the pattern —
 * a nested `RecyclerView` — and Material chips are a real component, unlike Material "orthogonal
 * scrolling sections", which are not a thing at all.
 *
 * The pool is shared across every strip in the list. Ten chip sections then share one set of
 * pooled pills rather than each keeping its own, which is the entire reason to nest a
 * `RecyclerView` rather than to lay out a `HorizontalScrollView` full of views.
 */
class ChipStripView(context: Context, sharedPool: RecyclerView.RecycledViewPool) :
  RecyclerView(context) {

  private val chipAdapter = ChipAdapter()

  init {
    layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
    adapter = chipAdapter
    setRecycledViewPool(sharedPool)
    clipToPadding = false
    setPadding(context.dp(GroupShape.INSET_DP), context.dp(4), context.dp(GroupShape.INSET_DP), context.dp(4))
    // The outer list must not steal a horizontal drag, and the inner one must not steal a vertical
    // one. RecyclerView already resolves that by axis — this only stops the *parent* from
    // intercepting once the inner list has decided the gesture is its own.
    isNestedScrollingEnabled = false
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  }

  fun bind(rows: List<RowSpec>, style: RowStyle, onPress: (String) -> Unit) {
    chipAdapter.submit(rows, style, onPress)
  }

  private class ChipAdapter : RecyclerView.Adapter<ChipHolder>() {
    private var rows: List<RowSpec> = emptyList()
    private var style: RowStyle? = null
    private var onPress: (String) -> Unit = {}

    fun submit(rows: List<RowSpec>, style: RowStyle, onPress: (String) -> Unit) {
      this.rows = rows
      this.style = style
      this.onPress = onPress
      notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
      ChipHolder(
        TextView(parent.context).apply {
          gravity = Gravity.CENTER
          minHeight = parent.context.dp(32)
          setPadding(
            parent.context.dp(14),
            parent.context.dp(6),
            parent.context.dp(14),
            parent.context.dp(6),
          )
          layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
              marginEnd = parent.context.dp(8)
            }
        }
      )

    override fun onBindViewHolder(holder: ChipHolder, position: Int) {
      val row = rows[position]
      val resolved = style ?: return
      val selected = row.on == true
      val tint = parseRnguiHex(row.tintColor) ?: resolved.tintColor

      holder.text.text = row.label.orEmpty()
      holder.text.setTextColor(
        when {
          row.disabled == true -> resolved.disabledColor
          selected -> Color.WHITE
          else -> resolved.labelColor
        }
      )
      FontResolver.apply(holder.text, row.font ?: resolved.font, CHIP_SIZE_SP, holder.text.context)

      // A stadium, and a filled one when selected — the M3 chip's own two states.
      val radius = holder.text.context.dp(16).toFloat()
      val fill =
        GradientDrawable().apply {
          cornerRadius = radius
          setColor(if (selected) tint else Color.TRANSPARENT)
          if (!selected) setStroke(holder.text.context.dp(1), resolved.separatorColor)
        }
      val mask = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Color.WHITE)
      }
      holder.text.background =
        RippleDrawable(
          ColorStateList.valueOf(resolved.labelColor and 0x00FFFFFF or (0x1F shl 24)),
          fill,
          mask,
        )

      holder.text.alpha = if (row.disabled == true) 0.4f else 1f
      // Set unconditionally, including to null. The reuse rule reaches the inner adapter too.
      holder.text.setOnClickListener(
        if (row.disabled == true) null else ({ onPress(row.id) })
      )
    }

    private companion object {
      const val CHIP_SIZE_SP = 14f
    }
  }

  private class ChipHolder(val text: TextView) : RecyclerView.ViewHolder(text)
}

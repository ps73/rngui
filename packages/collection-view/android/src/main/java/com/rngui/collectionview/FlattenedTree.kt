package com.rngui.collectionview

import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree

/**
 * One entry in the adapter's list: a header, a row, or a footer, in tree order.
 *
 * A single flat list rather than a section/row hierarchy, because that is the shape
 * `RecyclerView` wants and — more usefully — the shape the rest of the contract is already in.
 * `serialize` emits sections in this order, `onVisibleRangeChange` indexes against it, and
 * `hostIndex` is an index into the same ordering.
 *
 * `data class` throughout so `DiffUtil.areContentsTheSame` is structural equality and nobody has
 * to write it. That is the whole reason the generated model is data classes too.
 */
sealed class Item {
  /** Stable across updates, and unique across the whole list. What `DiffUtil` diffs on. */
  abstract val id: String

  data class Header(
    val sectionId: String,
    val title: String?,
    val section: SectionSpec,
    /** Which section this is, so M4 can space it against the one before. */
    val sectionIndex: Int,
  ) : Item() {
    override val id get() = "h:$sectionId"
  }

  data class Footer(
    val sectionId: String,
    val text: String?,
    val sectionIndex: Int,
  ) : Item() {
    override val id get() = "f:$sectionId"
  }

  data class Row(
    val row: RowSpec,
    val sectionId: String,
    val sectionIndex: Int,
    /**
     * Where this row sits inside its section, which is what M4's corner shapes need. Computed
     * here rather than in the adapter because the adapter sees a flat list and would have to walk
     * backwards to a header to work it out — and a `plain` section has no header to find.
     */
    val positionInSection: Position,
    /**
     * Index into the *rows only*, ignoring headers and footers.
     *
     * The number `onVisibleRangeChange` reports and `hostIndex` addresses. It is emphatically not
     * the adapter position: a list with headers has more adapter positions than rows, and sending
     * one where the other is expected windows the wrong rows — silently, and only on lists that
     * happen to have headers.
     */
    val rowIndex: Int,
  ) : Item() {
    override val id get() = "r:${row.id}"
  }

  /** Where a row sits in its section. A section of one row is [only], not [first]. */
  enum class Position {
    only,
    first,
    middle,
    last,
  }
}

/**
 * The flattened list, plus what the view needs to address it.
 *
 * @property rowCount how many [Item.Row]s there are, so an empty list can report `-1, -1`.
 * @property adapterPositionByRowIndex maps a flat row index back to an adapter position, for
 *   scrolling to a row and for M8's host claiming.
 */
data class FlattenedTree(
  val items: List<Item> = emptyList(),
  val rowCount: Int = 0,
  val adapterPositionByRowIndex: IntArray = IntArray(0),
) {
  /** The flat row index at an adapter position, or -1 if that position is not a row. */
  fun rowIndexAt(adapterPosition: Int): Int =
    (items.getOrNull(adapterPosition) as? Item.Row)?.rowIndex ?: -1

  // IntArray is an array, so the generated data-class equality would compare identity. Nothing
  // compares two FlattenedTrees today; these exist so that if something starts to, it is right.
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is FlattenedTree &&
        items == other.items &&
        rowCount == other.rowCount &&
        adapterPositionByRowIndex.contentEquals(other.adapterPositionByRowIndex))

  override fun hashCode(): Int =
    (items.hashCode() * 31 + rowCount) * 31 + adapterPositionByRowIndex.contentHashCode()

  companion object {
    /**
     * Flattens a decoded tree, dropping duplicate ids.
     *
     * **Duplicates are dropped rather than tolerated**, which is a genuine difference from what
     * `RecyclerView` would do on its own. `ListAdapter` does not care about repeated ids the way
     * `UICollectionViewDiffableDataSource` does — `appendItems` raises
     * `NSInternalInconsistencyException` on iOS and nothing raises anything here — but letting
     * them through would mean `DiffUtil` sees two items claiming the same identity and animates
     * them into each other, and it would mean the two platforms disagree about what a malformed
     * tree renders as. The serializer already reports the collision under `__DEV__`, where the
     * offending call site is visible; this is the same last line of defence iOS has.
     */
    fun of(tree: Tree): FlattenedTree {
      val items = ArrayList<Item>(tree.sections.sumOf { it.rows.size + 2 })
      val adapterPositions = ArrayList<Int>()
      val seenSections = HashSet<String>()
      val seenRows = HashSet<String>()
      var sectionIndex = 0

      for (section in tree.sections) {
        if (!seenSections.add(section.id)) continue

        val rows = section.rows.filter { seenRows.add(it.id) }
        // A section with no surviving rows contributes nothing, not even its header. A header
        // pinned over an empty stretch is how a `plain` list ends up with two headers touching.
        if (rows.isEmpty()) continue

        if (section.header != null) {
          items += Item.Header(section.id, section.header, section, sectionIndex)
        }

        rows.forEachIndexed { index, row ->
          adapterPositions += items.size
          items +=
            Item.Row(
              row = row,
              sectionId = section.id,
              sectionIndex = sectionIndex,
              positionInSection =
                when {
                  rows.size == 1 -> Item.Position.only
                  index == 0 -> Item.Position.first
                  index == rows.lastIndex -> Item.Position.last
                  else -> Item.Position.middle
                },
              rowIndex = adapterPositions.size - 1,
            )
        }

        if (section.footer != null) {
          items += Item.Footer(section.id, section.footer, sectionIndex)
        }
        sectionIndex++
      }

      return FlattenedTree(
        items = items,
        rowCount = adapterPositions.size,
        adapterPositionByRowIndex = adapterPositions.toIntArray(),
      )
    }
  }
}

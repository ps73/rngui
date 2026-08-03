package com.rngui.collectionview

import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionLayout
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

  /**
   * A whole `chips` section, as one item.
   *
   * The horizontally scrolling strip is the one thing compositional layout buys on iOS that has a
   * genuinely idiomatic Android answer — the Play Store shelf: a nested `RecyclerView` inside a
   * vertical one, sharing a pool. Modelled as a single item because that is what it *is* to the
   * outer list; the chips inside it are the inner adapter's business.
   *
   * It still consumes every one of its rows' flat indices, because `serialize` concatenates all
   * rows regardless of layout and `onVisibleRangeChange` indexes against that.
   */
  data class ChipStrip(
    val sectionId: String,
    val sectionIndex: Int,
    val rows: List<RowSpec>,
    val firstRowIndex: Int,
    val lastRowIndex: Int,
  ) : Item() {
    override val id get() = "c:$sectionId"
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
 * Whether M3 should draw this row as a *selected* list item.
 *
 * True for a checked `checkbox` or `radio` accessory, which is what the
 * [M3 list guidance](https://m3.material.io/components/lists/guidelines) means by a selected item —
 * a `checkmark` is a static tick rather than a selection control, and a `switch` toggles a setting
 * rather than selecting the row it sits on. Neither should recolour its container.
 */
val RowSpec.isSelected: Boolean
  get() =
    on == true &&
      (accessory == com.rngui.collectionview.generated.AccessoryKind.checkbox ||
        accessory == com.rngui.collectionview.generated.AccessoryKind.radio)

/**
 * The flattened list, plus what the view needs to address it.
 *
 * @property rowCount how many [Item.Row]s there are, so an empty list can report `-1, -1`.
 * @property adapterPositionByRowIndex maps a flat row index back to an adapter position, for
 *   scrolling to a row and for M8's host claiming.
 * @property headerPositionBySection the adapter position of each section's header, or -1 where the
 *   section has none. M5's sticky header needs "which header is above this row" on every frame,
 *   and walking backwards through the list to find one is O(section length) — fine for a Settings
 *   group, ruinous for a 2,000-row Contacts section.
 * @property sections one entry per surviving section, in order, for the section index.
 */
data class FlattenedTree(
  val items: List<Item> = emptyList(),
  val rowCount: Int = 0,
  val adapterPositionByRowIndex: IntArray = IntArray(0),
  val headerPositionBySection: IntArray = IntArray(0),
  val sections: List<SectionEntry> = emptyList(),
) {
  /**
   * A section, as the index scrubber and the sticky header see it.
   *
   * @property indexTitle the letter this section contributes to the scrubber. A section that sets
   *   none is skipped rather than given a blank stop, so a list can mix indexed and unindexed
   *   sections — the same rule iOS follows.
   */
  data class SectionEntry(
    val id: String,
    val indexTitle: String?,
    val firstAdapterPosition: Int,
  )

  /** The adapter position of the header above [adapterPosition], or -1 if there is none. */
  fun headerPositionAbove(adapterPosition: Int): Int {
    val item = items.getOrNull(adapterPosition) ?: return -1
    val sectionIndex =
      when (item) {
        is Item.Header -> item.sectionIndex
        is Item.Footer -> item.sectionIndex
        is Item.Row -> item.sectionIndex
        is Item.ChipStrip -> item.sectionIndex
      }
    return headerPositionBySection.getOrElse(sectionIndex) { -1 }
  }

  /** The flat row index at an adapter position, or -1 if that position holds no rows. */
  fun rowIndexAt(adapterPosition: Int): Int =
    when (val item = items.getOrNull(adapterPosition)) {
      is Item.Row -> item.rowIndex
      // A strip covers a span of row indices; which end matters depends on which edge of the
      // viewport is being asked about.
      is Item.ChipStrip -> item.firstRowIndex
      else -> -1
    }

  /** As [rowIndexAt], but the *last* row a chip strip covers. */
  fun lastRowIndexAt(adapterPosition: Int): Int =
    when (val item = items.getOrNull(adapterPosition)) {
      is Item.Row -> item.rowIndex
      is Item.ChipStrip -> item.lastRowIndex
      else -> -1
    }

  // IntArray is an array, so the generated data-class equality would compare identity. Nothing
  // compares two FlattenedTrees today; these exist so that if something starts to, it is right.
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is FlattenedTree &&
        items == other.items &&
        rowCount == other.rowCount &&
        adapterPositionByRowIndex.contentEquals(other.adapterPositionByRowIndex) &&
        headerPositionBySection.contentEquals(other.headerPositionBySection) &&
        sections == other.sections)

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
      val headerPositions = ArrayList<Int>()
      val sections = ArrayList<SectionEntry>()
      val seenSections = HashSet<String>()
      val seenRows = HashSet<String>()
      var sectionIndex = 0

      for (section in tree.sections) {
        if (!seenSections.add(section.id)) continue

        val rows = section.rows.filter { seenRows.add(it.id) }
        // A section with no surviving rows contributes nothing, not even its header. A header
        // pinned over an empty stretch is how a `plain` list ends up with two headers touching.
        if (rows.isEmpty()) continue

        headerPositions +=
          if (section.header != null) {
            items += Item.Header(section.id, section.header, section, sectionIndex)
            items.lastIndex
          } else {
            -1
          }
        sections += SectionEntry(section.id, section.indexTitle, items.size)

        // A `chips` section collapses to one item. Its rows still take their flat indices, so
        // nothing downstream has to know the difference.
        if (section.layout == SectionLayout.chips) {
          val first = adapterPositions.size
          rows.forEach { adapterPositions += items.size }
          items +=
            Item.ChipStrip(
              sectionId = section.id,
              sectionIndex = sectionIndex,
              rows = rows,
              firstRowIndex = first,
              lastRowIndex = adapterPositions.size - 1,
            )
          if (section.footer != null) {
            items += Item.Footer(section.id, section.footer, sectionIndex)
          }
          sectionIndex++
          continue
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
        headerPositionBySection = headerPositions.toIntArray(),
        sections = sections,
      )
    }
  }
}

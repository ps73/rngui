package com.rngui.collectionview

import com.rngui.collectionview.generated.RowSpec
import com.rngui.collectionview.generated.SectionLayout
import com.rngui.collectionview.generated.SectionSpec
import com.rngui.collectionview.generated.Tree
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The index arithmetic every other Android file trusts.
 *
 * `FlattenedTree` is the one piece of this backend that is pure logic — no `Context`, no view, no
 * `Looper` — and it is also the piece that everything else addresses through: `onVisibleRangeChange`
 * reports its row indices, `hostIndex` addresses its ordering, the sticky header asks it which
 * header is above a position, the scrubber scrolls to its `firstAdapterPosition`, and
 * `CollectionAdapter.positionOfRow` — which decides *which contact gets deleted* — depends on its
 * uniqueness guarantee. It ran without a single test until a review pointed that out.
 *
 * These are local JVM tests rather than instrumented ones because none of this needs a device, and
 * the JVM source set is already wired for the generated-model contract test next door. Run them
 * with `npm run test:kotlin`.
 */
class FlattenedTreeTest {

  // -- the uniqueness guarantee -----------------------------------------------------------------

  /**
   * The invariant `CollectionAdapter.positionOfRow` returns the *first* match on.
   *
   * If two rows could share an id, resolving a swipe tray by identity would be as ambiguous as
   * resolving it by position was — the bug that fix exists to close.
   */
  @Test
  fun `every emitted row id is unique`() {
    val tree =
      Tree(
        sections =
          listOf(
            section("a", "A", rows = listOf("x", "y", "x")),
            section("b", "B", rows = listOf("y", "z")),
          )
      )

    val ids = FlattenedTree.of(tree).items.filterIsInstance<Item.Row>().map { it.row.id }

    assertEquals(listOf("x", "y", "z"), ids)
    assertEquals(ids.size, ids.toSet().size)
  }

  @Test
  fun `a duplicate row id keeps the first occurrence, not the last`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(
              id = "a",
              header = "A",
              rows = listOf(RowSpec(id = "x", label = "first"), RowSpec(id = "x", label = "second")),
            )
          )
      )

    val rows = FlattenedTree.of(tree).items.filterIsInstance<Item.Row>()

    assertEquals(1, rows.size)
    assertEquals("first", rows.single().row.label)
  }

  @Test
  fun `a duplicate section id is dropped whole`() {
    val tree =
      Tree(
        sections =
          listOf(section("a", "First", rows = listOf("x")), section("a", "Second", rows = listOf("y")))
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(1, flattened.sections.size)
    assertEquals(listOf("x"), flattened.items.filterIsInstance<Item.Row>().map { it.row.id })
  }

  /**
   * A section can be emptied by deduplication alone, and then it must vanish entirely.
   *
   * The header going too is the point: a `plain` list that kept it would pin a header over the
   * next section's rows, which is the "two headers touching" artefact `of` documents.
   */
  @Test
  fun `a section left with no rows contributes nothing, header included`() {
    val tree =
      Tree(
        sections =
          listOf(
            section("a", "A", rows = listOf("x")),
            section("b", "B", rows = listOf("x")),
            section("c", "C", rows = listOf("y")),
          )
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(listOf("A", "C"), flattened.items.filterIsInstance<Item.Header>().map { it.title })
    assertEquals(listOf("a", "c"), flattened.sections.map { it.id })
  }

  // -- row index versus adapter position --------------------------------------------------------

  /**
   * The distinction `Item.Row.rowIndex` carries a paragraph of comment about.
   *
   * Headers and footers occupy adapter positions and no row indices. Sending one where the other
   * is expected windows the wrong rows, and only on lists that happen to have headers — so a test
   * without headers would prove nothing.
   */
  @Test
  fun `row indices skip headers and footers, adapter positions do not`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(
              id = "a",
              header = "A",
              footer = "note",
              rows = listOf(RowSpec(id = "x"), RowSpec(id = "y")),
            ),
            section("b", "B", rows = listOf("z")),
          )
      )

    val flattened = FlattenedTree.of(tree)

    // header, x, y, footer, header, z
    assertEquals(6, flattened.items.size)
    assertEquals(3, flattened.rowCount)
    assertEquals(listOf(0, 1, 2), flattened.items.filterIsInstance<Item.Row>().map { it.rowIndex })
    assertArrayEquals(intArrayOf(1, 2, 5), flattened.adapterPositionByRowIndex)
  }

  @Test
  fun `rowIndexAt reports -1 for a header and for a footer`() {
    val tree =
      Tree(
        sections =
          listOf(SectionSpec(id = "a", header = "A", footer = "note", rows = listOf(RowSpec(id = "x"))))
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(-1, flattened.rowIndexAt(0))
    assertEquals(0, flattened.rowIndexAt(1))
    assertEquals(-1, flattened.rowIndexAt(2))
    assertEquals(-1, flattened.rowIndexAt(99))
  }

  // -- the sticky header's lookup ---------------------------------------------------------------

  @Test
  fun `headerPositionAbove answers for rows, footers and the header itself`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(id = "a", header = "A", rows = listOf(RowSpec(id = "x"))),
            SectionSpec(id = "b", header = "B", footer = "note", rows = listOf(RowSpec(id = "y"))),
          )
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(0, flattened.headerPositionAbove(0))
    assertEquals(0, flattened.headerPositionAbove(1))
    assertEquals(2, flattened.headerPositionAbove(2))
    assertEquals(2, flattened.headerPositionAbove(3))
    assertEquals(2, flattened.headerPositionAbove(4))
  }

  /** A headerless section has nothing to pin, and must say so rather than pin its neighbour's. */
  @Test
  fun `a section without a header reports -1, not the section above`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(id = "a", header = "A", rows = listOf(RowSpec(id = "x"))),
            SectionSpec(id = "b", header = null, rows = listOf(RowSpec(id = "y"))),
          )
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(0, flattened.headerPositionAbove(1))
    assertEquals(-1, flattened.headerPositionAbove(2))
  }

  @Test
  fun `headerPositionAbove is out-of-range safe`() {
    val flattened = FlattenedTree.of(Tree(sections = listOf(section("a", "A", rows = listOf("x")))))

    assertEquals(-1, flattened.headerPositionAbove(-1))
    assertEquals(-1, flattened.headerPositionAbove(99))
  }

  // -- corner shapes ----------------------------------------------------------------------------

  /** A section of one row is `only` — not `first`, which would round the top and square the base. */
  @Test
  fun `a lone row is only, not first`() {
    val flattened = FlattenedTree.of(Tree(sections = listOf(section("a", "A", rows = listOf("x")))))

    assertEquals(Item.Position.only, flattened.items.filterIsInstance<Item.Row>().single().positionInSection)
  }

  @Test
  fun `positions run first, middle, last down a section`() {
    val tree = Tree(sections = listOf(section("a", "A", rows = listOf("x", "y", "z", "w"))))

    val positions = FlattenedTree.of(tree).items.filterIsInstance<Item.Row>().map { it.positionInSection }

    assertEquals(
      listOf(Item.Position.first, Item.Position.middle, Item.Position.middle, Item.Position.last),
      positions,
    )
  }

  /** Deduplication happens *before* positions are assigned, so the survivors re-round correctly. */
  @Test
  fun `positions are assigned after duplicates are dropped`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(
              id = "a",
              rows = listOf(RowSpec(id = "x"), RowSpec(id = "x"), RowSpec(id = "y")),
            )
          )
      )

    val positions = FlattenedTree.of(tree).items.filterIsInstance<Item.Row>().map { it.positionInSection }

    assertEquals(listOf(Item.Position.first, Item.Position.last), positions)
  }

  // -- chip strips ------------------------------------------------------------------------------

  /**
   * One adapter position, every row index.
   *
   * The strip is a single item to the outer list but `serialize` concatenates all rows regardless
   * of layout, so `onVisibleRangeChange` must still be able to name the rows inside it.
   */
  @Test
  fun `a chips section is one item spanning all of its row indices`() {
    val tree =
      Tree(
        sections =
          listOf(
            section("a", "A", rows = listOf("x")),
            SectionSpec(
              id = "chips",
              layout = SectionLayout.chips,
              rows = listOf(RowSpec(id = "c1"), RowSpec(id = "c2"), RowSpec(id = "c3")),
            ),
            section("b", "B", rows = listOf("y")),
          )
      )

    val flattened = FlattenedTree.of(tree)
    val strip = flattened.items.filterIsInstance<Item.ChipStrip>().single()

    assertEquals(3, strip.rows.size)
    assertEquals(1, strip.firstRowIndex)
    assertEquals(3, strip.lastRowIndex)
    assertEquals(5, flattened.rowCount)
    // Every chip resolves to the strip's own adapter position.
    val stripPosition = flattened.items.indexOf(strip)
    assertArrayEquals(
      intArrayOf(stripPosition, stripPosition, stripPosition),
      flattened.adapterPositionByRowIndex.copyOfRange(1, 4),
    )
  }

  @Test
  fun `rowIndexAt and lastRowIndexAt report the two ends of a strip`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(
              id = "chips",
              layout = SectionLayout.chips,
              rows = listOf(RowSpec(id = "c1"), RowSpec(id = "c2")),
            )
          )
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(0, flattened.rowIndexAt(0))
    assertEquals(1, flattened.lastRowIndexAt(0))
  }

  // -- the scrubber's stops ---------------------------------------------------------------------

  /**
   * `firstAdapterPosition` must land on the first *row*, not on the header.
   *
   * `SectionIndexView` scrolls to it with `scrollToPositionWithOffset(position, 0)`; pointing at
   * the header would put the header under the pinned copy of itself.
   */
  @Test
  fun `firstAdapterPosition points past the header`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(id = "a", header = "A", indexTitle = "A", rows = listOf(RowSpec(id = "x"))),
            SectionSpec(id = "b", header = "B", indexTitle = "B", rows = listOf(RowSpec(id = "y"))),
          )
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(listOf(1, 3), flattened.sections.map { it.firstAdapterPosition })
    assertTrue(flattened.items[1] is Item.Row)
    assertTrue(flattened.items[3] is Item.Row)
  }

  /**
   * An unindexed section still gets an entry, carrying a null title.
   *
   * The filtering is `SectionIndexView`'s — a section with no letter is not a blank stop, it is not
   * a stop — but the entry has to exist for the sticky header, which indexes the same list.
   */
  @Test
  fun `a section without an indexTitle keeps its entry and a null title`() {
    val tree =
      Tree(
        sections =
          listOf(
            SectionSpec(id = "a", indexTitle = "A", rows = listOf(RowSpec(id = "x"))),
            SectionSpec(id = "b", rows = listOf(RowSpec(id = "y"))),
          )
      )

    val flattened = FlattenedTree.of(tree)

    assertEquals(2, flattened.sections.size)
    assertEquals("A", flattened.sections[0].indexTitle)
    assertNull(flattened.sections[1].indexTitle)
  }

  // -- degenerate input -------------------------------------------------------------------------

  @Test
  fun `an empty tree flattens to nothing rather than throwing`() {
    val flattened = FlattenedTree.of(Tree())

    assertEquals(emptyList<Item>(), flattened.items)
    assertEquals(0, flattened.rowCount)
    assertEquals(-1, flattened.headerPositionAbove(0))
    assertEquals(-1, flattened.rowIndexAt(0))
  }

  private fun section(id: String, header: String?, rows: List<String>) =
    SectionSpec(id = id, header = header, rows = rows.map { RowSpec(id = it) })
}

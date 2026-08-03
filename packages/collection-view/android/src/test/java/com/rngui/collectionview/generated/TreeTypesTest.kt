// Generated from src/tree.ts by scripts/gen-kotlin-types.mjs. Do not edit.
//
// Decodes the generated fixture and asserts that *every* field arrived. Each fixture value is
// deliberately different from the field's default, so a field the decoder skipped shows up as a
// failed assertion rather than as a plausible-looking zero.
//
// This is the contract test between src/tree.ts and the generated Kotlin model, and it reads the
// *same fixture files the Swift test reads* — `ios/Tests/`, passed in by Gradle as
// `rngui.fixtureDir` rather than copied, because two copies of a contract fixture is two
// contracts. What the two platforms accept cannot drift while this passes.
//
// Run it through the example app's Gradle:
//   apps/example/android/gradlew -p apps/example/android \
//     :rngui_collection-view:testDebugUnitTest

package com.rngui.collectionview.generated

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeTypesTest {
  private fun load(name: String): String {
    val dir =
      requireNotNull(System.getProperty("rngui.fixtureDir")) {
        "rngui.fixtureDir is unset — see the systemProperty in android/build.gradle"
      }
    val file = File(dir, "$name.json")
    assertTrue("$file missing; run 'npm run gen'", file.isFile)
    return file.readText()
  }

  /** Every field in the schema decodes to its fixture value. */
  @Test
  fun everyFieldRoundTrips() {
    val decoded = Tree.decode(load("TreeTypesFixture"))

    assertEquals(1, decoded.sections.size)
    assertEquals("Tree.sections[0].id", decoded.sections.get(0).id)
    assertEquals("Tree.sections[0].header", decoded.sections.get(0).header)
    assertEquals("Tree.sections[0].action.title", decoded.sections.get(0).action?.title)
    assertEquals("Tree.sections[0].action.systemImage", decoded.sections.get(0).action?.systemImage)
    assertEquals(true, decoded.sections.get(0).action?.disabled)
    assertEquals("Tree.sections[0].footer", decoded.sections.get(0).footer)
    assertEquals(SectionLayout.chips, decoded.sections.get(0).layout)
    assertEquals("Tree.sections[0].indexTitle", decoded.sections.get(0).indexTitle)
    assertEquals(1, decoded.sections.get(0).rows.size)
    assertEquals("Tree.sections[0].rows[0].id", decoded.sections.get(0).rows.get(0).id)
    assertEquals(RowKind.chip, decoded.sections.get(0).rows.get(0).kind)
    assertEquals("Tree.sections[0].rows[0].label", decoded.sections.get(0).rows.get(0).label)
    assertEquals("Tree.sections[0].rows[0].secondaryLabel", decoded.sections.get(0).rows.get(0).secondaryLabel)
    assertEquals("Tree.sections[0].rows[0].value", decoded.sections.get(0).rows.get(0).value)
    assertEquals(AccessoryKind.spinner, decoded.sections.get(0).rows.get(0).accessory)
    assertEquals("Tree.sections[0].rows[0].systemImage", decoded.sections.get(0).rows.get(0).systemImage)
    assertEquals("Tree.sections[0].rows[0].materialSymbol", decoded.sections.get(0).rows.get(0).materialSymbol)
    assertEquals("Tree.sections[0].rows[0].imageColor", decoded.sections.get(0).rows.get(0).imageColor)
    assertEquals("Tree.sections[0].rows[0].imageBackground", decoded.sections.get(0).rows.get(0).imageBackground)
    assertEquals("Tree.sections[0].rows[0].imageMonogram", decoded.sections.get(0).rows.get(0).imageMonogram)
    assertEquals(11.0, decoded.sections.get(0).rows.get(0).imageSize)
    assertEquals("Tree.sections[0].rows[0].badge", decoded.sections.get(0).rows.get(0).badge)
    assertEquals("Tree.sections[0].rows[0].badgeColor", decoded.sections.get(0).rows.get(0).badgeColor)
    assertEquals(true, decoded.sections.get(0).rows.get(0).secondaryLabelTinted)
    assertEquals("Tree.sections[0].rows[0].font.family", decoded.sections.get(0).rows.get(0).font?.family)
    assertEquals(FontDesign.monospaced, decoded.sections.get(0).rows.get(0).font?.design)
    assertEquals(22.0, decoded.sections.get(0).rows.get(0).font?.size)
    assertEquals("Tree.sections[0].rows[0].font.weight", decoded.sections.get(0).rows.get(0).font?.weight)
    assertEquals("Tree.sections[0].rows[0].font.variations", decoded.sections.get(0).rows.get(0).font?.variations)
    assertEquals(true, decoded.sections.get(0).rows.get(0).font?.scaled)
    assertEquals(true, decoded.sections.get(0).rows.get(0).selectable)
    assertEquals(true, decoded.sections.get(0).rows.get(0).disabled)
    assertEquals("Tree.sections[0].rows[0].tintColor", decoded.sections.get(0).rows.get(0).tintColor)
    assertEquals(33, decoded.sections.get(0).rows.get(0).hostIndex)
    assertEquals(44.0, decoded.sections.get(0).rows.get(0).height)
    assertEquals(true, decoded.sections.get(0).rows.get(0).on)
    assertEquals("Tree.sections[0].rows[0].text", decoded.sections.get(0).rows.get(0).text)
    assertEquals("Tree.sections[0].rows[0].placeholder", decoded.sections.get(0).rows.get(0).placeholder)
    assertEquals(KeyboardType.asciiCapable, decoded.sections.get(0).rows.get(0).keyboardType)
    assertEquals(AutoCapitalize.characters, decoded.sections.get(0).rows.get(0).autoCapitalize)
    assertEquals(ReturnKeyType.send, decoded.sections.get(0).rows.get(0).returnKeyType)
    assertEquals(true, decoded.sections.get(0).rows.get(0).secure)
    assertEquals(55, decoded.sections.get(0).rows.get(0).maxLines)
    assertEquals(66.0, decoded.sections.get(0).rows.get(0).dateMillis)
    assertEquals(DatePickerMode.dateAndTime, decoded.sections.get(0).rows.get(0).datePickerMode)
    assertEquals(DatePickerStyle.wheels, decoded.sections.get(0).rows.get(0).datePickerStyle)
    assertEquals(77.0, decoded.sections.get(0).rows.get(0).minDateMillis)
    assertEquals(88.0, decoded.sections.get(0).rows.get(0).maxDateMillis)
    assertEquals(99.0, decoded.sections.get(0).rows.get(0).sliderValue)
    assertEquals(110.0, decoded.sections.get(0).rows.get(0).sliderMin)
    assertEquals(121.0, decoded.sections.get(0).rows.get(0).sliderMax)
    assertEquals(132.0, decoded.sections.get(0).rows.get(0).sliderStep)
    assertEquals("Tree.sections[0].rows[0].sliderMinImage", decoded.sections.get(0).rows.get(0).sliderMinImage)
    assertEquals("Tree.sections[0].rows[0].sliderMaxImage", decoded.sections.get(0).rows.get(0).sliderMaxImage)
    assertEquals(ButtonRole.plain, decoded.sections.get(0).rows.get(0).role)
    assertEquals(1, decoded.sections.get(0).rows.get(0).menuItems?.size)
    assertEquals("Tree.sections[0].rows[0].menuItems[0].id", decoded.sections.get(0).rows.get(0).menuItems?.get(0)?.id)
    assertEquals("Tree.sections[0].rows[0].menuItems[0].title", decoded.sections.get(0).rows.get(0).menuItems?.get(0)?.title)
    assertEquals("Tree.sections[0].rows[0].menuItems[0].systemImage", decoded.sections.get(0).rows.get(0).menuItems?.get(0)?.systemImage)
    assertEquals(true, decoded.sections.get(0).rows.get(0).menuItems?.get(0)?.destructive)
    assertEquals(true, decoded.sections.get(0).rows.get(0).menuItems?.get(0)?.disabled)
    assertEquals("Tree.sections[0].rows[0].selectedItemId", decoded.sections.get(0).rows.get(0).selectedItemId)
    assertEquals(1, decoded.sections.get(0).rows.get(0).trailingActions?.size)
    assertEquals("Tree.sections[0].rows[0].trailingActions[0].id", decoded.sections.get(0).rows.get(0).trailingActions?.get(0)?.id)
    assertEquals("Tree.sections[0].rows[0].trailingActions[0].title", decoded.sections.get(0).rows.get(0).trailingActions?.get(0)?.title)
    assertEquals("Tree.sections[0].rows[0].trailingActions[0].systemImage", decoded.sections.get(0).rows.get(0).trailingActions?.get(0)?.systemImage)
    assertEquals(SwipeActionStyle.destructive, decoded.sections.get(0).rows.get(0).trailingActions?.get(0)?.style)
    assertEquals("Tree.sections[0].rows[0].trailingActions[0].backgroundColor", decoded.sections.get(0).rows.get(0).trailingActions?.get(0)?.backgroundColor)
    assertEquals(1, decoded.sections.get(0).rows.get(0).leadingActions?.size)
    assertEquals("Tree.sections[0].rows[0].leadingActions[0].id", decoded.sections.get(0).rows.get(0).leadingActions?.get(0)?.id)
    assertEquals("Tree.sections[0].rows[0].leadingActions[0].title", decoded.sections.get(0).rows.get(0).leadingActions?.get(0)?.title)
    assertEquals("Tree.sections[0].rows[0].leadingActions[0].systemImage", decoded.sections.get(0).rows.get(0).leadingActions?.get(0)?.systemImage)
    assertEquals(SwipeActionStyle.destructive, decoded.sections.get(0).rows.get(0).leadingActions?.get(0)?.style)
    assertEquals("Tree.sections[0].rows[0].leadingActions[0].backgroundColor", decoded.sections.get(0).rows.get(0).leadingActions?.get(0)?.backgroundColor)
    assertEquals(ListAppearance.plain, decoded.listAppearance)
    assertEquals(AndroidListStyle.segmented, decoded.androidListStyle)
    assertEquals("Tree.appearance.background", decoded.appearance?.background)
    assertEquals("Tree.appearance.backgroundGradient.colors[0]", decoded.appearance?.backgroundGradient?.colors?.get(0))
    assertEquals(143.0, decoded.appearance?.backgroundGradient?.locations?.get(0))
    assertEquals(154.0, decoded.appearance?.backgroundGradient?.angle)
    assertEquals("Tree.appearance.rowBackground", decoded.appearance?.rowBackground)
    assertEquals("Tree.appearance.separator", decoded.appearance?.separator)
    assertEquals("Tree.appearance.labelColor", decoded.appearance?.labelColor)
    assertEquals("Tree.appearance.secondaryLabelColor", decoded.appearance?.secondaryLabelColor)
    assertEquals("Tree.appearance.headerTextColor", decoded.appearance?.headerTextColor)
    assertEquals(HeaderBackgroundStyle.transparent, decoded.appearance?.headerBackgroundStyle)
    assertEquals("Tree.appearance.footerTextColor", decoded.appearance?.footerTextColor)
    assertEquals("Tree.appearance.tintColor", decoded.appearance?.tintColor)
    assertEquals(165.0, decoded.appearance?.sectionSpacing)
    assertEquals("Tree.appearance.font.family", decoded.appearance?.font?.family)
    assertEquals(FontDesign.monospaced, decoded.appearance?.font?.design)
    assertEquals(176.0, decoded.appearance?.font?.size)
    assertEquals("Tree.appearance.font.weight", decoded.appearance?.font?.weight)
    assertEquals("Tree.appearance.font.variations", decoded.appearance?.font?.variations)
    assertEquals(true, decoded.appearance?.font?.scaled)
    assertEquals("Tree.appearance.headerFont.family", decoded.appearance?.headerFont?.family)
    assertEquals(FontDesign.monospaced, decoded.appearance?.headerFont?.design)
    assertEquals(187.0, decoded.appearance?.headerFont?.size)
    assertEquals("Tree.appearance.headerFont.weight", decoded.appearance?.headerFont?.weight)
    assertEquals("Tree.appearance.headerFont.variations", decoded.appearance?.headerFont?.variations)
    assertEquals(true, decoded.appearance?.headerFont?.scaled)
    assertEquals("Tree.appearance.footerFont.family", decoded.appearance?.footerFont?.family)
    assertEquals(FontDesign.monospaced, decoded.appearance?.footerFont?.design)
    assertEquals(198.0, decoded.appearance?.footerFont?.size)
    assertEquals("Tree.appearance.footerFont.weight", decoded.appearance?.footerFont?.weight)
    assertEquals("Tree.appearance.footerFont.variations", decoded.appearance?.footerFont?.variations)
    assertEquals(true, decoded.appearance?.footerFont?.scaled)
    assertEquals("Tree.darkAppearance.background", decoded.darkAppearance?.background)
    assertEquals("Tree.darkAppearance.backgroundGradient.colors[0]", decoded.darkAppearance?.backgroundGradient?.colors?.get(0))
    assertEquals(209.0, decoded.darkAppearance?.backgroundGradient?.locations?.get(0))
    assertEquals(220.0, decoded.darkAppearance?.backgroundGradient?.angle)
    assertEquals("Tree.darkAppearance.rowBackground", decoded.darkAppearance?.rowBackground)
    assertEquals("Tree.darkAppearance.separator", decoded.darkAppearance?.separator)
    assertEquals("Tree.darkAppearance.labelColor", decoded.darkAppearance?.labelColor)
    assertEquals("Tree.darkAppearance.secondaryLabelColor", decoded.darkAppearance?.secondaryLabelColor)
    assertEquals("Tree.darkAppearance.headerTextColor", decoded.darkAppearance?.headerTextColor)
    assertEquals(HeaderBackgroundStyle.transparent, decoded.darkAppearance?.headerBackgroundStyle)
    assertEquals("Tree.darkAppearance.footerTextColor", decoded.darkAppearance?.footerTextColor)
    assertEquals("Tree.darkAppearance.tintColor", decoded.darkAppearance?.tintColor)
    assertEquals(231.0, decoded.darkAppearance?.sectionSpacing)
    assertEquals("Tree.darkAppearance.font.family", decoded.darkAppearance?.font?.family)
    assertEquals(FontDesign.monospaced, decoded.darkAppearance?.font?.design)
    assertEquals(242.0, decoded.darkAppearance?.font?.size)
    assertEquals("Tree.darkAppearance.font.weight", decoded.darkAppearance?.font?.weight)
    assertEquals("Tree.darkAppearance.font.variations", decoded.darkAppearance?.font?.variations)
    assertEquals(true, decoded.darkAppearance?.font?.scaled)
    assertEquals("Tree.darkAppearance.headerFont.family", decoded.darkAppearance?.headerFont?.family)
    assertEquals(FontDesign.monospaced, decoded.darkAppearance?.headerFont?.design)
    assertEquals(253.0, decoded.darkAppearance?.headerFont?.size)
    assertEquals("Tree.darkAppearance.headerFont.weight", decoded.darkAppearance?.headerFont?.weight)
    assertEquals("Tree.darkAppearance.headerFont.variations", decoded.darkAppearance?.headerFont?.variations)
    assertEquals(true, decoded.darkAppearance?.headerFont?.scaled)
    assertEquals("Tree.darkAppearance.footerFont.family", decoded.darkAppearance?.footerFont?.family)
    assertEquals(FontDesign.monospaced, decoded.darkAppearance?.footerFont?.design)
    assertEquals(264.0, decoded.darkAppearance?.footerFont?.size)
    assertEquals("Tree.darkAppearance.footerFont.weight", decoded.darkAppearance?.footerFont?.weight)
    assertEquals("Tree.darkAppearance.footerFont.variations", decoded.darkAppearance?.footerFont?.variations)
    assertEquals(true, decoded.darkAppearance?.footerFont?.scaled)
  }

  /**
   * A tree from a *newer* JS bundle than this binary still decodes to a usable list.
   *
   * The same fixture and the same assertions run on iOS — see
   * `ios/Tests/TreeTypesTests.swift`. A decoder that is lenient on one platform and strict on
   * the other is worse than one that is strict on both, because only one of the two phones goes
   * blank.
   */
  @Test
  fun forwardCompatibleTreeDecodes() {
    val decoded = Tree.decode(load("ForwardCompatFixture"))

    assertEquals("the known section survived", 1, decoded.sections.size)
    assertEquals("its header decoded past the unknown sibling key", "Still here", decoded.sections.get(0).header)
    assertEquals("both rows survived, including the unrenderable one", 2, decoded.sections.get(0).rows.size)
    assertEquals("the known row is intact", RowKind.`value`, decoded.sections.get(0).rows.get(0).kind)
    assertEquals("and kept its fields", "Value", decoded.sections.get(0).rows.get(0).value)
    assertEquals("the future row kind degraded rather than throwing", RowKind.unknown, decoded.sections.get(0).rows.get(1).kind)
    assertEquals("and the rest of that row still decoded", "From a newer bundle", decoded.sections.get(0).rows.get(1).label)
    assertEquals("an unrecognised value for a known enum degrades too", ListAppearance.unknown, decoded.listAppearance)
    assertEquals("appearance decoded past its unknown key", "#112233FF", decoded.appearance?.labelColor)
    assertEquals("and so did the font nested inside it", "620", decoded.appearance?.font?.weight)
  }

  /** A missing key takes the field's default rather than throwing. */
  @Test
  fun missingKeysTakeDefaults() {
    assertEquals(0, Tree.decode("{}").sections.size)
  }

  /** An unrecognised enum value degrades to `unknown` instead of failing the whole payload. */
  @Test
  fun unknownEnumDegradesInsteadOfThrowing() {
    val decoded = Tree.decode("""{"sections":[{"id":"s","rows":[{"id":"r","kind":"nope"}]}]}""")
    assertEquals(RowKind.unknown, decoded.sections[0].rows[0].kind)
  }

  /**
   * Malformed JSON renders an empty list rather than throwing.
   *
   * There is no Swift counterpart because there is no equivalent risk: `tree` arrives as a prop
   * on the UI thread, and a decoder that throws there takes the app with it.
   */
  @Test
  fun malformedJsonDecodesToAnEmptyTree() {
    assertEquals(0, Tree.decode("not json at all").sections.size)
    assertEquals(0, Tree.decode("[]").sections.size)
    assertEquals(0, Tree.decode(null).sections.size)
  }
}

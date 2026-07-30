// Generated from src/tree.ts by scripts/gen-swift-types.mjs. Do not edit.
//
// Decodes the generated fixture and asserts that *every* field arrived. Each fixture value is
// deliberately different from the field's default, so a field the decoder skipped shows up as a
// failed assertion rather than as a plausible-looking zero.
//
// This is the contract test between src/tree.ts and ios/Generated/TreeTypes.swift. Run it with
// `npm run test:swift` — it needs no simulator, because the model depends only on Foundation.

import XCTest
@testable import RNGUICollectionViewModel

final class TreeTypesTests: XCTestCase {
  private func loadFixture() throws -> Data {
    let url = try XCTUnwrap(
      Bundle.module.url(forResource: "TreeTypesFixture", withExtension: "json"),
      "TreeTypesFixture.json missing from the test bundle"
    )
    return try Data(contentsOf: url)
  }

  /// Every field in the schema decodes to its fixture value.
  func testEveryFieldRoundTrips() throws {
    let decoded = try JSONDecoder().decode(Tree.self, from: try loadFixture())

    XCTAssertEqual(decoded.sections.count, 1)
    XCTAssertEqual(decoded.sections[0].id, "Tree.sections[0].id")
    XCTAssertEqual(decoded.sections[0].header, "Tree.sections[0].header")
    XCTAssertEqual(decoded.sections[0].footer, "Tree.sections[0].footer")
    XCTAssertEqual(decoded.sections[0].indexTitle, "Tree.sections[0].indexTitle")
    XCTAssertEqual(decoded.sections[0].rows.count, 1)
    XCTAssertEqual(decoded.sections[0].rows[0].id, "Tree.sections[0].rows[0].id")
    XCTAssertEqual(decoded.sections[0].rows[0].kind, .datePicker)
    XCTAssertEqual(decoded.sections[0].rows[0].label, "Tree.sections[0].rows[0].label")
    XCTAssertEqual(decoded.sections[0].rows[0].secondaryLabel, "Tree.sections[0].rows[0].secondaryLabel")
    XCTAssertEqual(decoded.sections[0].rows[0].value, "Tree.sections[0].rows[0].value")
    XCTAssertEqual(decoded.sections[0].rows[0].accessory, .spinner)
    XCTAssertEqual(decoded.sections[0].rows[0].systemImage, "Tree.sections[0].rows[0].systemImage")
    XCTAssertEqual(decoded.sections[0].rows[0].imageColor, "Tree.sections[0].rows[0].imageColor")
    XCTAssertEqual(decoded.sections[0].rows[0].secondaryLabelTinted, true)
    XCTAssertEqual(decoded.sections[0].rows[0].font?.family, "Tree.sections[0].rows[0].font.family")
    XCTAssertEqual(decoded.sections[0].rows[0].font?.design, .monospaced)
    XCTAssertEqual(decoded.sections[0].rows[0].font?.size, 11)
    XCTAssertEqual(decoded.sections[0].rows[0].font?.weight, "Tree.sections[0].rows[0].font.weight")
    XCTAssertEqual(decoded.sections[0].rows[0].font?.variations, "Tree.sections[0].rows[0].font.variations")
    XCTAssertEqual(decoded.sections[0].rows[0].font?.scaled, true)
    XCTAssertEqual(decoded.sections[0].rows[0].selectable, true)
    XCTAssertEqual(decoded.sections[0].rows[0].disabled, true)
    XCTAssertEqual(decoded.sections[0].rows[0].tintColor, "Tree.sections[0].rows[0].tintColor")
    XCTAssertEqual(decoded.sections[0].rows[0].hostIndex, 22)
    XCTAssertEqual(decoded.sections[0].rows[0].height, 33)
    XCTAssertEqual(decoded.sections[0].rows[0].on, true)
    XCTAssertEqual(decoded.sections[0].rows[0].text, "Tree.sections[0].rows[0].text")
    XCTAssertEqual(decoded.sections[0].rows[0].placeholder, "Tree.sections[0].rows[0].placeholder")
    XCTAssertEqual(decoded.sections[0].rows[0].keyboardType, .asciiCapable)
    XCTAssertEqual(decoded.sections[0].rows[0].autoCapitalize, .characters)
    XCTAssertEqual(decoded.sections[0].rows[0].returnKeyType, .send)
    XCTAssertEqual(decoded.sections[0].rows[0].secure, true)
    XCTAssertEqual(decoded.sections[0].rows[0].maxLines, 44)
    XCTAssertEqual(decoded.sections[0].rows[0].dateMillis, 55)
    XCTAssertEqual(decoded.sections[0].rows[0].datePickerMode, .dateAndTime)
    XCTAssertEqual(decoded.sections[0].rows[0].datePickerStyle, .wheels)
    XCTAssertEqual(decoded.sections[0].rows[0].minDateMillis, 66)
    XCTAssertEqual(decoded.sections[0].rows[0].maxDateMillis, 77)
    XCTAssertEqual(decoded.sections[0].rows[0].role, .plain)
    XCTAssertEqual(decoded.sections[0].rows[0].menuItems?.count, 1)
    XCTAssertEqual(decoded.sections[0].rows[0].menuItems?[0].id, "Tree.sections[0].rows[0].menuItems[0].id")
    XCTAssertEqual(decoded.sections[0].rows[0].menuItems?[0].title, "Tree.sections[0].rows[0].menuItems[0].title")
    XCTAssertEqual(decoded.sections[0].rows[0].menuItems?[0].systemImage, "Tree.sections[0].rows[0].menuItems[0].systemImage")
    XCTAssertEqual(decoded.sections[0].rows[0].menuItems?[0].destructive, true)
    XCTAssertEqual(decoded.sections[0].rows[0].menuItems?[0].disabled, true)
    XCTAssertEqual(decoded.sections[0].rows[0].selectedItemId, "Tree.sections[0].rows[0].selectedItemId")
    XCTAssertEqual(decoded.sections[0].rows[0].trailingActions?.count, 1)
    XCTAssertEqual(decoded.sections[0].rows[0].trailingActions?[0].id, "Tree.sections[0].rows[0].trailingActions[0].id")
    XCTAssertEqual(decoded.sections[0].rows[0].trailingActions?[0].title, "Tree.sections[0].rows[0].trailingActions[0].title")
    XCTAssertEqual(decoded.sections[0].rows[0].trailingActions?[0].systemImage, "Tree.sections[0].rows[0].trailingActions[0].systemImage")
    XCTAssertEqual(decoded.sections[0].rows[0].trailingActions?[0].style, .destructive)
    XCTAssertEqual(decoded.sections[0].rows[0].trailingActions?[0].backgroundColor, "Tree.sections[0].rows[0].trailingActions[0].backgroundColor")
    XCTAssertEqual(decoded.sections[0].rows[0].leadingActions?.count, 1)
    XCTAssertEqual(decoded.sections[0].rows[0].leadingActions?[0].id, "Tree.sections[0].rows[0].leadingActions[0].id")
    XCTAssertEqual(decoded.sections[0].rows[0].leadingActions?[0].title, "Tree.sections[0].rows[0].leadingActions[0].title")
    XCTAssertEqual(decoded.sections[0].rows[0].leadingActions?[0].systemImage, "Tree.sections[0].rows[0].leadingActions[0].systemImage")
    XCTAssertEqual(decoded.sections[0].rows[0].leadingActions?[0].style, .destructive)
    XCTAssertEqual(decoded.sections[0].rows[0].leadingActions?[0].backgroundColor, "Tree.sections[0].rows[0].leadingActions[0].backgroundColor")
    XCTAssertEqual(decoded.listAppearance, .plain)
    XCTAssertEqual(decoded.appearance?.background, "Tree.appearance.background")
    XCTAssertEqual(decoded.appearance?.rowBackground, "Tree.appearance.rowBackground")
    XCTAssertEqual(decoded.appearance?.separator, "Tree.appearance.separator")
    XCTAssertEqual(decoded.appearance?.labelColor, "Tree.appearance.labelColor")
    XCTAssertEqual(decoded.appearance?.secondaryLabelColor, "Tree.appearance.secondaryLabelColor")
    XCTAssertEqual(decoded.appearance?.headerTextColor, "Tree.appearance.headerTextColor")
    XCTAssertEqual(decoded.appearance?.headerBackgroundStyle, .transparent)
    XCTAssertEqual(decoded.appearance?.footerTextColor, "Tree.appearance.footerTextColor")
    XCTAssertEqual(decoded.appearance?.tintColor, "Tree.appearance.tintColor")
    XCTAssertEqual(decoded.appearance?.sectionSpacing, 88)
    XCTAssertEqual(decoded.appearance?.font?.family, "Tree.appearance.font.family")
    XCTAssertEqual(decoded.appearance?.font?.design, .monospaced)
    XCTAssertEqual(decoded.appearance?.font?.size, 99)
    XCTAssertEqual(decoded.appearance?.font?.weight, "Tree.appearance.font.weight")
    XCTAssertEqual(decoded.appearance?.font?.variations, "Tree.appearance.font.variations")
    XCTAssertEqual(decoded.appearance?.font?.scaled, true)
    XCTAssertEqual(decoded.appearance?.headerFont?.family, "Tree.appearance.headerFont.family")
    XCTAssertEqual(decoded.appearance?.headerFont?.design, .monospaced)
    XCTAssertEqual(decoded.appearance?.headerFont?.size, 110)
    XCTAssertEqual(decoded.appearance?.headerFont?.weight, "Tree.appearance.headerFont.weight")
    XCTAssertEqual(decoded.appearance?.headerFont?.variations, "Tree.appearance.headerFont.variations")
    XCTAssertEqual(decoded.appearance?.headerFont?.scaled, true)
    XCTAssertEqual(decoded.appearance?.footerFont?.family, "Tree.appearance.footerFont.family")
    XCTAssertEqual(decoded.appearance?.footerFont?.design, .monospaced)
    XCTAssertEqual(decoded.appearance?.footerFont?.size, 121)
    XCTAssertEqual(decoded.appearance?.footerFont?.weight, "Tree.appearance.footerFont.weight")
    XCTAssertEqual(decoded.appearance?.footerFont?.variations, "Tree.appearance.footerFont.variations")
    XCTAssertEqual(decoded.appearance?.footerFont?.scaled, true)
    XCTAssertEqual(decoded.darkAppearance?.background, "Tree.darkAppearance.background")
    XCTAssertEqual(decoded.darkAppearance?.rowBackground, "Tree.darkAppearance.rowBackground")
    XCTAssertEqual(decoded.darkAppearance?.separator, "Tree.darkAppearance.separator")
    XCTAssertEqual(decoded.darkAppearance?.labelColor, "Tree.darkAppearance.labelColor")
    XCTAssertEqual(decoded.darkAppearance?.secondaryLabelColor, "Tree.darkAppearance.secondaryLabelColor")
    XCTAssertEqual(decoded.darkAppearance?.headerTextColor, "Tree.darkAppearance.headerTextColor")
    XCTAssertEqual(decoded.darkAppearance?.headerBackgroundStyle, .transparent)
    XCTAssertEqual(decoded.darkAppearance?.footerTextColor, "Tree.darkAppearance.footerTextColor")
    XCTAssertEqual(decoded.darkAppearance?.tintColor, "Tree.darkAppearance.tintColor")
    XCTAssertEqual(decoded.darkAppearance?.sectionSpacing, 132)
    XCTAssertEqual(decoded.darkAppearance?.font?.family, "Tree.darkAppearance.font.family")
    XCTAssertEqual(decoded.darkAppearance?.font?.design, .monospaced)
    XCTAssertEqual(decoded.darkAppearance?.font?.size, 143)
    XCTAssertEqual(decoded.darkAppearance?.font?.weight, "Tree.darkAppearance.font.weight")
    XCTAssertEqual(decoded.darkAppearance?.font?.variations, "Tree.darkAppearance.font.variations")
    XCTAssertEqual(decoded.darkAppearance?.font?.scaled, true)
    XCTAssertEqual(decoded.darkAppearance?.headerFont?.family, "Tree.darkAppearance.headerFont.family")
    XCTAssertEqual(decoded.darkAppearance?.headerFont?.design, .monospaced)
    XCTAssertEqual(decoded.darkAppearance?.headerFont?.size, 154)
    XCTAssertEqual(decoded.darkAppearance?.headerFont?.weight, "Tree.darkAppearance.headerFont.weight")
    XCTAssertEqual(decoded.darkAppearance?.headerFont?.variations, "Tree.darkAppearance.headerFont.variations")
    XCTAssertEqual(decoded.darkAppearance?.headerFont?.scaled, true)
    XCTAssertEqual(decoded.darkAppearance?.footerFont?.family, "Tree.darkAppearance.footerFont.family")
    XCTAssertEqual(decoded.darkAppearance?.footerFont?.design, .monospaced)
    XCTAssertEqual(decoded.darkAppearance?.footerFont?.size, 165)
    XCTAssertEqual(decoded.darkAppearance?.footerFont?.weight, "Tree.darkAppearance.footerFont.weight")
    XCTAssertEqual(decoded.darkAppearance?.footerFont?.variations, "Tree.darkAppearance.footerFont.variations")
    XCTAssertEqual(decoded.darkAppearance?.footerFont?.scaled, true)
  }

  /// An unrecognised enum value degrades to `.unknown` instead of failing the whole payload.
  ///
  /// This is what keeps a JS bundle that is newer than the native binary — the normal state of
  /// affairs under `expo-updates` — from rendering an empty list.
  func testUnknownEnumDegradesInsteadOfThrowing() throws {
    let json = Data(#"{"sections":[{"id":"s","rows":[{"id":"r","kind":"not-a-real-kind"}]}]}"#.utf8)
    let decoded = try JSONDecoder().decode(Tree.self, from: json)
    XCTAssertEqual(decoded.sections.first?.rows.first?.kind, .unknown)
  }

  /// A missing key takes the field's default rather than throwing.
  func testMissingKeysTakeDefaults() throws {
    let decoded = try JSONDecoder().decode(Tree.self, from: Data(#"{}"#.utf8))
    XCTAssertEqual(decoded.sections.count, 0)
  }
}

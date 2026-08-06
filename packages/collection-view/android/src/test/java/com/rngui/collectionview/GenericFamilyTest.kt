package com.rngui.collectionview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The generic family table, against React Native's own.
 *
 * `FontSpec.family` takes the five CSS generic names or an app face, and the whole argument for
 * spelling them React Native's way is that a row and a `<Text>` beside it resolve the same face.
 * That only holds while the two tables agree, and React Native's is in
 * `RCTFontUtils.RCTGetFontDescriptorSystemDesign` — five entries, matched lowercased. These are
 * those five.
 *
 * The table is a `when` over strings, which is exactly the kind of thing that survives a refactor
 * with one arm quietly missing.
 */
class GenericFamilyTest {

  @Test
  fun `the five generic names resolve`() {
    assertEquals(GenericFamily.SANS, genericFamilyOf("system-ui"))
    assertEquals(GenericFamily.SANS, genericFamilyOf("ui-sans-serif"))
    assertEquals(GenericFamily.SERIF, genericFamilyOf("ui-serif"))
    assertEquals(GenericFamily.MONOSPACE, genericFamilyOf("ui-monospace"))
    // Android has no rounded system face. Degrading to the default one is the documented
    // behaviour, not an oversight — see the platform table in the README.
    assertEquals(GenericFamily.SANS, genericFamilyOf("ui-rounded"))
  }

  @Test
  fun `matching is case-insensitive, as React Native's is`() {
    assertEquals(GenericFamily.MONOSPACE, genericFamilyOf("UI-Monospace"))
    assertEquals(GenericFamily.SERIF, genericFamilyOf("UI-SERIF"))
  }

  @Test
  fun `an app face is not a generic name`() {
    assertNull(genericFamilyOf("Inter"))
    assertNull(genericFamilyOf("SF Pro Rounded"))
    assertNull(genericFamilyOf(""))
  }

  /**
   * UIKit spells the design `monospaced`; CSS and React Native spell the family `ui-monospace`.
   * Accepting both would make the wrong one work on Android and fail on iOS, where React Native's
   * table is the one that answers — so the wrong one is a font name we do not have, and takes the
   * missing-font path.
   */
  @Test
  fun `UIKit's spelling is not aliased`() {
    assertNull(genericFamilyOf("ui-monospaced"))
    assertNull(genericFamilyOf("rounded"))
  }
}

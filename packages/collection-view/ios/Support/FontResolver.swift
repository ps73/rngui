import CoreText
import UIKit

/**
 * Resolves a `FontSpec` into a concrete `UIFont`.
 *
 * Handles the four things a list actually needs from typography, and they compose in a
 * specific order because each one is expressed differently in UIKit:
 *
 * 1. **The face.** One `family`, holding either a generic name or an app-bundled one. The five
 *    generic names are CSS's and React Native's — `RCTFontUtils` maps the same five onto
 *    `UIFontDescriptor.SystemDesign` for `<Text>`, and this is the same table so that a row and
 *    a label beside it resolve identically.
 * 2. **Everything else** is a bundled family, looked up by PostScript name first and by family
 *    name second, because the two differ often enough — `expo-font` registers under the key you
 *    gave `useFonts`, which is usually neither.
 * 3. **Variable-font axes.** Applied through Core Text, since UIKit has no API for them.
 * 4. **Dynamic Type.** Last, because scaling has to wrap a finished font.
 *
 * Cached: this runs once per cell configuration, and descriptor matching is expensive enough to
 * show up while scrolling.
 */
enum FontResolver {
  private static let cache = NSCache<NSString, UIFont>()

  static func resolve(_ spec: FontSpec?, fallback: UIFont) -> UIFont {
    guard let spec, isCustomised(spec) else { return fallback }

    let key = cacheKey(spec, fallback: fallback)
    if let cached = cache.object(forKey: key) { return cached }

    let size = CGFloat(spec.size ?? Double(fallback.pointSize))
    var font = baseFont(spec, size: size, fallback: fallback)

    if let variations = spec.variations, !variations.isEmpty {
      font = applying(variations: variations, to: font)
    }
    // Scaling wraps a finished font, so it has to come last. Defaults on: a list that ignores
    // the reader's text size is broken for the people who changed it.
    if spec.scaled ?? true {
      font = UIFontMetrics.default.scaledFont(for: font)
    }

    cache.setObject(font, forKey: key)
    return font
  }

  /// Everything unset means "use the slot's own font", which is not the same as the system font
  /// at the fallback's size — a section header is smaller and heavier than a row label.
  private static func isCustomised(_ spec: FontSpec) -> Bool {
    spec.family != nil
      || spec.size != nil
      || spec.weight != nil
      || spec.variations != nil
  }

  private static func cacheKey(_ spec: FontSpec, fallback: UIFont) -> NSString {
    let parts: [String] = [
      spec.family ?? "",
      // A closure rather than `String.init`, which is ambiguous for `Double`.
      spec.size.map { String($0) } ?? "",
      spec.weight ?? "",
      spec.variations ?? "",
      String(spec.scaled ?? true),
      // Part of the key because an unset size or weight is inherited from it.
      "\(fallback.fontName)@\(fallback.pointSize)",
    ]
    return parts.joined(separator: "|") as NSString
  }

  private static func baseFont(_ spec: FontSpec, size: CGFloat, fallback: UIFont) -> UIFont {
    var design: UIFontDescriptor.SystemDesign?

    if let family = spec.family, !family.isEmpty {
      // A generic name is a design on the system font, and never a face to look up: no device
      // has a family called `ui-rounded`, so falling through to the lookup below would warn
      // about a font the caller never claimed to have registered.
      if let generic = systemDesign(family) {
        design = generic
      } else if let named = UIFont(name: family, size: size) {
        // PostScript name first, then family. `expo-font` registers under the key passed to
        // `useFonts`, which may be either — and asking for the wrong one returns nil rather
        // than falling back.
        return named
      } else {
        let descriptor = UIFontDescriptor(fontAttributes: [.family: family])
        let matched = UIFont(descriptor: descriptor, size: size)
        // A failed family match yields the system font rather than nil, so compare to detect it.
        if matched.familyName == family {
          return matched
        }
        #if DEBUG
        print(
          "[@rngui/collection-view] Font family '\(family)' is neither a generic name "
            + "(system-ui, ui-sans-serif, ui-serif, ui-rounded, ui-monospace) nor a registered "
            + "font. With expo-font, use the key you passed to useFonts."
        )
        #endif
      }
    }

    let weightValue = weight(from: spec.weight)

    // Neither a design nor a weight to apply: keep the slot's own face and only resize it, so
    // `{ size: 20 }` on a semibold section header stays semibold instead of collapsing to the
    // plain system font.
    guard design != nil || weightValue != nil else {
      return size == fallback.pointSize ? fallback : fallback.withSize(size)
    }

    var descriptor = UIFont.systemFont(ofSize: size).fontDescriptor

    // Design first, weight second, and the order matters. `withDesign` re-derives the descriptor
    // from the design's own family and drops any weight it cannot match there — so weighting
    // first (via `UIFont.systemFont(ofSize:weight:)`) and designing second makes `withDesign`
    // return nil for combinations like semibold + monospaced, which silently leaves a
    // proportional font. That shows up as row labels turning monospaced while the headers, which
    // also carry a weight, do not.
    if let design, let designed = descriptor.withDesign(design) {
      descriptor = designed
    }
    // An explicit weight wins; otherwise inherit the slot's own. Applying a design starts from the
    // plain system font, which is *regular* — so without this a section header asking only for
    // `family: 'ui-rounded'` comes back SF Rounded Regular instead of SF Rounded Semibold, quietly
    // losing the emphasis that made it read as a header.
    let effectiveWeight = weightValue?.rawValue ?? inheritedWeight(of: fallback)

    if let effectiveWeight {
      // Merged into the existing traits, never assigned over them: `addingAttributes` replaces the
      // value at `.traits` wholesale, and the chosen system design is recorded inside that same
      // dictionary.
      var traits = descriptor.fontAttributes[.traits] as? [UIFontDescriptor.TraitKey: Any] ?? [:]
      traits[.weight] = effectiveWeight
      descriptor = descriptor.addingAttributes([.traits: traits])
    }

    return UIFont(descriptor: descriptor, size: size)
  }

  /// The numeric weight baked into a resolved font, so a design change can preserve it.
  private static func inheritedWeight(of font: UIFont) -> CGFloat? {
    let traits = font.fontDescriptor.fontAttributes[.traits] as? [UIFontDescriptor.TraitKey: Any]
    return traits?[.weight] as? CGFloat
  }

  /**
   * The five generic family names, or `nil` for "this is an app font".
   *
   * The same table as `RCTFontUtils.RCTGetFontDescriptorSystemDesign`, lowercased the same way,
   * so `<Text style={{ fontFamily: 'ui-rounded' }}>` and a row asking for `ui-rounded` cannot
   * disagree. `system-ui` and `ui-sans-serif` resolve to `.default` rather than to nil, because
   * naming one is an explicit request for the system face — that is what lets a row override a
   * bundled family set on the root.
   */
  private static func systemDesign(_ family: String) -> UIFontDescriptor.SystemDesign? {
    switch family.lowercased() {
    case "system-ui", "ui-sans-serif": return UIFontDescriptor.SystemDesign.default
    case "ui-serif": return .serif
    case "ui-rounded": return .rounded
    case "ui-monospace": return .monospaced
    default: return nil
    }
  }

  /**
   * Applies variable-font axes, given as `'wght=620,wdth=110'`.
   *
   * Core Text keys variations by a four-character-code axis identifier, so each tag is packed
   * into an integer. Axes the face does not expose are simply ignored by Core Text, which is
   * what makes one spec safe to share between a variable font and a static one.
   *
   * **The name attribute has to go, and that is the whole trick.** A descriptor that names a
   * concrete face — which is exactly what `UIFont(name:)` produces — is matched by *name*, and
   * Core Text then ignores the variation attribute completely. The symptom is silent and
   * convincing: the font is right, the axis does nothing, and `wght=350` and `wght=900` render
   * byte-identically. Rebuilding around the family keeps the face and lets the axes decide the
   * instance.
   */
  private static func applying(variations: String, to font: UIFont) -> UIFont {
    var axes: [Int: CGFloat] = [:]

    for pair in variations.split(separator: ",") {
      let parts = pair.split(separator: "=", maxSplits: 1)
      guard parts.count == 2 else { continue }
      let tag = parts[0].trimmingCharacters(in: .whitespaces)
      guard
        let identifier = fourCharCode(tag),
        let value = Double(parts[1].trimmingCharacters(in: .whitespaces))
      else { continue }
      axes[identifier] = CGFloat(value)
    }

    guard !axes.isEmpty else { return font }

    var attributes = font.fontDescriptor.fontAttributes
    attributes.removeValue(forKey: .name)
    attributes[.family] = font.familyName
    attributes[kCTFontVariationAttribute as UIFontDescriptor.AttributeName] = axes

    let descriptor = UIFontDescriptor(fontAttributes: attributes)
    let varied = UIFont(descriptor: descriptor, size: font.pointSize)

    // A family that turns out not to be variable comes back as some member of itself, which is
    // still the right face — so there is nothing to detect and nothing to fall back to.
    return varied
  }

  private static func fourCharCode(_ tag: String) -> Int? {
    let bytes = Array(tag.utf8)
    guard bytes.count == 4 else { return nil }
    return bytes.reduce(0) { ($0 << 8) | Int($1) }
  }

  /// Accepts both the named weights and the numeric CSS scale, matching React Native.
  private static func weight(from raw: String?) -> UIFont.Weight? {
    switch raw {
    case "ultraLight", "100": return .ultraLight
    case "thin", "200": return .thin
    case "light", "300": return .light
    case "regular", "normal", "400": return .regular
    case "medium", "500": return .medium
    case "semibold", "600": return .semibold
    case "bold", "700": return .bold
    case "heavy", "800": return .heavy
    case "black", "900": return .black
    default: return nil
    }
  }
}

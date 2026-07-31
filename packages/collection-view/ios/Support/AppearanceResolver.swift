import UIKit

/**
 * Resolves the light/dark appearance pair into UIKit values.
 *
 * The important design decision is that **colours come back as dynamic `UIColor`s**, built with
 * `UIColor(dynamicProvider:)`. UIKit then re-resolves them itself whenever the trait collection
 * changes, which means a light/dark switch needs no reconfiguration of any cell, no trait
 * observation for the common case, and — crucially — no round trip to JavaScript. The naive
 * alternative, resolving to a concrete colour at apply time, produces a list that only restyles
 * on the *next* unrelated render, which is the kind of bug that gets reported as "dark mode is
 * flaky".
 *
 * A field left unset falls through to the platform's own colour, and those are already dynamic,
 * so partial theming stays correct in both modes for free.
 */
struct AppearanceResolver {
  private let light: Appearance?
  private let dark: Appearance?

  init(light: Appearance?, dark: Appearance?) {
    self.light = light
    self.dark = dark
  }

  /**
   * A colour that follows the interface style, or `fallback` when neither side sets it.
   *
   * `dark` falls back to `light` field by field, never the other way round: setting only
   * `appearance` should give that look in both modes, which is the least surprising behaviour
   * and means adding a dark override is always additive.
   */
  func color(_ keyPath: KeyPath<Appearance, String?>, fallback: UIColor) -> UIColor {
    optionalColor(keyPath, fallback: fallback) ?? fallback
  }

  /**
   * `nil` when neither side themes this field, which is not the same as "the default colour".
   *
   * `UIBackgroundConfiguration` is the reason this distinction has to exist: its
   * `backgroundColor` is `nil` by default and that nil is *meaningful* — it lets UIKit supply
   * both the resting colour and the highlight behaviour. Assigning a concrete colour, even the
   * identical one, replaces that behaviour and the row stops greying out when pressed. So an
   * unthemed field must be left alone rather than resolved to its own default.
   */
  func optionalColor(
    _ keyPath: KeyPath<Appearance, String?>,
    fallback: UIColor? = nil
  ) -> UIColor? {
    let lightHex = light?[keyPath: keyPath]
    let darkHex = dark?[keyPath: keyPath] ?? lightHex

    guard lightHex != nil || darkHex != nil else { return nil }

    return UIColor { traits in
      let hex = traits.userInterfaceStyle == .dark ? darkHex : lightHex
      // Resolving the fallback against the same traits keeps a partially themed list coherent:
      // an unparseable value behaves exactly as if the field had been left out.
      return UIColor(rnguiHex: hex)
        ?? fallback?.resolvedColor(with: traits)
        ?? .clear
    }
  }

  /**
   * Non-colour values, resolved against a specific trait collection.
   *
   * These cannot be dynamic — there is no `UIFont(dynamicProvider:)` and no dynamic `CGFloat` —
   * so a caller who genuinely sets a different font or spacing per mode needs the visible cells
   * reconfigured when the style flips. That is the only reason this component observes trait
   * changes at all; see `interfaceStyleDidChange` in the host.
   *
   * Takes a closure rather than a `KeyPath<Appearance, T?>`. The key-path form looks tidier and
   * type-checks, but unifying `KeyPath<Appearance, FontSpec?>` against `KeyPath<Appearance, T?>`
   * resolved to something that returned nil at runtime for a value that was demonstrably present
   * in the decoded struct. A closure leaves nothing to infer.
   */
  func value<T>(
    _ pick: (Appearance) -> T?,
    for traits: UITraitCollection
  ) -> T? {
    if traits.userInterfaceStyle == .dark, let dark, let value = pick(dark) {
      return value
    }
    return light.flatMap(pick)
  }

  /// True when either side sets a value that cannot be expressed as a dynamic colour, and the
  /// list therefore has to be reconfigured on a style change rather than left to UIKit.
  var hasStyleDependentNonColorValues: Bool {
    guard let dark else { return false }
    return dark.font != nil
      || dark.headerFont != nil
      || dark.footerFont != nil
      || dark.sectionSpacing != nil
  }
}

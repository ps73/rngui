import UIKit

extension UIColor {
  /**
   * Parses the `#RRGGBBAA` form that JavaScript normalises every appearance colour into.
   *
   * The shorter CSS forms are accepted too, purely so that a hand-written value in a test or a
   * debugging session behaves the way anyone would expect. Production input is always eight
   * digits, because `resolveColor` in `appearance.ts` runs React Native's own colour parser
   * first — which is what lets a caller write `'red'`, `'rgba(0,0,0,.5)'` or whatever their
   * theming library resolves to.
   */
  convenience init?(rnguiHex hex: String?) {
    guard var digits = hex?.trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
    if digits.hasPrefix("#") { digits.removeFirst() }

    // Expand the shorthand forms by doubling each digit: #1a2 -> #11aa22.
    if digits.count == 3 || digits.count == 4 {
      digits = digits.map { "\($0)\($0)" }.joined()
    }
    guard digits.count == 6 || digits.count == 8 else { return nil }
    guard let value = UInt64(digits, radix: 16) else { return nil }

    let hasAlpha = digits.count == 8
    let red = CGFloat((value >> (hasAlpha ? 24 : 16)) & 0xFF) / 255
    let green = CGFloat((value >> (hasAlpha ? 16 : 8)) & 0xFF) / 255
    let blue = CGFloat((value >> (hasAlpha ? 8 : 0)) & 0xFF) / 255
    let alpha = hasAlpha ? CGFloat(value & 0xFF) / 255 : 1

    self.init(red: red, green: green, blue: blue, alpha: alpha)
  }

  /**
   * Composites `overlay` at `alpha` over the receiver, staying dynamic.
   *
   * Exists for the pressed state of a *themed* row. UIKit draws that state itself as long as the
   * cell keeps its default background configuration, but a caller who sets `rowBackground` replaces
   * that configuration and the highlight with it — so it has to be reconstructed.
   *
   * Overlaying `label` is what makes one rule work in both modes: `label` resolves to black in light
   * and white in dark, so the same 8% composite darkens a light row and lightens a dark one, which
   * is exactly what iOS does. Resolving both sides against the *same* trait collection inside the
   * provider is what keeps the result dynamic rather than freezing whichever mode was current when
   * the row was configured.
   */
  func rnguiOverlaid(with overlay: UIColor, alpha: CGFloat) -> UIColor {
    UIColor { traits in
      let base = self.resolvedColor(with: traits)
      let top = overlay.resolvedColor(with: traits)

      var baseComponents = (red: CGFloat(0), green: CGFloat(0), blue: CGFloat(0), alpha: CGFloat(0))
      var topComponents = (red: CGFloat(0), green: CGFloat(0), blue: CGFloat(0), alpha: CGFloat(0))
      guard
        base.getRed(
          &baseComponents.red,
          green: &baseComponents.green,
          blue: &baseComponents.blue,
          alpha: &baseComponents.alpha
        ),
        top.getRed(
          &topComponents.red,
          green: &topComponents.green,
          blue: &topComponents.blue,
          alpha: &topComponents.alpha
        )
      else {
        // A pattern colour has no components to read. Returning the base unchanged loses the
        // highlight, which is better than returning something arbitrary.
        return base
      }

      let mix = { (bottom: CGFloat, top: CGFloat) in bottom + (top - bottom) * alpha }
      return UIColor(
        red: mix(baseComponents.red, topComponents.red),
        green: mix(baseComponents.green, topComponents.green),
        blue: mix(baseComponents.blue, topComponents.blue),
        alpha: baseComponents.alpha
      )
    }
  }
}

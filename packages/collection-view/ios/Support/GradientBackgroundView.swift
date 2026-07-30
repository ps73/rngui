import UIKit

/**
 * The collection view's `backgroundView` when `appearance.backgroundGradient` is set.
 *
 * Two things make this a view rather than a bare layer:
 *
 * 1. **`CAGradientLayer.colors` takes `CGColor`**, and a `CGColor` has no trait collection — a
 *    dynamic `UIColor` resolved once would freeze whichever interface style happened to be current.
 *    Everywhere else in this component a colour crosses as `UIColor(dynamicProvider:)` and UIKit
 *    re-resolves it for free; here the re-resolution has to be done by hand, which needs something
 *    that receives trait changes.
 * 2. **`backgroundView` is the one thing UIKit keeps pinned to the scroll view's bounds** while the
 *    content scrolls over it. A layer added to the collection view would scroll with the content;
 *    one on the container would be painted over by the list's own background colour.
 */
@MainActor
final class GradientBackgroundView: UIView {
  /// Owned by the host so the layer survives being detached and reattached as `backgroundView`.
  var gradient: CAGradientLayer?
  var spec: GradientSpec?

  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false

    if #available(iOS 17.0, *) {
      registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: Self, _) in
        view.applySpec()
      }
    }
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — this view is only created in code")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    // No implicit animation: the layer is resized inside the collection view's own layout pass, and
    // a quarter-second cross-fade on every bounds change reads as the background lagging the scroll.
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    gradient?.frame = bounds
    CATransaction.commit()
  }

  override func traitCollectionDidChange(_ previous: UITraitCollection?) {
    super.traitCollectionDidChange(previous)
    if #unavailable(iOS 17.0) {
      if traitCollection.hasDifferentColorAppearance(comparedTo: previous) {
        applySpec()
      }
    }
  }

  func applySpec() {
    guard let gradient, let spec else { return }

    CATransaction.begin()
    CATransaction.setDisableActions(true)

    gradient.colors = spec.colors.map { hex in
      (UIColor(rnguiHex: hex) ?? .clear).resolvedColor(with: traitCollection).cgColor
    }

    // Ignored unless it matches the colours one-for-one, which is what `CAGradientLayer` requires —
    // a mismatched array makes Core Animation fall back to even spacing anyway, silently.
    if let locations = spec.locations, locations.count == spec.colors.count {
      gradient.locations = locations.map { NSNumber(value: $0) }
    } else {
      gradient.locations = nil
    }

    // Degrees clockwise from a top-to-bottom gradient, converted to the unit-square start and end
    // points Core Animation wants. `0` is vertical; `90` runs left to right.
    let radians = (spec.angle ?? 0) * .pi / 180
    let dx = sin(radians) / 2
    let dy = cos(radians) / 2
    gradient.startPoint = CGPoint(x: 0.5 - dx, y: 0.5 - dy)
    gradient.endPoint = CGPoint(x: 0.5 + dx, y: 0.5 + dy)

    CATransaction.commit()
  }
}

import UIKit

/**
 * A pinned section header's background with no bottom edge.
 *
 * `blurred` is a material that stops in a straight line — correct before iOS 26, and out of place
 * under a navigation bar using the soft scroll edge effect, because the eye reads the two hard
 * edges as competing. This fades the same material out instead, so the header dissolves into the
 * rows scrolling beneath it.
 *
 * A `UIBackgroundConfiguration.customView` rather than its `visualEffect`, because the effect view
 * UIKit manages for that property is not ours to mask — and a gradient mask is the whole idea.
 */
@MainActor
final class SoftHeaderBackgroundView: UIView {
  private let effect = UIVisualEffectView(
    effect: UIBlurEffect(style: .systemChromeMaterial)
  )
  private let fade = CAGradientLayer()

  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false

    effect.translatesAutoresizingMaskIntoConstraints = true
    effect.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(effect)

    /**
     * Opaque for the top two thirds, then out.
     *
     * Not a fade across the whole height: the header's letter is vertically centred, and a gradient
     * starting at the top would leave its lower half sitting on almost nothing while rows slide
     * under it. The falloff belongs below the text, which is also where a `UIScrollEdgeEffect` puts
     * its own.
     */
    fade.colors = [
      UIColor.black.cgColor,
      UIColor.black.cgColor,
      UIColor.black.withAlphaComponent(0).cgColor,
    ]
    fade.locations = [0, 0.6, 1]
    effect.layer.mask = fade
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — this view is only created in code")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    // No implicit animation: the header is resized inside the collection view's own layout pass,
    // and a quarter-second cross-fade on every bounds change reads as the blur lagging the scroll.
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    fade.frame = effect.bounds
    CATransaction.commit()
  }
}

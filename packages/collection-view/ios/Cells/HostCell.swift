import UIKit

/**
 * A cell that hosts a real React Native view.
 *
 * This class is the payoff of building on React Native's own codegen rather than Nitro. The
 * component view overrides `mountChildComponentView:` / `unmountChildComponentView:` without
 * calling `super`, which means it — not `RCTViewComponentView` — decides where a mounted
 * child physically lives. So the child becomes a genuine subview of `contentView`, and UIKit
 * clips it, hit-tests it and scrolls it for free.
 *
 * The alternative, which Nitro forces, is to leave the child parented elsewhere and
 * reposition it over the cell on every scrolled frame, with a mask layer to fake clipping
 * and a `hitTest` override to route touches. None of that exists here.
 *
 * What this cannot do is recycle its content. Every hosted row is a distinct React subtree
 * with distinct state, so there is no pool of interchangeable views to draw from — hence
 * `detach()` returning the view to a parking bay rather than dropping it.
 */
final class HostCell: UICollectionViewCell {
  private weak var hosted: UIView?
  private weak var parkingView: UIView?
  private var heightConstraint: NSLayoutConstraint?

  /// Reserves vertical space. Drives self-sizing against an `.estimated` item dimension.
  func setHeight(_ height: CGFloat) {
    if let existing = heightConstraint {
      existing.constant = height
      return
    }
    let constraint = contentView.heightAnchor.constraint(equalToConstant: height)
    constraint.isActive = true
    heightConstraint = constraint
  }

  func attach(_ view: UIView, parkingView: UIView) {
    guard hosted !== view else { return }
    detach()

    #if DEBUG
    // Yoga lays the hosted subtree out against the *collection view's* width minus a horizontal
    // margin that JavaScript has to assume, because UIKit's grouped-card inset is not queryable
    // from there. If that assumption is wrong, this cell's frame is right but everything *inside*
    // the subtree was measured for a different width — so it overflows and gets clipped, or falls
    // short and leaves a gap. Reporting the exact delta beats guessing at the constant.
    let yogaWidth = view.frame.width
    let cellWidth = contentView.bounds.width
    if cellWidth > 0, abs(yogaWidth - cellWidth) > 0.5 {
      print(
        "[@rngui/collection-view] hosted width mismatch: Yoga measured \(yogaWidth)pt, "
          + "cell contentView is \(cellWidth)pt (delta \(yogaWidth - cellWidth)pt)."
      )
    }
    #endif

    self.parkingView = parkingView
    view.removeFromSuperview()
    view.isHidden = false
    // Fabric has already laid this subtree out and assigns frames directly, so the view must
    // stay in the autoresizing world rather than being handed to Auto Layout.
    view.translatesAutoresizingMaskIntoConstraints = true
    contentView.addSubview(view)
    view.frame = contentView.bounds
    hosted = view
  }

  /// The view this cell is currently displaying, so the host can find the one cell that owns it.
  var hostedView: UIView? { hosted }

  func detach() {
    guard let view = hosted else { return }
    hosted = nil

    /**
     * Only if this cell still owns it, and that guard is load-bearing.
     *
     * During a reload UIKit configures the *replacement* cell before recycling the outgoing one, so
     * by the time `prepareForReuse` runs here the view has already been claimed by the cell that
     * replaced this one. Hiding it then blanks a row that is on screen and correct — which is
     * exactly what a theme change did: `reloadSections` re-created every cell, and every hosted row
     * whose identity survived went empty while the one that had just been *inserted* was fine.
     */
    guard view.superview === contentView else { return }

    view.isHidden = true
    view.removeFromSuperview()
    // Returned to the parking bay rather than left orphaned. React still owns this view and
    // will unmount it on its own schedule; a view with no superview at all is one mounting
    // transaction away from an assertion, and the bay is also where the next cell to claim
    // it will look.
    parkingView?.addSubview(view)
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    hosted?.frame = contentView.bounds
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    detach()
  }
}

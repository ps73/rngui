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
 *
 * A `UICollectionViewListCell` rather than a plain `UICollectionViewCell`, so that a hosted row can
 * take the same grouped card as the described rows around it when it asks for one. A plain cell
 * cannot: `UIBackgroundConfiguration` carries a corner radius but no masked-corners, so the
 * first-and-last-in-section treatment is not expressible through it — it comes from the list cell
 * reading the layout's own attributes. The row asks with `Host`'s `background` prop, and asking is
 * opt-in; see `makeHostRegistration` for what the two cases install.
 */
final class HostCell: UICollectionViewListCell {
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
    // Moving it out of the bay is the only thing that makes it visible, and moving it back is the
    // only thing that hides it again. Nothing here writes `isHidden` on a view React owns — see
    // `ParkingView` for what that cost the last time it did.
    view.removeFromSuperview()
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
     * replaced this one. Reparenting it then blanks a row that is on screen and correct — which is
     * exactly what a theme change did: `reloadSections` re-created every cell, and every hosted row
     * whose identity survived went empty while the one that had just been *inserted* was fine.
     */
    guard view.superview === contentView else { return }

    // Returned to the parking bay rather than left orphaned, and the move is also what hides it:
    // the bay is invisible, so a parked child draws nothing without this cell ever writing to a
    // property React owns. The bay is where the next cell to claim it will look.
    //
    // Un-parented first rather than relying on `addSubview` to reparent, so that a bay which has
    // already gone — the weak reference is nil only while the host is being torn down — still
    // leaves the child out of a dead cell. React asserts on recycling a view that still has a
    // superview; orphaned is the recoverable half of that choice.
    view.removeFromSuperview()
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

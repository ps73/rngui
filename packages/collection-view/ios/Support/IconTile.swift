import UIKit

/**
 * Settings' rounded coloured squares: a white SF Symbol on a filled tile.
 *
 * **Rendered into a `UIImage` rather than assembled from live views, because the tile has to be the
 * content configuration's `image`.** `UIListContentConfiguration` owns the leading image slot — its
 * spacing to the label, its alignment across a section, its behaviour under Dynamic Type — and the
 * alternative, a `UICellAccessory.customView` at `.leading`, is a different slot with different
 * metrics. Matching Settings means using the slot Settings uses.
 *
 * Two things follow from that, and both are handled below rather than accepted as limitations.
 */
@MainActor
enum IconTile {
  /// 29pt, which is what Settings uses. Not configurable yet: nothing has needed a second size, and
  /// a tile that does not match its neighbours is worse than no tile.
  static let edge: CGFloat = 29

  /**
   * Rendered images, keyed by symbol, colour and size.
   *
   * `applyImage` runs on every cell configure — every scroll, every reuse — and drawing a bitmap
   * there would be absurd. An `NSCache` rather than a dictionary so memory pressure can reclaim it;
   * the cost of a miss is one redraw.
   */
  private static let cache = NSCache<NSString, UIImage>()

  static func image(symbol: String, background: UIColor) -> UIImage? {
    let key = "\(symbol)|\(background)|\(edge)" as NSString
    if let cached = cache.object(forKey: key) { return cached }

    /**
     * One rendering per interface style, registered together.
     *
     * A `UIImage` is a bitmap and has no trait collection, so a dynamic `UIColor` drawn into one is
     * frozen at whichever style happened to be current — the failure mode being a tile that keeps
     * light-mode blue after the device goes dark. `UIImageAsset` is UIKit's own answer: register a
     * variant per style and the image view picks between them, with no JavaScript render and no
     * trait observer of ours.
     */
    let asset = UIImageAsset()
    for style: UIUserInterfaceStyle in [.light, .dark] {
      let traits = UITraitCollection(userInterfaceStyle: style)
      guard let rendered = render(symbol: symbol, background: background, traits: traits) else {
        return nil
      }
      asset.register(rendered, with: traits)
    }

    // `.current` resolves now, but the returned image carries the asset — so it keeps adapting.
    let image = asset.image(with: .current)
    cache.setObject(image, forKey: key)
    return image
  }

  private static func render(
    symbol: String,
    background: UIColor,
    traits: UITraitCollection
  ) -> UIImage? {
    let configuration = UIImage.SymbolConfiguration(
      pointSize: edge * 0.55,
      weight: .medium
    )
    guard
      let glyph = UIImage(systemName: symbol, withConfiguration: configuration)?
        .withTintColor(.white, renderingMode: .alwaysOriginal)
    else { return nil }

    /**
     * Drawn from a real view rather than a `UIBezierPath`, and the reason is the corner.
     *
     * iOS rounds these tiles with a *continuous* curve — the squircle — and `UIBezierPath` can only
     * draw the circular kind. `CALayer.cornerCurve` can do it, so the tile is built as a layer and
     * rendered. At 29pt the difference is small and unmistakable side by side, which for a screen
     * whose whole purpose is to sit next to the real Settings app is the difference that matters.
     */
    let tile = UIView(frame: CGRect(x: 0, y: 0, width: edge, height: edge))
    tile.backgroundColor = background
    tile.layer.cornerRadius = edge * 0.2255
    tile.layer.cornerCurve = .continuous
    // Renders the layer against this style rather than whatever is current, which is what makes the
    // two variants actually differ.
    tile.overrideUserInterfaceStyle = traits.userInterfaceStyle

    let format = UIGraphicsImageRendererFormat()
    format.scale = 0
    format.opaque = false
    let renderer = UIGraphicsImageRenderer(size: tile.bounds.size, format: format)

    let image = renderer.image { context in
      tile.layer.render(in: context.cgContext)
      let size = glyph.size
      glyph.draw(
        in: CGRect(
          x: ((edge - size.width) / 2).rounded(),
          y: ((edge - size.height) / 2).rounded(),
          width: size.width,
          height: size.height
        )
      )
    }

    // Original, not template: the tile is already the colours it wants, and a template rendering
    // would flatten the whole thing to the list's tint.
    return image.withRenderingMode(.alwaysOriginal)
  }
}

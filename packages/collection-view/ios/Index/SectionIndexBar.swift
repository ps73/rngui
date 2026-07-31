import UIKit

/**
 * The A–Z scrubber down the trailing edge of a Contacts-style list.
 *
 * **There is no UIKit equivalent.** `sectionIndexTitles(for:)` and `sectionForSectionIndexTitle:`
 * are `UITableViewDataSource` methods; `UICollectionView` has no counterpart and no plans for one,
 * so the whole control — layout, hit testing, haptics, the magnified callout, accessibility — is
 * hand-built here.
 *
 * Two behaviours are copied from the system control because their absence is what makes an
 * imitation feel wrong rather than merely different:
 *
 * 1. **Scrolling is never animated.** The system scrubber tracks the finger frame for frame;
 *    animating each jump means the list is always chasing a position the finger has already left,
 *    which reads as lag rather than as smoothness.
 * 2. **A selection haptic fires on each change of letter, not on each touch event.** That tick is
 *    most of what makes the bar feel like a physical control.
 *
 * Deliberately a *sibling* of the collection view rather than a subview: it must not scroll with
 * the content, and it must not be `subviews[0]` — react-native-screens finds "the screen's scroll
 * view" by walking that one index (see the note in `RNGUICollectionViewComponentView.mm`).
 */
@MainActor
final class SectionIndexBar: UIView {
  /// One stop on the bar: a letter, and the section it scrolls to.
  struct Entry: Equatable {
    let title: String
    let sectionIndex: Int
  }

  /// Called with a *section* index, never a bar index — the two differ as soon as either the
  /// list has unindexed sections or the bar has thinned itself out.
  var onSelect: ((Int) -> Void)?

  /// Overrides the letter colour. Falls back to the inherited tint, i.e. `appearance.tintColor`.
  var titleColor: UIColor? {
    didSet { setNeedsDisplay() }
  }

  /// Points per letter. `nil` takes `Self.defaultRowHeight`.
  var rowHeight: CGFloat? {
    didSet {
      guard rowHeight != oldValue else { return }
      recomputeRows()
      setNeedsDisplay()
    }
  }

  /// The magnified letter shown beside the finger while scrubbing.
  var showsCallout = true

  private var entries: [Entry] = []
  /// What is actually drawn: one row per line of text, `•` included. Recomputed on resize
  /// because how many letters fit depends on the height available.
  private var rows: [Entry] = []
  private var isCollapsed = false
  private var activeRow: Int?

  private let feedback = UISelectionFeedbackGenerator()
  private let callout = CalloutView()

  /// The widest glyph the bar has to fit, plus breathing room. `#` is wider than any letter.
  static let preferredWidth: CGFloat = 22

  /**
   * Points per letter when the caller does not say.
   *
   * Deliberately a fixed compact metric rather than "the height divided by the number of letters".
   * The system control is a small block centred in the scroll view; stretching the letters to fill
   * a tall screen makes them drift apart and the control stops reading as one object. Stretching is
   * the obvious implementation and is wrong the moment you put it next to Contacts.
   */
  static let defaultRowHeight: CGFloat = 13

  /// Room for one line of text. Below this the bar starts dropping letters.
  private static let minimumRowHeight: CGFloat = 11

  private var effectiveRowHeight: CGFloat {
    max(Self.minimumRowHeight, rowHeight ?? Self.defaultRowHeight)
  }

  /// The block is centred, so everything — drawing, hit testing, the callout — measures from here.
  private var blockOrigin: CGFloat {
    max(0, (bounds.height - CGFloat(rows.count) * effectiveRowHeight) / 2)
  }

  private var font: UIFont {
    // Not scaled with Dynamic Type on purpose: the bar's job is to fit the *screen*, and the
    // system scrubber likewise stays put and drops letters rather than growing. Scaling here
    // would collapse a 26-letter index to `A • Z` at the larger accessibility sizes.
    .systemFont(ofSize: 11, weight: .semibold)
  }

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    // Drawn rather than composed from 26 `UILabel`s: it is one text run per frame against a
    // transparent background, and a label per letter would mean 26 views laid out on every
    // resize for no benefit.
    contentMode = .redraw

    isAccessibilityElement = true
    accessibilityTraits = .adjustable
    accessibilityLabel = "Section index"

    // `titleColor` is a dynamic `UIColor`, so the value it resolves to changes with the interface
    // style even though the property itself does not. Drawn text is not re-resolved the way a
    // `backgroundColor` is — it has to be drawn again.
    if #available(iOS 17.0, *) {
      registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: Self, _) in
        view.setNeedsDisplay()
      }
    }
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — this view is only created in code")
  }

  // MARK: - Content

  func setEntries(_ entries: [Entry]) {
    // Compares the section indices too, not just the letters. An unindexed section inserted ahead
    // of the indexed ones leaves every title identical while shifting what each one points at, and
    // comparing titles alone would keep the stale mapping — a scrubber that jumps to the wrong
    // place, with nothing on screen to suggest why.
    guard entries != self.entries else { return }
    self.entries = entries
    isHidden = entries.isEmpty
    recomputeRows()
    setNeedsDisplay()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    let previous = rows.count
    recomputeRows()
    // Only redraw when thinning actually changed the content; a plain height change redraws
    // through `contentMode = .redraw` anyway.
    if rows.count != previous { setNeedsDisplay() }
  }

  /**
   * Thins the index out when there is not enough vertical room for every letter.
   *
   * The system control does this too, and the result reads as `A • C • E …`. Every drawn row —
   * `•` rows included — still maps to a real section, so a touch anywhere on the bar lands
   * somewhere sensible rather than being ignored. That is what keeps a drag continuous instead
   * of stuttering across dead zones.
   */
  private func recomputeRows() {
    guard !entries.isEmpty else {
      rows = []
      isCollapsed = false
      return
    }

    // Thinning is driven by how many letters *fit at the chosen row height*, not by dividing the
    // height among however many there are.
    let capacity = max(1, Int(bounds.height / effectiveRowHeight))
    guard capacity < entries.count else {
      rows = entries
      isCollapsed = false
      return
    }

    isCollapsed = true
    rows = (0..<capacity).map { position in
      // Spread the kept letters evenly across the whole index, first and last included.
      let ratio = capacity == 1 ? 0 : Double(position) / Double(capacity - 1)
      let entry = entries[Int((ratio * Double(entries.count - 1)).rounded())]
      // Alternating rows become separators. The section mapping is kept, so the dot is a
      // usable target rather than a gap in the control.
      return position.isMultiple(of: 2)
        ? entry
        : Entry(title: "•", sectionIndex: entry.sectionIndex)
    }
  }

  // MARK: - Drawing

  override func draw(_ rect: CGRect) {
    guard !rows.isEmpty else { return }

    let rowHeight = effectiveRowHeight
    let top = blockOrigin
    let attributes: [NSAttributedString.Key: Any] = [
      .font: font,
      .foregroundColor: titleColor ?? tintColor ?? .tintColor,
    ]

    for (index, row) in rows.enumerated() {
      let text = row.title as NSString
      let size = text.size(withAttributes: attributes)
      let origin = CGPoint(
        x: (bounds.width - size.width) / 2,
        y: top + rowHeight * CGFloat(index) + (rowHeight - size.height) / 2
      )
      text.draw(at: origin, withAttributes: attributes)
    }
  }

  override func tintColorDidChange() {
    super.tintColorDidChange()
    setNeedsDisplay()
  }

  override func traitCollectionDidChange(_ previous: UITraitCollection?) {
    super.traitCollectionDidChange(previous)
    // Only below iOS 17, where `registerForTraitChanges` does not exist. Above it, this override
    // is deprecated and both paths would run.
    if #unavailable(iOS 17.0) {
      if traitCollection.hasDifferentColorAppearance(comparedTo: previous) {
        setNeedsDisplay()
      }
    }
  }

  // MARK: - Touches

  /**
   * Widens the touch target beyond the drawn glyphs.
   *
   * The system scrubber is likewise easier to grab than it looks — 22pt of drawn width is well
   * under the 44pt minimum, and a control this thin is unusable without help. Kept modest at the
   * leading edge, because this strip sits over the scroll indicator and every extra point is a
   * point of the list that can no longer be swiped.
   */
  override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    guard !rows.isEmpty, !isHidden else { return false }
    return bounds.insetBy(dx: -10, dy: 0).contains(point)
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    feedback.prepare()
    if showsCallout { callout.present(in: self) }
    handle(touch, isInitial: true)
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let touch = touches.first else { return }
    handle(touch, isInitial: false)
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    endTracking()
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    endTracking()
  }

  private func endTracking() {
    activeRow = nil
    callout.dismiss()
  }

  private func handle(_ touch: UITouch, isInitial: Bool) {
    guard !rows.isEmpty else { return }

    // Measured from the block, not the view: the finger can be anywhere along the bar's full height,
    // but the letters occupy only the centred block, so a touch above or below it clamps to the
    // first or last stop rather than mapping to nothing.
    let y = touch.location(in: self).y
    let index = min(
      max(Int((y - blockOrigin) / effectiveRowHeight), 0),
      rows.count - 1
    )

    // The callout follows the finger continuously even while the letter has not changed —
    // that decoupling is what makes it feel attached to the touch rather than to the bar.
    let row = rows[index]
    if showsCallout {
      callout.show(title: displayTitle(for: row), centeredAtY: y, in: self)
    }

    guard isInitial || index != activeRow else { return }
    activeRow = index
    feedback.selectionChanged()
    onSelect?(row.sectionIndex)
  }

  /// A `•` row has no letter of its own, so the callout shows the letter of the section it
  /// actually scrolls to. Showing a dot in a magnified bubble would be useless.
  private func displayTitle(for row: Entry) -> String {
    guard row.title == "•" else { return row.title }
    return entries.first { $0.sectionIndex == row.sectionIndex }?.title ?? row.title
  }

  // MARK: - Accessibility

  /**
   * Exposed as one adjustable element rather than 26 buttons.
   *
   * VoiceOver users move through it with swipe up/down, which is how the system scrubber
   * behaves, and it keeps a 26-stop control from flooding the rotor with elements that are 12pt
   * tall. Increment steps through *sections*, not drawn rows, so thinning the bar out never
   * makes letters unreachable — which is exactly the case a visual-only implementation loses.
   */
  override func accessibilityIncrement() {
    step(by: 1)
  }

  override func accessibilityDecrement() {
    step(by: -1)
  }

  private func step(by delta: Int) {
    guard !entries.isEmpty else { return }

    // The first adjustment lands on the first letter rather than stepping past it: there is no
    // "current" stop yet, and starting at index 0 and immediately incrementing would make `A`
    // unreachable without also scrolling back.
    let next: Int
    if let section = accessibilitySection,
      let current = entries.firstIndex(where: { $0.sectionIndex == section })
    {
      next = min(max(current + delta, 0), entries.count - 1)
      guard next != current else { return }
    } else {
      next = delta > 0 ? 0 : entries.count - 1
    }

    let entry = entries[next]
    accessibilitySection = entry.sectionIndex
    accessibilityValue = entry.title
    UIAccessibility.post(notification: .announcement, argument: entry.title)
    onSelect?(entry.sectionIndex)
  }

  private var accessibilitySection: Int?
}

/**
 * The magnified letter shown beside the finger while scrubbing.
 *
 * Added to the collection view's *superview* rather than to the bar: the bar is ~22pt wide and
 * the callout is ~54pt, so anything hosted inside would be clipped or would have to disable
 * clipping on an ancestor that has other reasons to keep it.
 */
@MainActor
private final class CalloutView: UIView {
  private let label = UILabel()

  init() {
    super.init(frame: .zero)

    // `.systemThickMaterial` rather than a flat fill so the letter stays legible over both a
    // white row and a dark one, which is the whole reason the system callout is a material.
    let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemThickMaterial))
    blur.translatesAutoresizingMaskIntoConstraints = false
    addSubview(blur)

    label.font = .systemFont(ofSize: 28, weight: .semibold)
    label.textAlignment = .center
    label.textColor = .label
    label.translatesAutoresizingMaskIntoConstraints = false
    blur.contentView.addSubview(label)

    NSLayoutConstraint.activate([
      blur.topAnchor.constraint(equalTo: topAnchor),
      blur.leadingAnchor.constraint(equalTo: leadingAnchor),
      blur.trailingAnchor.constraint(equalTo: trailingAnchor),
      blur.bottomAnchor.constraint(equalTo: bottomAnchor),
      label.centerXAnchor.constraint(equalTo: blur.contentView.centerXAnchor),
      label.centerYAnchor.constraint(equalTo: blur.contentView.centerYAnchor),
    ])

    layer.cornerRadius = 27
    layer.cornerCurve = .continuous
    layer.masksToBounds = true
    // Purely decorative, and it sits under a moving finger — it must never take the touch that
    // is currently driving the bar.
    isUserInteractionEnabled = false
    alpha = 0
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — this view is only created in code")
  }

  func present(in bar: UIView) {
    guard let container = bar.superview else { return }
    container.addSubview(self)
    container.bringSubviewToFront(self)
    UIView.animate(withDuration: 0.12) { self.alpha = 1 }
  }

  func show(title: String, centeredAtY y: CGFloat, in bar: UIView) {
    guard let container = superview else { return }
    label.text = title

    let side: CGFloat = 54
    let center = bar.convert(CGPoint(x: 0, y: y), to: container)
    frame = CGRect(
      // To the left of the bar, where a right-handed thumb is not covering it.
      x: bar.frame.minX - side - 12,
      y: min(max(center.y - side / 2, 0), container.bounds.height - side),
      width: side,
      height: side
    )
  }

  func dismiss() {
    UIView.animate(
      withDuration: 0.18,
      animations: { self.alpha = 0 },
      completion: { finished in
        // Guarded: a new drag can begin inside the fade, and removing the view then would
        // leave the next `show` writing into a detached frame.
        if finished, self.alpha == 0 { self.removeFromSuperview() }
      }
    )
  }
}

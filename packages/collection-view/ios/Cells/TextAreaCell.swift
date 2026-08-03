import UIKit

/**
 * A row containing a `UITextView` whose height follows its content.
 *
 * The self-sizing works because `isScrollEnabled = false` makes a `UITextView` report its full text
 * height as its intrinsic content size, and the cell's `contentView` is laid out with Auto Layout —
 * so an `.estimated` item dimension resolves against `systemLayoutSizeFitting`. Leaving scrolling
 * on would peg the intrinsic height at whatever the frame happened to be.
 *
 * What Auto Layout does *not* do is tell the collection view that the answer changed. A growing
 * text view invalidates its own intrinsic size, and the cell re-measures, but the layout keeps the
 * height it already computed — so the text runs past the cell's bottom edge. `onHeightChange` is
 * what closes that loop; the host invalidates just this item.
 */
final class TextAreaCell: UICollectionViewListCell, UITextViewDelegate {
  let textView = UITextView()
  private let placeholderLabel = UILabel()
  private var maxHeightConstraint: NSLayoutConstraint?
  private var minimumHeightConstraint: NSLayoutConstraint!
  private var lastReportedHeight: CGFloat = 0

  var onChange: ((String) -> Void)?
  var onFocusChange: ((Bool) -> Void)?
  /// Called when the intrinsic height changed, so the owner can invalidate this item's layout.
  var onHeightChange: (() -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)

    textView.isScrollEnabled = false
    textView.delegate = self
    textView.backgroundColor = .clear
    // A `UITextView` carries its own default padding, which makes it sit a few points off from
    // every stock label in the list. Zeroing both is what lines the first character up with the
    // labels above and below it.
    // Vertical padding split above and below the text — this is what centres a single line inside
    // the height the row reserves, so a one-line notes field matches the title field above it.
    // Horizontal stays zero so the first character lines up with every stock label in the list.
    textView.textContainerInset = UIEdgeInsets(
      top: TextInputTraits.verticalPadding,
      left: 0,
      bottom: TextInputTraits.verticalPadding,
      right: 0
    )
    textView.textContainer.lineFragmentPadding = 0
    textView.translatesAutoresizingMaskIntoConstraints = false
    contentView.addSubview(textView)

    // `UITextView` has no placeholder, so this is a plain label behind it — shown and hidden on
    // every text change, which is the standard approach and cheaper than an attributed-string dance.
    placeholderLabel.numberOfLines = 1
    placeholderLabel.textColor = .placeholderText
    placeholderLabel.translatesAutoresizingMaskIntoConstraints = false
    contentView.addSubview(placeholderLabel)

    let guide = contentView.layoutMarginsGuide
    minimumHeightConstraint = textView.heightAnchor.constraint(
      greaterThanOrEqualToConstant: TextInputTraits.minimumContentHeight(
        for: .preferredFont(forTextStyle: .body)
      )
    )
    NSLayoutConstraint.activate([
      textView.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
      textView.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
      textView.topAnchor.constraint(equalTo: guide.topAnchor),
      textView.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
      placeholderLabel.leadingAnchor.constraint(equalTo: textView.leadingAnchor),
      placeholderLabel.trailingAnchor.constraint(lessThanOrEqualTo: textView.trailingAnchor),
      // Offset by the same inset the text carries, or the placeholder sits above the text it stands
      // in for and the row appears to jump when the first character is typed.
      placeholderLabel.topAnchor.constraint(
        equalTo: textView.topAnchor,
        constant: TextInputTraits.verticalPadding
      ),
      minimumHeightConstraint,
    ])
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  func configure(
    row: RowSpec,
    labelFont: UIFont,
    labelColor: UIColor?,
    tint: UIColor?
  ) {
    textView.font = labelFont
    textView.textColor = labelColor ?? .label
    textView.isEditable = row.disabled != true
    textView.keyboardType = TextInputTraits.keyboardType(row.keyboardType)
    textView.autocapitalizationType = TextInputTraits.autocapitalization(row.autoCapitalize)
    textView.returnKeyType = TextInputTraits.returnKey(row.returnKeyType)
    textView.tintColor = tint

    placeholderLabel.font = labelFont
    placeholderLabel.text = row.placeholder
    // Kept in step with the single-line field at whatever font this row ended up with.
    minimumHeightConstraint.constant = TextInputTraits.minimumContentHeight(for: labelFont)

    // Echo-aware, exactly as in `TextFieldCell` — see the long note there. A value JavaScript is
    // merely agreeing with must not overwrite what the user has typed since, or characters are lost.
    let next = row.text ?? ""
    applyText(next)
    placeholderLabel.isHidden = !(textView.text ?? "").isEmpty

    applyMaxLines(row.maxLines, font: labelFont)
    lastReportedHeight = textView.intrinsicContentSize.height
  }

  /**
   * Caps growth, after which the text view scrolls internally.
   *
   * Derived from the font's line height rather than taken as a point value, so the cap means the
   * same thing at every Dynamic Type size — a four-line limit that becomes two lines at the
   * accessibility sizes would be worse than no limit at all.
   */
  private func applyMaxLines(_ maxLines: Int?, font: UIFont) {
    guard let maxLines, maxLines > 0 else {
      maxHeightConstraint?.isActive = false
      textView.isScrollEnabled = false
      return
    }

    let cap = font.lineHeight * CGFloat(maxLines) + TextInputTraits.verticalPadding * 2
    if let existing = maxHeightConstraint {
      existing.constant = cap
      existing.isActive = true
    } else {
      let constraint = textView.heightAnchor.constraint(lessThanOrEqualToConstant: cap)
      constraint.isActive = true
      maxHeightConstraint = constraint
    }
    // Scrolling is enabled only once the content has actually outgrown the cap. Turning it on
    // unconditionally would break self-sizing for the common case of a short note.
    textView.isScrollEnabled = textView.intrinsicContentSize.height > cap
  }

  /// See `TextFieldCell.applyText` for why an echo has to be recognised rather than applied.
  private func applyText(_ next: String) {
    if textView.isFirstResponder, let index = pendingEchoes.firstIndex(of: next) {
      // `index`, not `index + 1` — the matched value has to survive a second delivery. See
      // `TextFieldCell.applyText`.
      pendingEchoes.removeFirst(index)
      return
    }
    pendingEchoes.removeAll()
    if textView.text != next {
      textView.text = next
    }
  }

  private var pendingEchoes: [String] = []

  func textViewDidChange(_ textView: UITextView) {
    placeholderLabel.isHidden = !textView.text.isEmpty
    pendingEchoes.append(textView.text)
    if pendingEchoes.count > 32 {
      pendingEchoes.removeFirst(pendingEchoes.count - 32)
    }
    onChange?(textView.text)

    // Only when the height genuinely moved. Invalidating the layout on every keystroke would
    // re-measure the whole section for a change that usually is not one.
    let height = textView.intrinsicContentSize.height
    if abs(height - lastReportedHeight) > 0.5 {
      lastReportedHeight = height
      onHeightChange?()
    }
  }

  func textViewDidBeginEditing(_ textView: UITextView) {
    onFocusChange?(true)
  }

  func textViewDidEndEditing(_ textView: UITextView) {
    onFocusChange?(false)
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    onChange = nil
    onFocusChange = nil
    onHeightChange = nil
    pendingEchoes.removeAll()
  }
}

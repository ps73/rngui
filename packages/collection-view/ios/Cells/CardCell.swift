import UIKit

/**
 * A rich stacked cell: a tinted title with an optional symbol, a prominent value, and a caption.
 *
 * The recyclable counterpart to `HostCell`. Both put arbitrary-looking content in a row, but only
 * one of them pools: a card is *described* by a `RowSpec`, so fifty of them cost fifty reused cells,
 * whereas fifty hosted rows are fifty live React subtrees. That trade is the reason this exists at
 * all — `Host` remains the answer for genuinely one-off content like a chart.
 *
 * Deliberately **not** a general content-node tree. A node DSL sits between these two and is
 * speculative until a screen needs something this shape cannot express; the two ends of the range
 * are covered, and the middle can be designed against a real requirement rather than a guess.
 */
final class CardCell: UICollectionViewListCell {
  private let icon = UIImageView()
  private let title = UILabel()
  private let value = UILabel()
  private let caption = UILabel()
  private let titleRow = UIStackView()
  private let stack = UIStackView()

  override init(frame: CGRect) {
    super.init(frame: frame)

    icon.contentMode = .scaleAspectFit
    icon.setContentHuggingPriority(.required, for: .horizontal)
    icon.setContentCompressionResistancePriority(.required, for: .horizontal)

    title.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
    value.numberOfLines = 0
    caption.numberOfLines = 0
    caption.textColor = .secondaryLabel

    titleRow.axis = .horizontal
    titleRow.spacing = 6
    titleRow.alignment = .firstBaseline
    titleRow.addArrangedSubview(icon)
    titleRow.addArrangedSubview(title)

    stack.axis = .vertical
    stack.spacing = 6
    stack.alignment = .fill
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.addArrangedSubview(titleRow)
    stack.addArrangedSubview(value)
    stack.addArrangedSubview(caption)
    contentView.addSubview(stack)

    // The layout margins guide, so a card lines up with every stock row in the same list rather
    // than inventing its own inset.
    let guide = contentView.layoutMarginsGuide
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
      stack.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
      stack.topAnchor.constraint(equalTo: guide.topAnchor),
      stack.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
    ])
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  func configure(
    row: RowSpec,
    titleFont: UIFont,
    valueFont: UIFont,
    captionFont: UIFont,
    tint: UIColor?,
    labelColor: UIColor?
  ) {
    let accent = tint ?? .tintColor

    title.text = row.label
    title.font = titleFont
    title.textColor = accent
    // Hidden rather than left empty, so the vertical stack collapses the gap too.
    titleRow.isHidden = (row.label ?? "").isEmpty && row.systemImage == nil

    if let name = row.systemImage, let image = UIImage(systemName: name) {
      icon.image = image
      icon.isHidden = false
    } else {
      icon.image = nil
      icon.isHidden = true
    }
    icon.tintColor = accent

    value.text = row.value
    value.font = valueFont
    value.textColor = labelColor ?? .label
    value.isHidden = (row.value ?? "").isEmpty

    caption.text = row.secondaryLabel
    caption.font = captionFont
    caption.isHidden = (row.secondaryLabel ?? "").isEmpty
  }
}

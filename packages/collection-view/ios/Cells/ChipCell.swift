import UIKit

/**
 * A pill in a horizontally scrolling section.
 *
 * **A first-class cell rather than a `Host` row, and that is the point of the whole feature.** A
 * strip of chips is the case where hosted React children would be worst: they are many, they are
 * interchangeable, and they scroll — precisely the shape UIKit's reuse pool exists for. Described
 * as chips they cost one pooled cell per visible pill; rendered as React subtrees they would cost
 * one subtree per chip, all of them alive at once.
 *
 * The cell self-sizes horizontally: the label's intrinsic width plus the capsule's padding is what
 * `NSCollectionLayoutDimension.estimated` resolves against, so chips are as wide as their text
 * rather than a guessed constant.
 */
final class ChipCell: UICollectionViewCell {
  private let label = UILabel()
  private let icon = UIImageView()
  private let stack = UIStackView()

  override init(frame: CGRect) {
    super.init(frame: frame)

    icon.contentMode = .scaleAspectFit
    icon.setContentHuggingPriority(.required, for: .horizontal)
    label.setContentHuggingPriority(.required, for: .horizontal)
    label.setContentCompressionResistancePriority(.required, for: .horizontal)

    stack.axis = .horizontal
    stack.spacing = 5
    stack.alignment = .center
    stack.isUserInteractionEnabled = false
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.addArrangedSubview(icon)
    stack.addArrangedSubview(label)
    contentView.addSubview(stack)

    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
      stack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
      stack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
    ])

    // A capsule, so the radius follows the height rather than needing to be kept in step with it.
    contentView.layer.cornerCurve = .continuous
    contentView.clipsToBounds = true
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    contentView.layer.cornerRadius = contentView.bounds.height / 2
  }

  func configure(row: RowSpec, font: UIFont, tint: UIColor?, unselected: UIColor) {
    label.text = row.label
    label.font = font

    if let name = row.systemImage, let image = UIImage(systemName: name) {
      icon.image = image
      icon.isHidden = false
    } else {
      // Assigned in both branches: a reused cell arrives holding the previous chip's symbol.
      icon.image = nil
      icon.isHidden = true
    }

    let selected = row.on == true
    let accent = tint ?? .tintColor
    contentView.backgroundColor = selected ? accent : unselected
    let foreground: UIColor = selected ? .white : .label
    label.textColor = foreground
    icon.tintColor = foreground

    isUserInteractionEnabled = row.disabled != true
    contentView.alpha = row.disabled == true ? 0.5 : 1
  }
}

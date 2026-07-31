import UIKit

/**
 * The red count bubble on a Settings row.
 *
 * A live view rather than a rendered image — unlike `IconTile`, which has to be one because it
 * occupies the content configuration's image slot. This is a `.customView` accessory, so it can
 * keep a dynamic `UIColor`, scale its own text with Dynamic Type, and resize when the count goes
 * from one digit to three.
 *
 * The shape follows the content: a circle for a single character, a capsule for more. That is what
 * iOS does, and it falls out of a corner radius tied to the height plus a width that can never drop
 * below it.
 */
final class BadgeView: UIView {
  private let label = UILabel()

  override init(frame: CGRect) {
    super.init(frame: frame)

    label.textColor = .white
    label.textAlignment = .center
    label.font = .preferredFont(forTextStyle: .subheadline)
    label.adjustsFontForContentSizeCategory = true
    label.translatesAutoresizingMaskIntoConstraints = false
    addSubview(label)

    NSLayoutConstraint.activate([
      label.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 7),
      label.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -7),
      label.topAnchor.constraint(equalTo: topAnchor, constant: 2),
      label.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -2),
      // Never narrower than it is tall, which is what makes a single digit a circle rather than a
      // squashed pill.
      widthAnchor.constraint(greaterThanOrEqualTo: heightAnchor),
    ])
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — this view is only created in code")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    layer.cornerRadius = bounds.height / 2
  }

  func configure(text: String, color: UIColor) {
    label.text = text
    backgroundColor = color
  }
}

import UIKit

/**
 * A row with a trailing `UISwitch`.
 *
 * The switch is owned by the cell and reused with it, rather than being rebuilt per configure pass:
 * a fresh `UISwitch` every time would restart its animation and drop the in-flight thumb position
 * mid-drag.
 *
 * `onChange` is reassigned on **every** configure pass, never only when nil. A reused cell arrives
 * holding the previous row's closure, and a closure that reports the wrong row id is the worst kind
 * of bug here — the list updates a row the user never touched, and nothing about the symptom points
 * at recycling.
 */
final class SwitchCell: UICollectionViewListCell {
  let toggle = UISwitch()
  var onChange: ((Bool) -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    toggle.addTarget(self, action: #selector(valueChanged), for: .valueChanged)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  /// Sized once and reused; a `UISwitch` has a fixed intrinsic size.
  var accessory: UICellAccessory {
    .customView(
      configuration: .init(customView: toggle, placement: .trailing(displayed: .always))
    )
  }

  @objc private func valueChanged() {
    onChange?(toggle.isOn)
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    // Dropped explicitly rather than relying on the next configure pass to overwrite it. Between
    // reuse and reconfiguration the cell is briefly live in the hierarchy, and a stray
    // `valueChanged` in that window would report against whichever row used it last.
    onChange = nil
  }
}

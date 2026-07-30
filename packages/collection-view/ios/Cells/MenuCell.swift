import UIKit

/**
 * A row whose trailing control is a `UIButton` presenting a `UIMenu` — the iOS Settings picker.
 *
 * A button with `showsMenuAsPrimaryAction`, not a row that pushes a screen and not a
 * `UIPickerView`. That is what Settings actually uses, and it gets the anchoring, the dismissal, the
 * checkmark on the current item, and the accessibility behaviour from UIKit rather than from here.
 *
 * The menu is rebuilt on every configure pass. It has to be: a `UIMenu` is immutable, and the
 * checkmark lives in the menu's own state, so a menu built once would keep showing the first
 * selection forever.
 */
final class MenuCell: UICollectionViewListCell {
  let button = UIButton(type: .system)
  var onSelect: ((String) -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    button.showsMenuAsPrimaryAction = true
    // Lets the title shrink rather than shove the leading label off the row.
    button.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  var accessory: UICellAccessory {
    .customView(
      configuration: .init(customView: button, placement: .trailing(displayed: .always))
    )
  }

  func configure(row: RowSpec, font: UIFont, tint: UIColor?) {
    let items = row.menuItems ?? []
    let selected = items.first { $0.id == row.selectedItemId }

    var configuration = UIButton.Configuration.plain()
    configuration.title = selected?.title ?? ""
    configuration.baseForegroundColor = tint ?? .secondaryLabel
    // The up/down chevron pair, which is what marks a control as a menu rather than a link. The
    // single disclosure chevron would promise a pushed screen.
    configuration.image = UIImage(systemName: "chevron.up.chevron.down")
    configuration.imagePlacement = .trailing
    configuration.imagePadding = 4
    configuration.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(scale: .small)
    configuration.contentInsets = .zero
    configuration.titleTextAttributesTransformer = .init { incoming in
      var outgoing = incoming
      outgoing.font = font
      return outgoing
    }
    button.configuration = configuration
    button.isEnabled = row.disabled != true

    button.menu = UIMenu(
      children: items.map { item in
        UIAction(
          title: item.title,
          image: item.systemImage.flatMap { UIImage(systemName: $0) },
          attributes: menuAttributes(for: item),
          // The checkmark UIKit draws next to the current choice. Driven from the descriptor, so
          // it always agrees with what JavaScript thinks is selected.
          state: item.id == row.selectedItemId ? .on : .off,
          handler: { [weak self] _ in self?.onSelect?(item.id) }
        )
      }
    )
  }

  private func menuAttributes(for item: MenuItemSpec) -> UIMenuElement.Attributes {
    var attributes: UIMenuElement.Attributes = []
    if item.destructive == true { attributes.insert(.destructive) }
    if item.disabled == true { attributes.insert(.disabled) }
    return attributes
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    onSelect = nil
    // The menu closes over `onSelect` indirectly through `self`, but it also closes over the *item
    // ids* of whichever row used this cell last. Left in place, a menu presented in the window
    // between reuse and reconfiguration would offer the previous row's options.
    button.menu = nil
  }
}

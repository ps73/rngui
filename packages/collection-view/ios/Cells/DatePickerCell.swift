import UIKit

/**
 * A row containing a `UIDatePicker`.
 *
 * **Two registrations, not one**, and the split is not cosmetic. A `compact` picker is a small
 * trailing pill sharing the row with a label; an `inline` or `wheels` picker is a tall control
 * filling the row on its own. `preferredDatePickerStyle` cannot be changed once a picker has been
 * laid out in one of those shapes without UIKit leaving the old intrinsic size behind, so the two
 * must not share a reuse pool. The host builds `compactDateRegistration` and
 * `expandedDateRegistration` separately for that reason.
 *
 * The Reminders pattern — a switch that reveals a picker — needs nothing from this class. The
 * picker row is inserted and removed by JavaScript rendering it conditionally, and the diffable
 * data source animates it.
 */
final class DatePickerCell: UICollectionViewListCell {
  let picker = UIDatePicker()
  private let label = UILabel()
  private var isCompact = true
  private var installedConstraints: [NSLayoutConstraint] = []

  var onChange: ((Double) -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    picker.addTarget(self, action: #selector(valueChanged), for: .valueChanged)
    picker.translatesAutoresizingMaskIntoConstraints = false
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  /**
   * Installs the picker for the given shape. Called once per cell, from the registration that owns
   * that shape — never switched afterwards.
   */
  func install(compact: Bool) {
    guard installedConstraints.isEmpty else { return }
    isCompact = compact

    let guide = contentView.layoutMarginsGuide
    if compact {
      // Shares the row with a leading label. The picker keeps its intrinsic width and the label
      // takes the rest.
      label.translatesAutoresizingMaskIntoConstraints = false
      contentView.addSubview(label)
      contentView.addSubview(picker)
      installedConstraints = [
        label.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
        label.centerYAnchor.constraint(equalTo: guide.centerYAnchor),
        picker.leadingAnchor.constraint(
          greaterThanOrEqualTo: label.trailingAnchor, constant: 8
        ),
        picker.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
        picker.topAnchor.constraint(equalTo: guide.topAnchor),
        picker.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
        picker.heightAnchor.constraint(greaterThanOrEqualToConstant: 30),
      ]
    } else {
      // Fills the row. The picker's own intrinsic height drives the cell, which is what makes the
      // calendar and the drum size themselves.
      contentView.addSubview(picker)
      installedConstraints = [
        picker.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
        picker.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
        picker.topAnchor.constraint(equalTo: guide.topAnchor),
        picker.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
      ]
    }
    NSLayoutConstraint.activate(installedConstraints)
  }

  func configure(row: RowSpec, font: UIFont, labelColor: UIColor?, tint: UIColor?) {
    picker.datePickerMode = Self.mode(row.datePickerMode)
    picker.preferredDatePickerStyle = Self.style(row.datePickerStyle)
    picker.isEnabled = row.disabled != true
    picker.tintColor = tint

    // Set before the date, so a value outside the range is clamped by UIKit rather than silently
    // rejected — assigning a date first and the bounds second leaves the picker showing something
    // the bounds forbid.
    picker.minimumDate = row.minDateMillis.map { Date(timeIntervalSince1970: $0 / 1000) }
    picker.maximumDate = row.maxDateMillis.map { Date(timeIntervalSince1970: $0 / 1000) }

    if let millis = row.dateMillis {
      let date = Date(timeIntervalSince1970: millis / 1000)
      // Only when it differs, and by more than a second. A `wheels` picker mid-spin would otherwise
      // be yanked back to the value JavaScript last echoed, and sub-second drift from the
      // millisecond round trip would make that fire constantly.
      if abs(picker.date.timeIntervalSince(date)) >= 1 {
        picker.date = date
      }
    }

    if isCompact {
      label.text = row.label
      label.font = font
      label.textColor = row.disabled == true ? .secondaryLabel : (labelColor ?? .label)
      label.isHidden = (row.label ?? "").isEmpty
    }
  }

  private static func mode(_ mode: DatePickerMode?) -> UIDatePicker.Mode {
    switch mode {
    case .time: return .time
    case .dateAndTime: return .dateAndTime
    case .date, .unknown, nil: return .date
    }
  }

  private static func style(_ style: DatePickerStyle?) -> UIDatePickerStyle {
    switch style {
    case .inline: return .inline
    case .wheels: return .wheels
    case .compact, .unknown, nil: return .compact
    }
  }

  /// True when this style fills the row rather than sharing it with a label.
  static func isExpanded(_ style: DatePickerStyle?) -> Bool {
    switch style {
    case .inline, .wheels: return true
    case .compact, .unknown, nil: return false
    }
  }

  @objc private func valueChanged() {
    onChange?(picker.date.timeIntervalSince1970 * 1000)
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    onChange = nil
  }
}

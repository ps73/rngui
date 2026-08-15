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
  let picker = RowDatePicker()
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
      // The pill takes the same font as the label beside it — which is what `TextFieldCell` does
      // for its field, and what Android's date row has always done. Applied here as an early
      // opportunity, not as the mechanism: the pill's own labels may not exist until the cell is in
      // a window, so `RowDatePicker` re-applies from its layout pass.
      picker.overrideFont = font
      picker.applyOverrideFont()
    } else {
      // Assigned in both branches, so a recycled cell can never keep the previous row's font.
      picker.overrideFont = nil
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

/**
 * A `UIDatePicker` that keeps the font its row asked for.
 *
 * **`UIDatePicker` has no font API** — no property, no content configuration, no appearance
 * attribute that reaches the text in the compact pill. It is drawn by labels UIKit owns, so the
 * row's font is pushed onto whatever `UILabel`s the pill turns out to be built from.
 *
 * **This is the one place in the library that assumes anything about a system control's internals,
 * and the assumption is about shape rather than about symbols.** `UIView.subviews` and `UILabel.font`
 * are both public; nothing here uses a private selector or reads an ivar by key. If a future iOS
 * draws the pill some other way, the walk finds no label, nothing is assigned, and the picker keeps
 * UIKit's own font — a row that looks stock is the failure mode. That is what makes this preferable
 * to the two alternatives: an appearance proxy is process-global and so cannot express the per-row
 * fonts `RowSpec.font` exists for, and drawing our own pill over an invisible picker would mean
 * reimplementing UIKit's date formatting to produce a control no iOS user has seen.
 *
 * **Compact only.** `inline` and `wheels` draw dozens of labels — calendar days, drum rows — that
 * UIKit recycles as they scroll, and restyling those is that same control-nobody-has-seen trade.
 *
 * **Family and weight only.** A `size` in the row's font is deliberately not applied here; see
 * `apply(_:in:depth:)`.
 */
final class RowDatePicker: UIDatePicker {
  /// Nil for the styles this does not touch, which is what keeps `layoutSubviews` free for them.
  var overrideFont: UIFont?

  // The pill's labels are created and re-created by UIKit — on first display, after the date
  // changes, when the calendar popover dismisses — and none of those lays out the *cell*. Hooking
  // the picker's own pass is what makes the override survive all three without needing a
  // notification for each.
  override func layoutSubviews() {
    super.layoutSubviews()
    applyOverrideFont()
  }

  func applyOverrideFont() {
    guard let overrideFont, preferredDatePickerStyle == .compact else { return }
    apply(overrideFont, in: self, depth: 0)
  }

  private func apply(_ font: UIFont, in view: UIView, depth: Int) {
    // The pill's text sits a couple of levels down. The bound is not needed for today's hierarchy;
    // it is what stops a future one from turning this into a walk of an arbitrarily deep subtree on
    // every layout pass.
    guard depth <= 4 else { return }
    for subview in view.subviews {
      if let label = subview as? UILabel {
        // **The face travels, the size does not.** The pill's rounded background is laid out by
        // UIKit to metrics it chose, and it does not grow to fit metrics it did not — so honouring
        // a `size` here buys a caller clipped or re-abbreviated text (`15. Aug 2026` becomes
        // `15.08…`) in exchange for a number they cannot see the effect of until they try it.
        // Family, weight and variations are the parts that fit any size, so those are what carry
        // over; `size` on a compact picker is documented as ignored, and Dynamic Type — which the
        // picker already follows on its own — is how the pill gets bigger.
        let target = font.withSize(label.font?.pointSize ?? font.pointSize)
        // **Only when it differs, and this is the load-bearing line.** Assigning `font` invalidates
        // the label's intrinsic size, which marks its ancestors as needing layout, which calls this
        // method again — unconditional, that is a layout loop that spins for as long as the row is
        // on screen. The second pass finding the font already correct is what terminates it, and
        // it terminates because `target` is derived from a size this never changes.
        if label.font != target {
          label.font = target
        }
      }
      apply(font, in: subview, depth: depth + 1)
    }
  }
}

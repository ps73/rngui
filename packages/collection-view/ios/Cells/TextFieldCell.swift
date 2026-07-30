import UIKit

/**
 * A row containing a single-line `UITextField`.
 *
 * Two shapes, chosen by whether the row has a label: with one, the label leads and the field
 * trails, right-aligned — the Settings Wi-Fi-password look. Without one, the field fills the row —
 * the Reminders title look.
 *
 * **The reason this cell exists at all rather than being a `Host` row** is the first responder.
 * `reconfigureItems` keeps the cell instance alive across a re-render, so a field being typed into
 * keeps focus and caret; a React child would be torn down and remounted. What that requires from
 * the configure pass is the `text` guard in `configure(...)` — see below.
 */
final class TextFieldCell: UICollectionViewListCell, UITextFieldDelegate {
  let field = UITextField()
  private let label = UILabel()
  private let stack = UIStackView()

  var onChange: ((String) -> Void)?
  var onFocusChange: ((Bool) -> Void)?
  private var minimumHeightConstraint: NSLayoutConstraint!

  override init(frame: CGRect) {
    super.init(frame: frame)

    label.setContentCompressionResistancePriority(.required, for: .horizontal)
    // The field yields before the label does, so a long label never gets truncated to make room
    // for an empty field.
    field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
    field.delegate = self
    field.addTarget(self, action: #selector(editingChanged), for: .editingChanged)

    stack.axis = .horizontal
    stack.spacing = 8
    stack.alignment = .center
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.addArrangedSubview(label)
    stack.addArrangedSubview(field)
    contentView.addSubview(stack)

    // Pinned to the layout margins guide, not the bounds: that guide is what carries the list
    // style's own leading and trailing insets, so the text lines up with every stock cell around
    // it without hard-coding a number that differs per appearance.
    let guide = contentView.layoutMarginsGuide
    minimumHeightConstraint = stack.heightAnchor.constraint(
      greaterThanOrEqualToConstant: TextInputTraits.minimumContentHeight(
        for: .preferredFont(forTextStyle: .body)
      )
    )
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
      stack.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
      stack.topAnchor.constraint(equalTo: guide.topAnchor),
      stack.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
      // Matches the stock list row, so a text row in a group does not stand out as shorter.
      contentView.heightAnchor.constraint(greaterThanOrEqualTo: guide.heightAnchor, constant: 0),
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
    let hasLabel = !(row.label ?? "").isEmpty
    label.isHidden = !hasLabel
    label.text = row.label
    label.font = labelFont
    label.textColor = row.disabled == true ? .secondaryLabel : (labelColor ?? .label)

    // Right-aligned only when sharing the row with a label. Filling the row it reads as body text
    // and should start at the leading edge.
    field.textAlignment = hasLabel ? .right : .natural
    field.font = labelFont
    field.textColor = labelColor ?? .label
    field.placeholder = row.placeholder
    field.isSecureTextEntry = row.secure ?? false
    field.isEnabled = row.disabled != true
    field.keyboardType = TextInputTraits.keyboardType(row.keyboardType)
    field.autocapitalizationType = TextInputTraits.autocapitalization(row.autoCapitalize)
    field.returnKeyType = TextInputTraits.returnKey(row.returnKeyType)
    field.tintColor = tint
    minimumHeightConstraint.constant = TextInputTraits.minimumContentHeight(for: labelFont)

    applyText(row.text ?? "")
  }

  /**
   * Writes the descriptor's text into the field without ever losing a keystroke.
   *
   * The naive version — assign whenever it differs — drops characters, and the mechanism is worth
   * spelling out because it is not obvious and it *looks* correct. Each keystroke goes to JavaScript
   * asynchronously. Type `ab` quickly and the sequence is: field holds `ab`; the commit carrying
   * `a` arrives; `"ab" != "a"` so the field is assigned `"a"`; the `b` is gone. Measured typing
   * "Rehearsal on Thursday" into the naive version: 12 of 21 characters survived.
   *
   * So every value sent to JavaScript is remembered, and an incoming value that is one of those is
   * recognised as an **echo** and ignored — it is JavaScript agreeing with something the field
   * already knows, possibly something it has since moved past. Anything else is a genuine
   * instruction (a clear, an input mask, a value set from elsewhere) and is applied.
   *
   * This is the same problem React Native's own `TextInput` solves with `eventCount`; the pending
   * list is the same idea without needing a counter to ride in the tree.
   */
  private func applyText(_ next: String) {
    if field.isFirstResponder, let index = pendingEchoes.firstIndex(of: next) {
      // Everything up to and including this value is now accounted for. Older entries can go: a
      // commit can never arrive out of order, so they will never be echoed again.
      pendingEchoes.removeFirst(index + 1)
      return
    }
    pendingEchoes.removeAll()
    // Still guarded: assigning `text` resets `selectedTextRange`, which throws the caret to the end.
    if field.text != next {
      field.text = next
    }
  }

  /// Values sent to JavaScript and not yet echoed back. Bounded — a burst of typing is short, and an
  /// unbounded list would grow for the lifetime of a field that is never echoed at all.
  private var pendingEchoes: [String] = []

  @objc private func editingChanged() {
    let text = field.text ?? ""
    pendingEchoes.append(text)
    if pendingEchoes.count > 32 {
      pendingEchoes.removeFirst(pendingEchoes.count - 32)
    }
    onChange?(text)
  }

  func textFieldDidBeginEditing(_ textField: UITextField) {
    onFocusChange?(true)
  }

  func textFieldDidEndEditing(_ textField: UITextField) {
    onFocusChange?(false)
  }

  func textFieldShouldReturn(_ textField: UITextField) -> Bool {
    textField.resignFirstResponder()
    return true
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    onChange = nil
    onFocusChange = nil
    // Belongs to the row that just left. Kept, it would make the next row's first legitimate value
    // look like an echo and be ignored.
    pendingEchoes.removeAll()
  }
}

/// Shared by both text cells — the same four mappings, and no reason for two copies to drift.
enum TextInputTraits {
  /**
   * The minimum content height a text row reserves, so a single-line `TextArea` is exactly as tall
   * as a `TextField`.
   *
   * They diverged at first because the two controls are laid out differently: a `UITextField`
   * centres its text in whatever height it is given, while a `UITextView` top-aligns its text and
   * reports only the text's own height. Left to their intrinsic sizes, a one-line notes field came
   * out visibly shorter than the title field above it, which reads as a broken row rather than as a
   * different control.
   *
   * Derived from the font rather than fixed, so the two stay matched at every Dynamic Type size.
   */
  static func minimumContentHeight(for font: UIFont) -> CGFloat {
    max(30, font.lineHeight + verticalPadding * 2)
  }

  /// Split above and below the text of a `UITextView`, which is what centres a single line in the
  /// height `minimumContentHeight` reserves.
  static let verticalPadding: CGFloat = 5

  static func keyboardType(_ kind: KeyboardType?) -> UIKeyboardType {
    switch kind {
    case .numeric: return .numberPad
    case .decimal: return .decimalPad
    case .email: return .emailAddress
    case .phone: return .phonePad
    case .url: return .URL
    case .asciiCapable: return .asciiCapable
    case .default, .unknown, nil: return .default
    }
  }

  static func autocapitalization(_ kind: AutoCapitalize?) -> UITextAutocapitalizationType {
    // Defaulted *before* the switch, not inside it. `AutoCapitalize` has its own `none` case, so
    // over an `AutoCapitalize?` the pattern `case .none` binds to `Optional.none` instead — which
    // leaves the real `.none` unhandled and makes the switch inexhaustive. Unwrapping first is what
    // removes the ambiguity rather than working around it.
    switch kind ?? .sentences {
    case .none: return .none
    case .words: return .words
    case .characters: return .allCharacters
    case .sentences, .unknown: return .sentences
    }
  }

  static func returnKey(_ kind: ReturnKeyType?) -> UIReturnKeyType {
    switch kind {
    case .done: return .done
    case .go: return .go
    case .next: return .next
    case .search: return .search
    case .send: return .send
    case .default, .unknown, nil: return .default
    }
  }
}

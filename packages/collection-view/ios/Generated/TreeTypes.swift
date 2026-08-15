// Generated from src/tree.ts by scripts/gen-swift-types.mjs. Do not edit.
//
// Run `npm run gen:swift-types` after changing the descriptor types. The output is committed
// on purpose: `pod install` must not require Node, and a schema change should be visible in a
// diff.
//
// `npm run verify` regenerates and fails on any diff, so a stale copy cannot pass it. Nothing
// runs that automatically — this repository has no CI — so it is a check somebody has to invoke.
//
// Every field decodes leniently. `JSONDecoder` fails an entire payload on one unknown enum
// case or missing key, and `expo-updates` can ship a JS bundle newer than the native binary
// it runs against — so an unrecognised value has to degrade rather than blank the list.

import Foundation

/// Generated from `RowKind` in tree.ts.
enum RowKind: String, Decodable {
  case `default`
  case value
  case subtitle
  case host
  case `switch`
  case textField
  case textArea
  case button
  case menu
  case datePicker
  case slider
  case card
  case chip
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = RowKind(rawValue: raw) ?? .unknown
  }
}

/// Generated from `AccessoryKind` in tree.ts.
enum AccessoryKind: String, Decodable {
  case none
  case disclosure
  case checkmark
  case checkbox
  case radio
  case spinner
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = AccessoryKind(rawValue: raw) ?? .unknown
  }
}

/// Generated from `KeyboardType` in tree.ts.
enum KeyboardType: String, Decodable {
  case `default`
  case numeric
  case decimal
  case email
  case phone
  case url
  case asciiCapable
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = KeyboardType(rawValue: raw) ?? .unknown
  }
}

/// Generated from `AutoCapitalize` in tree.ts.
enum AutoCapitalize: String, Decodable {
  case none
  case sentences
  case words
  case characters
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = AutoCapitalize(rawValue: raw) ?? .unknown
  }
}

/// Generated from `ReturnKeyType` in tree.ts.
enum ReturnKeyType: String, Decodable {
  case `default`
  case done
  case go
  case next
  case search
  case send
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = ReturnKeyType(rawValue: raw) ?? .unknown
  }
}

/// Generated from `DatePickerMode` in tree.ts.
enum DatePickerMode: String, Decodable {
  case date
  case time
  case dateAndTime
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = DatePickerMode(rawValue: raw) ?? .unknown
  }
}

/// Generated from `DatePickerStyle` in tree.ts.
enum DatePickerStyle: String, Decodable {
  case compact
  case inline
  case wheels
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = DatePickerStyle(rawValue: raw) ?? .unknown
  }
}

/// Generated from `ButtonRole` in tree.ts.
enum ButtonRole: String, Decodable {
  case `default`
  case destructive
  case plain
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = ButtonRole(rawValue: raw) ?? .unknown
  }
}

/// Generated from `SwipeActionStyle` in tree.ts.
enum SwipeActionStyle: String, Decodable {
  case normal
  case destructive
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = SwipeActionStyle(rawValue: raw) ?? .unknown
  }
}

/// Generated from `HeaderBackgroundStyle` in tree.ts.
enum HeaderBackgroundStyle: String, Decodable {
  case opaque
  case blurred
  case soft
  case transparent
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = HeaderBackgroundStyle(rawValue: raw) ?? .unknown
  }
}

/// Generated from `SectionLayout` in tree.ts.
enum SectionLayout: String, Decodable {
  case list
  case chips
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = SectionLayout(rawValue: raw) ?? .unknown
  }
}

/// Generated from `HostBackground` in tree.ts.
enum HostBackground: String, Decodable {
  case none
  case card
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = HostBackground(rawValue: raw) ?? .unknown
  }
}

/// Generated from `ListAppearance` in tree.ts.
enum ListAppearance: String, Decodable {
  case insetGrouped
  case grouped
  case plain
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = ListAppearance(rawValue: raw) ?? .unknown
  }
}

/// Generated from `AndroidListStyle` in tree.ts.
enum AndroidListStyle: String, Decodable {
  case standard
  case segmented
  /// A value this binary does not recognise.
  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = AndroidListStyle(rawValue: raw) ?? .unknown
  }
}

/// One entry in a `menu` row's `UIMenu`.
struct MenuItemSpec: Decodable, Equatable {
  var id: String = ""
  var title: String = ""
  /// SF Symbol name, e.g. `exclamationmark.2`.
  var systemImage: String?
  var destructive: Bool?
  var disabled: Bool?

  private enum CodingKeys: String, CodingKey {
    case id, title, systemImage, destructive, disabled
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
    title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
    systemImage = try container.decodeIfPresent(String.self, forKey: .systemImage)
    destructive = try container.decodeIfPresent(Bool.self, forKey: .destructive)
    disabled = try container.decodeIfPresent(Bool.self, forKey: .disabled)
  }
}

/// One button revealed by swiping a row.
///
/// Lives on the row rather than being a separate concept because it is configured through the
/// *layout* — `UICollectionLayoutListConfiguration.trailingSwipeActionsConfigurationProvider` —
/// which is handed an index path and has to answer from the row's own descriptor.
struct SwipeActionSpec: Decodable, Equatable {
  var id: String = ""
  var title: String?
  /// SF Symbol name. Shown instead of the title when both are set, as UIKit prefers.
  var systemImage: String?
  var style: SwipeActionStyle?
  /// Overrides the style's own colour. Normalised to `#RRGGBBAA` before crossing.
  var backgroundColor: String?

  private enum CodingKeys: String, CodingKey {
    case id, title, systemImage, style, backgroundColor
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
    title = try container.decodeIfPresent(String.self, forKey: .title)
    systemImage = try container.decodeIfPresent(String.self, forKey: .systemImage)
    style = try container.decodeIfPresent(SwipeActionStyle.self, forKey: .style)
    backgroundColor = try container.decodeIfPresent(String.self, forKey: .backgroundColor)
  }
}

/// A single row.
struct RowSpec: Decodable, Equatable {
  /// Globally unique — not just unique within its section.
  ///
  /// These become `UICollectionViewDiffableDataSource` item identifiers, and a repeated one is
  /// **fatal**: `appendItems` raises `NSInternalInconsistencyException`, *"Fatal: supplied item
  /// identifiers are not unique"*. Native deduplicates before building the snapshot so malformed
  /// input degrades to a missing row rather than a dead app, and the serializer reports the
  /// collision under `__DEV__` where the offending call site is visible.
  var id: String = ""
  var kind: RowKind = .`default`
  var label: String?
  /// Second line, for `subtitle` rows, and the caption of a `card`.
  var secondaryLabel: String?
  /// Trailing detail text, for `value` rows.
  var value: String?
  var accessory: AccessoryKind?
  /// A leading SF Symbol — the calendar and clock glyphs on Reminders' Date and Time rows.
  ///
  /// A symbol name rather than an image source: these are glyphs from the system set, they scale
  /// with Dynamic Type for free, and they take the row's tint without an asset pipeline.
  var systemImage: String?
  /// An explicit Material Symbol name, for Android.
  ///
  /// The one deliberate platform-specific field in this file, and the escape hatch for the fact
  /// that `systemImage` cannot be mapped completely: SF Symbols and Material Symbols overlap in
  /// meaning but never in naming, so native carries a curated map and an unmapped name renders
  /// nothing. Setting this names the Android glyph directly, and wins over `systemImage` where both
  /// are set — an escape hatch that loses to the thing it overrides is not one.
  ///
  /// Ignored on iOS. Additive, so it is safe under rule 2 above: a binary that predates it simply
  /// does not read the key.
  var materialSymbol: String?
  /// Overrides the glyph's colour. Normalised to `#RRGGBBAA` before crossing.
  var imageColor: String?
  /// Fills the platform's icon container behind the glyph and draws the glyph white.
  ///
  /// **The container's shape is the platform's, not the caller's**: a 29pt rounded square on iOS,
  /// which is Settings' coloured tile, and a 40dp circle on Android, which is M3's leading avatar.
  /// Android has never had the tile — it is Apple's, and drawing it there would be the clearest
  /// possible case of one platform wearing the other's clothes. A screen that wants Pixel Settings
  /// leaves this unset and gets the bare monochrome glyph Pixel Settings actually draws.
  ///
  /// Set, this replaces `imageColor` rather than combining with it: the point of a container is that
  /// the colour is the *background*, and neither platform tints the glyph on top of it. Normalised
  /// to `#RRGGBBAA` before crossing.
  var imageBackground: String?
  /// One or two letters drawn in the container instead of a glyph — a contact's initials.
  ///
  /// The monogram avatar every address book falls back to when a person has no photo, and the one
  /// leading element a symbol set cannot express: it is *derived from the row's own data* rather
  /// than chosen from a fixed vocabulary. Both platforms draw it, in the container shape described
  /// on [imageBackground] above.
  ///
  /// Wins over `systemImage` and `materialSymbol` where both are set, and needs `imageBackground` to
  /// have something to sit in — letters floating unbounded where an icon would be read as a layout
  /// bug, so a monogram without a container draws nothing and warns.
  ///
  /// Truncated to two characters by native. Longer is not a monogram, and silently drawing five
  /// letters squeezed into a 40dp circle would be worse than the truncation.
  var imageMonogram: String?
  /// The glyph's point size, for a bare symbol. Ignored when `imageBackground` is set, where the
  /// container's own size decides.
  var imageSize: Double?
  /// The red count bubble — Settings' unread badge.
  ///
  /// A string rather than a number because these are not always counts: iOS puts version numbers
  /// and a bare `!` in the same bubble, and a caller who has "1" already formatted should not have
  /// to unformat it.
  var badge: String?
  /// The bubble's fill. Defaults to the system red. Normalised to `#RRGGBBAA` before crossing.
  var badgeColor: String?
  /// Draws `secondaryLabel` in the tint colour rather than as grey detail text.
  ///
  /// This is the "Today" / "15:00" under Reminders' Date and Time rows: the tint is what marks the
  /// value as the row's *current setting* and the row as something to tap, rather than as an
  /// explanatory second line.
  var secondaryLabelTinted: Bool?
  /// Overrides the list's font for this row alone, falling back to it field by field.
  ///
  /// What a large title field needs — Reminders' title is set noticeably bigger than the notes
  /// under it, and that is a property of the row rather than of the list.
  var font: FontSpec?
  /// Whether tapping highlights the row and reports a press.
  var selectable: Bool?
  /// Greys the row out and stops it responding.
  ///
  /// Applied to the *control* as well as the label, which is the part that is easy to miss: a
  /// disabled-looking row whose switch still flips is worse than no disabled state at all.
  var disabled: Bool?
  /// Overrides the list tint for this row alone — a destructive button, a highlighted accessory.
  /// Normalised to `#RRGGBBAA` before crossing.
  var tintColor: String?
  /// For `host` rows: which mounted React child belongs in this cell, by mount order.
  var hostIndex: Int?
  /// For `host` rows: whether the cell draws the section's background.
  var hostBackground: HostBackground?
  /// Row height in points.
  ///
  /// For a `host` row this is the space the cell reserves, and it is always a number JavaScript
  /// decided — never something native measured. Fabric lays the child out with Yoga, so the view
  /// has no `intrinsicContentSize` and an `.estimated` cell would measure it as zero. Either the
  /// caller stated a height, or `Root` measured the mounted subtree with `onLayout` and sent the
  /// result back through here.
  var height: Double?
  /// `switch` state, and the checked state of a `checkbox` or `radio` accessory.
  var on: Bool?
  /// Current text of a `textField` or `textArea` row.
  var text: String?
  var placeholder: String?
  var keyboardType: KeyboardType?
  var autoCapitalize: AutoCapitalize?
  var returnKeyType: ReturnKeyType?
  var secure: Bool?
  /// A fixed suffix at the trailing edge of a `textField` row — the `cm` in `Height 187 cm`.
  ///
  /// Deliberately not part of `text`. The caller owns the value, and a unit folded into it would
  /// come straight back through `onChangeText` as something the user has to parse off again — so
  /// native draws it beside the field and never sends it anywhere.
  ///
  /// **Read by `textField` rows only.** A `textArea` fills its row and grows with its content, so a
  /// suffix has no line to sit on; the TypeScript API does not offer one, the serializer will not
  /// write one, and both platforms ignore it if an older bundle sends one anyway.
  var unit: String?
  /// Caps how far a `textArea` grows before it starts scrolling internally. Unset means it grows
  /// without limit, which is what the Reminders notes field does.
  var maxLines: Int?
  /// `datePicker` value, as milliseconds since the epoch — the one date encoding JSON has.
  var dateMillis: Double?
  var datePickerMode: DatePickerMode?
  var datePickerStyle: DatePickerStyle?
  var minDateMillis: Double?
  var maxDateMillis: Double?
  /// `slider` position, in the units [sliderMin]…[sliderMax] describe.
  ///
  /// **A controlled value, with one exception that native owns.** Like every other control here the
  /// caller holds the state and native reports changes — but a slider reports them *per frame of a
  /// drag*, so the commit carrying frame N routinely arrives while the thumb is at frame N+3. Native
  /// therefore ignores incoming values for as long as a drag is in progress and takes them again on
  /// release, which is the numeric form of the echo rule the text fields follow.
  var sliderValue: Double?
  /// Defaults to 0.
  var sliderMin: Double?
  /// Defaults to 1, so an unbounded slider is a fraction — which is what most of them are.
  var sliderMax: Double?
  /// Quantises the value. Unset or 0 is continuous.
  ///
  /// Both platforms round to it, but only Android *draws* it: M3 marks each stop on the track, and
  /// iOS has never had tick marks on a `UISlider`. Documented rather than faked — drawing our own
  /// ticks on iOS would produce a control no iOS user has seen.
  var sliderStep: Double?
  /// SF Symbols flanking the track — the small and large suns on a brightness slider.
  ///
  /// `UISlider` has slots for exactly these (`minimumValueImage`/`maximumValueImage`), and Android
  /// has no equivalent property, so the Material slider is laid out between two icon views to the
  /// same effect. Mapped through the Material Symbols table on Android like any other `systemImage`.
  var sliderMinImage: String?
  var sliderMaxImage: String?
  /// `button` emphasis.
  var role: ButtonRole?
  /// `menu` entries, and which of them is currently chosen.
  var menuItems: [MenuItemSpec]?
  var selectedItemId: String?
  /// Revealed by swiping from the trailing and leading edges respectively.
  var trailingActions: [SwipeActionSpec]?
  var leadingActions: [SwipeActionSpec]?

  private enum CodingKeys: String, CodingKey {
    case id, kind, label, secondaryLabel, value, accessory, systemImage, materialSymbol, imageColor, imageBackground, imageMonogram, imageSize, badge, badgeColor, secondaryLabelTinted, font, selectable, disabled, tintColor, hostIndex, hostBackground, height, on, text, placeholder, keyboardType, autoCapitalize, returnKeyType, secure, unit, maxLines, dateMillis, datePickerMode, datePickerStyle, minDateMillis, maxDateMillis, sliderValue, sliderMin, sliderMax, sliderStep, sliderMinImage, sliderMaxImage, role, menuItems, selectedItemId, trailingActions, leadingActions
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
    kind = try container.decodeIfPresent(RowKind.self, forKey: .kind) ?? .`default`
    label = try container.decodeIfPresent(String.self, forKey: .label)
    secondaryLabel = try container.decodeIfPresent(String.self, forKey: .secondaryLabel)
    value = try container.decodeIfPresent(String.self, forKey: .value)
    accessory = try container.decodeIfPresent(AccessoryKind.self, forKey: .accessory)
    systemImage = try container.decodeIfPresent(String.self, forKey: .systemImage)
    materialSymbol = try container.decodeIfPresent(String.self, forKey: .materialSymbol)
    imageColor = try container.decodeIfPresent(String.self, forKey: .imageColor)
    imageBackground = try container.decodeIfPresent(String.self, forKey: .imageBackground)
    imageMonogram = try container.decodeIfPresent(String.self, forKey: .imageMonogram)
    imageSize = try container.decodeIfPresent(Double.self, forKey: .imageSize)
    badge = try container.decodeIfPresent(String.self, forKey: .badge)
    badgeColor = try container.decodeIfPresent(String.self, forKey: .badgeColor)
    secondaryLabelTinted = try container.decodeIfPresent(Bool.self, forKey: .secondaryLabelTinted)
    font = try container.decodeIfPresent(FontSpec.self, forKey: .font)
    selectable = try container.decodeIfPresent(Bool.self, forKey: .selectable)
    disabled = try container.decodeIfPresent(Bool.self, forKey: .disabled)
    tintColor = try container.decodeIfPresent(String.self, forKey: .tintColor)
    hostIndex = try container.decodeIfPresent(Int.self, forKey: .hostIndex)
    hostBackground = try container.decodeIfPresent(HostBackground.self, forKey: .hostBackground)
    height = try container.decodeIfPresent(Double.self, forKey: .height)
    on = try container.decodeIfPresent(Bool.self, forKey: .on)
    text = try container.decodeIfPresent(String.self, forKey: .text)
    placeholder = try container.decodeIfPresent(String.self, forKey: .placeholder)
    keyboardType = try container.decodeIfPresent(KeyboardType.self, forKey: .keyboardType)
    autoCapitalize = try container.decodeIfPresent(AutoCapitalize.self, forKey: .autoCapitalize)
    returnKeyType = try container.decodeIfPresent(ReturnKeyType.self, forKey: .returnKeyType)
    secure = try container.decodeIfPresent(Bool.self, forKey: .secure)
    unit = try container.decodeIfPresent(String.self, forKey: .unit)
    maxLines = try container.decodeIfPresent(Int.self, forKey: .maxLines)
    dateMillis = try container.decodeIfPresent(Double.self, forKey: .dateMillis)
    datePickerMode = try container.decodeIfPresent(DatePickerMode.self, forKey: .datePickerMode)
    datePickerStyle = try container.decodeIfPresent(DatePickerStyle.self, forKey: .datePickerStyle)
    minDateMillis = try container.decodeIfPresent(Double.self, forKey: .minDateMillis)
    maxDateMillis = try container.decodeIfPresent(Double.self, forKey: .maxDateMillis)
    sliderValue = try container.decodeIfPresent(Double.self, forKey: .sliderValue)
    sliderMin = try container.decodeIfPresent(Double.self, forKey: .sliderMin)
    sliderMax = try container.decodeIfPresent(Double.self, forKey: .sliderMax)
    sliderStep = try container.decodeIfPresent(Double.self, forKey: .sliderStep)
    sliderMinImage = try container.decodeIfPresent(String.self, forKey: .sliderMinImage)
    sliderMaxImage = try container.decodeIfPresent(String.self, forKey: .sliderMaxImage)
    role = try container.decodeIfPresent(ButtonRole.self, forKey: .role)
    menuItems = try container.decodeIfPresent([MenuItemSpec].self, forKey: .menuItems)
    selectedItemId = try container.decodeIfPresent(String.self, forKey: .selectedItemId)
    trailingActions = try container.decodeIfPresent([SwipeActionSpec].self, forKey: .trailingActions)
    leadingActions = try container.decodeIfPresent([SwipeActionSpec].self, forKey: .leadingActions)
  }
}

/// The tappable control on the trailing edge of a section header — "See All", "Edit".
///
/// No callback here, for the same reason no other spec has one: this is JSON. The section's `id` is
/// what native reports back, and `Root` dispatches from a registry keyed by it.
struct SectionActionSpec: Decodable, Equatable {
  /// The button's title. Drawn in the tint colour, as a header button is.
  var title: String?
  /// SF Symbol name. Shown instead of the title when both are set, as UIKit prefers elsewhere.
  var systemImage: String?
  var disabled: Bool?

  private enum CodingKeys: String, CodingKey {
    case title, systemImage, disabled
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    title = try container.decodeIfPresent(String.self, forKey: .title)
    systemImage = try container.decodeIfPresent(String.self, forKey: .systemImage)
    disabled = try container.decodeIfPresent(Bool.self, forKey: .disabled)
  }
}

struct SectionSpec: Decodable, Equatable {
  /// Unique among sections; becomes a diffable section identifier.
  ///
  /// Fatal if repeated, for the same reason `RowSpec.id` is — `appendSections` rejects a duplicate
  /// exactly as `appendItems` does — and guarded the same way.
  var id: String = ""
  /// Header title. Pins to the top of the viewport in the `plain` appearance.
  var header: String?
  /// A control on the header's trailing edge.
  ///
  /// Only meaningful with a `header` — UIKit gives a section no header view at all unless one is
  /// asked for, and a button floating where no header exists has nowhere to be.
  var action: SectionActionSpec?
  /// The grey explanatory text drawn under a group.
  var footer: String?
  /// `list` unless set. `chips` makes the section a horizontally scrolling strip of pills.
  var layout: SectionLayout?
  /// The letter this section contributes to the A–Z scrubber, when `showsSectionIndex` is on.
  ///
  /// Separate from `header` because the two genuinely differ: a Contacts header reads `A` but a
  /// header could equally be `Recently Added`, and the scrubber has room for one glyph. A section
  /// that sets no index title is skipped by the scrubber rather than given a blank stop, so a
  /// list can mix indexed and unindexed sections.
  var indexTitle: String?
  var rows: [RowSpec] = []

  private enum CodingKeys: String, CodingKey {
    case id, header, action, footer, layout, indexTitle, rows
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
    header = try container.decodeIfPresent(String.self, forKey: .header)
    action = try container.decodeIfPresent(SectionActionSpec.self, forKey: .action)
    footer = try container.decodeIfPresent(String.self, forKey: .footer)
    layout = try container.decodeIfPresent(SectionLayout.self, forKey: .layout)
    indexTitle = try container.decodeIfPresent(String.self, forKey: .indexTitle)
    rows = try container.decodeIfPresent([RowSpec].self, forKey: .rows) ?? []
  }
}

/// Font selection. Set on the root as the default, or per slot to override.
///
/// Leave `family` unset to keep whatever face the slot normally uses — which is not the same as
/// asking for `system-ui`, because a section header is heavier than a row label and only the
/// former keeps that.
struct FontSpec: Decodable, Equatable {
  var family: String?
  /// Points. Omit to take the size of the text style the slot normally uses.
  var size: Double?
  /// `'regular' | 'medium' | 'semibold' | 'bold' | …`, or `'100'`–`'900'`.
  var weight: String?
  /// Variable-font axis overrides, as a compact `tag=value` list: `'wght=620,wdth=110'`.
  ///
  /// One flat string rather than a nested object so it stays a single scalar across the
  /// boundary. Axes the face does not expose are ignored, which makes the same spec safe to
  /// reuse with a static font.
  var variations: String?
  /// Scale with Dynamic Type. Defaults to true.
  var scaled: Bool?

  private enum CodingKeys: String, CodingKey {
    case family, size, weight, variations, scaled
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    family = try container.decodeIfPresent(String.self, forKey: .family)
    size = try container.decodeIfPresent(Double.self, forKey: .size)
    weight = try container.decodeIfPresent(String.self, forKey: .weight)
    variations = try container.decodeIfPresent(String.self, forKey: .variations)
    scaled = try container.decodeIfPresent(Bool.self, forKey: .scaled)
  }
}

/// A linear gradient behind the content.
///
/// Health's summary screens are the case: a tinted wash behind the cards that a flat `background`
/// cannot express. Drawn into the collection view's `backgroundView` as a `CAGradientLayer`, so it
/// scrolls with nothing and costs one layer.
struct GradientSpec: Decodable, Equatable {
  /// Two or more colours, normalised to `#RRGGBBAA` before crossing.
  var colors: [String] = []
  /// Optional stops in `0...1`. Must match `colors` in length, or it is ignored.
  var locations: [Double]?
  /// Degrees clockwise from top-to-bottom. `0` is vertical, `90` runs left to right.
  var angle: Double?

  private enum CodingKeys: String, CodingKey {
    case colors, locations, angle
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    colors = try container.decodeIfPresent([String].self, forKey: .colors) ?? []
    locations = try container.decodeIfPresent([Double].self, forKey: .locations)
    angle = try container.decodeIfPresent(Double.self, forKey: .angle)
  }
}

/// Colour, spacing and typography overrides.
///
/// Everything is optional and falls back to the platform's own value, so setting one field
/// does not force you to restate the rest of the look. That is the reason appearance travels
/// in the JSON tree rather than as a typed codegen prop: codegen has no representation of
/// "absent" at any depth, so `rowBackground` unset would be indistinguishable from
/// `rowBackground: transparent`.
///
/// Colours accept anything React Native accepts — `'red'`, `'#f0f'`, `'rgba(0,0,0,.5)'` — and
/// are normalised to `#RRGGBBAA` before crossing.
struct Appearance: Decodable, Equatable {
  /// Behind the cells.
  var background: String?
  /// Behind the cells, drawn over `background`.
  var backgroundGradient: GradientSpec?
  /// The cell itself — in a grouped style, the rounded card.
  var rowBackground: String?
  var separator: String?
  var labelColor: String?
  /// Second line and trailing value text.
  var secondaryLabelColor: String?
  var headerTextColor: String?
  /// Defaults to `opaque`, because a pinned header has to hide the rows passing beneath it — unless
  /// it is deliberately a material, which is what Contacts does.
  var headerBackgroundStyle: HeaderBackgroundStyle?
  var footerTextColor: String?
  /// Overall tint for accessories and interactive elements.
  var tintColor: String?
  /// The whole vertical gap between one section and the next — not a contribution to it.
  /// Unset keeps the platform's own default.
  var sectionSpacing: Double?
  /// The gap above the *first* section, which is a separate number from the one between sections.
  ///
  /// It has to be separate, because UIKit itself treats it as one. A grouped list reserves about
  /// 35pt above section 0 — `UITableView`'s grouped inheritance, which
  /// `NSCollectionLayoutSection.list(using:)` still carries — and UIKit drops it only when the list
  /// is the scroll view under an *expanded large title*, the one arrangement where the title
  /// already supplies that separation. Turn the large title off and the gap comes back with
  /// nothing above it to explain it, which reads as the list having been pushed down.
  ///
  /// So this is the number a screen without a large title has to be able to say. `0` closes the
  /// gap entirely; unset keeps the platform's own value, which is the honest default because the
  /// 35pt *is* what a hand-written `UICollectionViewController` does.
  ///
  /// Android has the same gap for its own reasons — an opaque toolbar reserves its height and
  /// stops, so the first card would otherwise sit flush against it — and takes the same override.
  var firstSectionSpacing: Double?
  /// Default font for row labels and values.
  var font: FontSpec?
  /// Overrides `font` for section headers.
  var headerFont: FontSpec?
  /// Overrides `font` for section footers.
  var footerFont: FontSpec?

  private enum CodingKeys: String, CodingKey {
    case background, backgroundGradient, rowBackground, separator, labelColor, secondaryLabelColor, headerTextColor, headerBackgroundStyle, footerTextColor, tintColor, sectionSpacing, firstSectionSpacing, font, headerFont, footerFont
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    background = try container.decodeIfPresent(String.self, forKey: .background)
    backgroundGradient = try container.decodeIfPresent(GradientSpec.self, forKey: .backgroundGradient)
    rowBackground = try container.decodeIfPresent(String.self, forKey: .rowBackground)
    separator = try container.decodeIfPresent(String.self, forKey: .separator)
    labelColor = try container.decodeIfPresent(String.self, forKey: .labelColor)
    secondaryLabelColor = try container.decodeIfPresent(String.self, forKey: .secondaryLabelColor)
    headerTextColor = try container.decodeIfPresent(String.self, forKey: .headerTextColor)
    headerBackgroundStyle = try container.decodeIfPresent(HeaderBackgroundStyle.self, forKey: .headerBackgroundStyle)
    footerTextColor = try container.decodeIfPresent(String.self, forKey: .footerTextColor)
    tintColor = try container.decodeIfPresent(String.self, forKey: .tintColor)
    sectionSpacing = try container.decodeIfPresent(Double.self, forKey: .sectionSpacing)
    firstSectionSpacing = try container.decodeIfPresent(Double.self, forKey: .firstSectionSpacing)
    font = try container.decodeIfPresent(FontSpec.self, forKey: .font)
    headerFont = try container.decodeIfPresent(FontSpec.self, forKey: .headerFont)
    footerFont = try container.decodeIfPresent(FontSpec.self, forKey: .footerFont)
  }
}

/// The whole payload, as it crosses on the `tree` prop.
struct Tree: Decodable, Equatable {
  var sections: [SectionSpec] = []
  var listAppearance: ListAppearance?
  /// Android's Material 3 list style. Ignored on iOS, where the shape comes from `listAppearance`.
  ///
  /// Defaults to `segmented` for `insetGrouped` and `grouped`, and to `standard` for `plain` —
  /// which is the mapping that makes an unchanged cross-platform screen look right on both.
  var androidListStyle: AndroidListStyle?
  var appearance: Appearance?
  /// Applied when the interface style is dark. Falls back to `appearance` field by field, so
  /// setting only `appearance` gives you that look in both modes — the least surprising
  /// behaviour, and the reason this is not a required counterpart.
  var darkAppearance: Appearance?

  private enum CodingKeys: String, CodingKey {
    case sections, listAppearance, androidListStyle, appearance, darkAppearance
  }

  /// All defaults. Lets native render an empty list before any tree has arrived.
  init() {}

  init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    sections = try container.decodeIfPresent([SectionSpec].self, forKey: .sections) ?? []
    listAppearance = try container.decodeIfPresent(ListAppearance.self, forKey: .listAppearance)
    androidListStyle = try container.decodeIfPresent(AndroidListStyle.self, forKey: .androidListStyle)
    appearance = try container.decodeIfPresent(Appearance.self, forKey: .appearance)
    darkAppearance = try container.decodeIfPresent(Appearance.self, forKey: .darkAppearance)
  }
}


/**
 * The descriptor tree, and the single source of truth for the Swift `Codable` model.
 *
 * This file is the contract. It crosses into native as one JSON string on the `tree` prop
 * (see `specs/RNGUICollectionViewNativeComponent.ts` for why it is JSON rather than typed
 * codegen props), and `ios/Generated/TreeTypes.swift` is generated from it. Two rules
 * follow, and both are load-bearing:
 *
 * 1. **Nothing here may be a function.** Callbacks cannot be serialized; rows carry a
 *    stable `id` and native reports events against it, which the handler registry
 *    dispatches. That is why `onPress` lives in the component props but never in a spec.
 *
 * 2. **Adding a field is safe; changing the meaning of one is not.** A JS bundle can be
 *    newer than the native binary it runs against — `expo-updates` ships JS alone — so the
 *    Swift side decodes leniently and an unknown enum value degrades rather than failing.
 *    Renaming or repurposing a field breaks that guarantee silently.
 */

/**
 * A whole number, for the Swift generator's benefit.
 *
 * TypeScript has one numeric type and Swift has several. Everything typed `number` is
 * generated as a `Double`, which is right for measurements but wrong for an index — so this
 * alias marks the fields that must arrive as `Int`. The generator matches it *syntactically*,
 * by the name written in the annotation, so it never has to resolve types.
 */
export type IntValue = number

/** Which native cell configuration to build for a row. */
export type RowKind =
  | 'default'
  /** `label` plus trailing detail text. */
  | 'value'
  /** `label` above `secondaryLabel`. */
  | 'subtitle'
  /**
   * Hosts a real React Native child view inside the cell.
   *
   * Unlike every other kind, the child is *rendered* rather than described, and native
   * reparents it into `cell.contentView`. `hostIndex` says which mounted child belongs here.
   */
  | 'host'
  /** `label` with a trailing `UISwitch`. */
  | 'switch'
  /** A single-line `UITextField` filling the row. */
  | 'textField'
  /** A `UITextView` that grows with its content. */
  | 'textArea'
  /** A tinted, centred, tappable label — not a row that happens to be pressable. */
  | 'button'
  /** `label` with a trailing `UIButton` presenting a `UIMenu`. */
  | 'menu'
  /** A `UIDatePicker`, compact or inline. */
  | 'datePicker'
  /**
   * A rich stacked cell — a title, a prominent value and a caption.
   *
   * The recyclable counterpart to a `Host` row: everything a summary card usually needs, described
   * rather than rendered, so a list of fifty of them costs fifty pooled cells instead of fifty
   * React subtrees.
   */
  | 'card'
  /**
   * A pill in a horizontally scrolling section. Set by the serializer from the *section's* layout
   * rather than by the caller, so a chip section cannot contain a non-chip row.
   */
  | 'chip'

/**
 * Trailing accessory, drawn at the leading edge of the cell's trailing margin.
 *
 * `checkbox` and `radio` are accessories rather than row kinds on purpose: they are decorations on
 * an otherwise ordinary row, and treating them as kinds would mean a `subtitle` row could not have
 * one. This is what `UICellAccessory` buys over `UITableViewCell.accessoryType`, which offered
 * only the iOS checkmark.
 */
export type AccessoryKind =
  | 'none'
  | 'disclosure'
  | 'checkmark'
  /** A filled circle when on, a hollow one when off — the multi-select affordance. */
  | 'checkbox'
  /** Visually a checkbox, semantically exclusive. The difference is the caller's to enforce. */
  | 'radio'
  /** A trailing `UIActivityIndicatorView`, for a row whose value is still loading. */
  | 'spinner'

/** Text-entry behaviour for `textField` and `textArea` rows. */
export type KeyboardType =
  'default' | 'numeric' | 'decimal' | 'email' | 'phone' | 'url' | 'asciiCapable'

export type AutoCapitalize = 'none' | 'sentences' | 'words' | 'characters'

export type ReturnKeyType =
  'default' | 'done' | 'go' | 'next' | 'search' | 'send'

/** What a `datePicker` row collects. */
export type DatePickerMode = 'date' | 'time' | 'dateAndTime'

/**
 * How a `datePicker` row presents itself.
 *
 * `compact` is the tappable pill that expands into an overlay; `inline` is the always-open
 * calendar or clock face; `wheels` is the classic drum. These need **separate reuse pools**
 * natively — a cell configured as a compact pill cannot be reconfigured into a calendar.
 */
export type DatePickerStyle = 'compact' | 'inline' | 'wheels'

/** A `button` row's emphasis, mapping to the same three roles `UIAlertAction` has. */
export type ButtonRole = 'default' | 'destructive' | 'plain'

/** One entry in a `menu` row's `UIMenu`. */
export interface MenuItemSpec {
  id: string
  title: string
  /** SF Symbol name, e.g. `exclamationmark.2`. */
  systemImage?: string
  destructive?: boolean
  disabled?: boolean
}

export type SwipeActionStyle = 'normal' | 'destructive'

/**
 * One button revealed by swiping a row.
 *
 * Lives on the row rather than being a separate concept because it is configured through the
 * *layout* — `UICollectionLayoutListConfiguration.trailingSwipeActionsConfigurationProvider` —
 * which is handed an index path and has to answer from the row's own descriptor.
 */
export interface SwipeActionSpec {
  id: string
  title?: string
  /** SF Symbol name. Shown instead of the title when both are set, as UIKit prefers. */
  systemImage?: string
  style?: SwipeActionStyle
  /** Overrides the style's own colour. Normalised to `#RRGGBBAA` before crossing. */
  backgroundColor?: string
}

/** A single row. */
export interface RowSpec {
  /**
   * Globally unique — not just unique within its section.
   *
   * These become `UICollectionViewDiffableDataSource` item identifiers, and a repeated one is
   * **fatal**: `appendItems` raises `NSInternalInconsistencyException`, *"Fatal: supplied item
   * identifiers are not unique"*. Native deduplicates before building the snapshot so malformed
   * input degrades to a missing row rather than a dead app, and the serializer reports the
   * collision under `__DEV__` where the offending call site is visible.
   */
  id: string
  kind: RowKind
  label?: string
  /** Second line, for `subtitle` rows, and the caption of a `card`. */
  secondaryLabel?: string
  /** Trailing detail text, for `value` rows. */
  value?: string
  accessory?: AccessoryKind
  /**
   * A leading SF Symbol — the calendar and clock glyphs on Reminders' Date and Time rows.
   *
   * A symbol name rather than an image source: these are glyphs from the system set, they scale
   * with Dynamic Type for free, and they take the row's tint without an asset pipeline.
   */
  systemImage?: string
  /**
   * An explicit Material Symbol name, for Android.
   *
   * The one deliberate platform-specific field in this file, and the escape hatch for the fact
   * that `systemImage` cannot be mapped completely: SF Symbols and Material Symbols overlap in
   * meaning but never in naming, so native carries a curated map and an unmapped name renders
   * nothing. Setting this names the Android glyph directly, and wins over `systemImage` where both
   * are set — an escape hatch that loses to the thing it overrides is not one.
   *
   * Ignored on iOS. Additive, so it is safe under rule 2 above: a binary that predates it simply
   * does not read the key.
   */
  materialSymbol?: string
  /** Overrides the glyph's colour. Normalised to `#RRGGBBAA` before crossing. */
  imageColor?: string
  /**
   * Fills a rounded tile behind the glyph and draws the glyph white — Settings' coloured squares.
   *
   * Set, this replaces `imageColor` rather than combining with it: the point of a tile is that the
   * colour is the *background*, and a Settings row has never had a tinted glyph on a tinted square.
   * Normalised to `#RRGGBBAA` before crossing.
   */
  imageBackground?: string
  /**
   * The glyph's point size, for a bare symbol. Ignored when `imageBackground` is set, where the
   * tile's own size decides.
   */
  imageSize?: number
  /**
   * The red count bubble — Settings' unread badge.
   *
   * A string rather than a number because these are not always counts: iOS puts version numbers
   * and a bare `!` in the same bubble, and a caller who has "1" already formatted should not have
   * to unformat it.
   */
  badge?: string
  /** The bubble's fill. Defaults to the system red. Normalised to `#RRGGBBAA` before crossing. */
  badgeColor?: string
  /**
   * Draws `secondaryLabel` in the tint colour rather than as grey detail text.
   *
   * This is the "Today" / "15:00" under Reminders' Date and Time rows: the tint is what marks the
   * value as the row's *current setting* and the row as something to tap, rather than as an
   * explanatory second line.
   */
  secondaryLabelTinted?: boolean
  /**
   * Overrides the list's font for this row alone, falling back to it field by field.
   *
   * What a large title field needs — Reminders' title is set noticeably bigger than the notes
   * under it, and that is a property of the row rather than of the list.
   */
  font?: FontSpec
  /** Whether tapping highlights the row and reports a press. */
  selectable?: boolean
  /**
   * Greys the row out and stops it responding.
   *
   * Applied to the *control* as well as the label, which is the part that is easy to miss: a
   * disabled-looking row whose switch still flips is worse than no disabled state at all.
   */
  disabled?: boolean
  /**
   * Overrides the list tint for this row alone — a destructive button, a highlighted accessory.
   * Normalised to `#RRGGBBAA` before crossing.
   */
  tintColor?: string
  /**
   * For `host` rows: which mounted React child belongs in this cell, by mount order.
   */
  hostIndex?: IntValue
  /**
   * Row height in points.
   *
   * For a `host` row this is the space the cell reserves, and it is always a number JavaScript
   * decided — never something native measured. Fabric lays the child out with Yoga, so the view
   * has no `intrinsicContentSize` and an `.estimated` cell would measure it as zero. Either the
   * caller stated a height, or `Root` measured the mounted subtree with `onLayout` and sent the
   * result back through here.
   */
  height?: number

  // -------------------------------------------------------------------------
  // Controls
  //
  // One flat set of fields rather than a discriminated union per kind. Codegen could not express
  // a union anyway, and `Codable` optionals already say "absent" — so a `switch` row simply
  // leaves the date fields unset. The cost is that nothing stops a caller setting `on` on a
  // `datePicker` row; native ignores it, which is the same outcome a union would produce.
  // -------------------------------------------------------------------------

  /** `switch` state, and the checked state of a `checkbox` or `radio` accessory. */
  on?: boolean

  /** Current text of a `textField` or `textArea` row. */
  text?: string
  placeholder?: string
  keyboardType?: KeyboardType
  autoCapitalize?: AutoCapitalize
  returnKeyType?: ReturnKeyType
  secure?: boolean
  /**
   * Caps how far a `textArea` grows before it starts scrolling internally. Unset means it grows
   * without limit, which is what the Reminders notes field does.
   */
  maxLines?: IntValue

  /** `datePicker` value, as milliseconds since the epoch — the one date encoding JSON has. */
  dateMillis?: number
  datePickerMode?: DatePickerMode
  datePickerStyle?: DatePickerStyle
  minDateMillis?: number
  maxDateMillis?: number

  /** `button` emphasis. */
  role?: ButtonRole

  /** `menu` entries, and which of them is currently chosen. */
  menuItems?: MenuItemSpec[]
  selectedItemId?: string

  /** Revealed by swiping from the trailing and leading edges respectively. */
  trailingActions?: SwipeActionSpec[]
  leadingActions?: SwipeActionSpec[]
}

/**
 * The tappable control on the trailing edge of a section header — "See All", "Edit".
 *
 * No callback here, for the same reason no other spec has one: this is JSON. The section's `id` is
 * what native reports back, and `Root` dispatches from a registry keyed by it.
 */
export interface SectionActionSpec {
  /** The button's title. Drawn in the tint colour, as a header button is. */
  title?: string
  /** SF Symbol name. Shown instead of the title when both are set, as UIKit prefers elsewhere. */
  systemImage?: string
  disabled?: boolean
}

export interface SectionSpec {
  /**
   * Unique among sections; becomes a diffable section identifier.
   *
   * Fatal if repeated, for the same reason `RowSpec.id` is — `appendSections` rejects a duplicate
   * exactly as `appendItems` does — and guarded the same way.
   */
  id: string
  /** Header title. Pins to the top of the viewport in the `plain` appearance. */
  header?: string
  /**
   * A control on the header's trailing edge.
   *
   * Only meaningful with a `header` — UIKit gives a section no header view at all unless one is
   * asked for, and a button floating where no header exists has nowhere to be.
   */
  action?: SectionActionSpec
  /** The grey explanatory text drawn under a group. */
  footer?: string
  /** `list` unless set. `chips` makes the section a horizontally scrolling strip of pills. */
  layout?: SectionLayout
  /**
   * The letter this section contributes to the A–Z scrubber, when `showsSectionIndex` is on.
   *
   * Separate from `header` because the two genuinely differ: a Contacts header reads `A` but a
   * header could equally be `Recently Added`, and the scrubber has room for one glyph. A section
   * that sets no index title is skipped by the scrubber rather than given a blank stop, so a
   * list can mix indexed and unindexed sections.
   */
  indexTitle?: string
  rows: RowSpec[]
}

// ---------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------

/**
 * The system typefaces, matching CSS's `ui-*` generic families and
 * `UIFontDescriptor.SystemDesign`. `rounded` is SF Rounded.
 */
export type FontDesign = 'default' | 'rounded' | 'serif' | 'monospaced'

/**
 * Font selection. Set on the root as the default, or per slot to override.
 *
 * `family` names an app-bundled face — whatever name it is registered under, which for
 * `expo-font` is the key passed to `useFonts`. Leave it unset to stay on the system font and
 * pick a `design` instead.
 */
export interface FontSpec {
  family?: string
  design?: FontDesign
  /** Points. Omit to take the size of the text style the slot normally uses. */
  size?: number
  /** `'regular' | 'medium' | 'semibold' | 'bold' | …`, or `'100'`–`'900'`. */
  weight?: string
  /**
   * Variable-font axis overrides, as a compact `tag=value` list: `'wght=620,wdth=110'`.
   *
   * One flat string rather than a nested object so it stays a single scalar across the
   * boundary. Axes the face does not expose are ignored, which makes the same spec safe to
   * reuse with a static font.
   */
  variations?: string
  /** Scale with Dynamic Type. Defaults to true. */
  scaled?: boolean
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

/**
 * How a section header paints its background.
 *
 * `blurred` and `soft` both let the rows scroll visibly *under* a pinned header rather than
 * disappearing behind an opaque strip, and both only read correctly when the header pins — which
 * is why the `plain` appearance is where they belong.
 *
 * The difference is the bottom edge. `blurred` is a material that stops in a straight line, which
 * is how iOS drew these before 26. `soft` fades the material out instead, so there is no line at
 * all — the same treatment `UIScrollEdgeEffect`'s soft style gives a navigation bar, and what a
 * screen sitting under one should match.
 */
export type HeaderBackgroundStyle =
  'opaque' | 'blurred' | 'soft' | 'transparent'

/**
 * How a section arranges its rows.
 *
 * `chips` is the one thing compositional layout buys that a `UITableView` simply cannot do: a
 * horizontally scrolling strip *inside* a vertical list, with its own recycling. Reminders' Details
 * screen and Health's category pickers are both this.
 */
export type SectionLayout = 'list' | 'chips'

/**
 * A linear gradient behind the content.
 *
 * Health's summary screens are the case: a tinted wash behind the cards that a flat `background`
 * cannot express. Drawn into the collection view's `backgroundView` as a `CAGradientLayer`, so it
 * scrolls with nothing and costs one layer.
 */
export interface GradientSpec {
  /** Two or more colours, normalised to `#RRGGBBAA` before crossing. */
  colors: string[]
  /** Optional stops in `0...1`. Must match `colors` in length, or it is ignored. */
  locations?: number[]
  /** Degrees clockwise from top-to-bottom. `0` is vertical, `90` runs left to right. */
  angle?: number
}

/** Mirrors `UICollectionLayoutListConfiguration.Appearance`. */
export type ListAppearance = 'insetGrouped' | 'grouped' | 'plain'

/**
 * How Material 3 arranges list items on Android. Ignored on iOS.
 *
 * The [M3 list spec](https://m3.material.io/components/lists/specs) defines exactly two, and they
 * are a *visual* choice that does not change a list's behaviour:
 *
 * - `standard` — items sit flush against one another on the list surface, separated by dividers
 *   where a divider is wanted. The default, and what most Android lists are.
 * - `segmented` — each item is its own rounded container with space between, and a selected item
 *   takes a larger corner radius and the `secondaryContainer` colour.
 *
 * A second field rather than more members on `ListAppearance`, because the two describe different
 * things: `listAppearance` says how sections are *grouped* and is shared, this says how Android
 * *draws* the items inside them. Additive, so it is safe under rule 2 above.
 */
export type AndroidListStyle = 'standard' | 'segmented'

/**
 * Colour, spacing and typography overrides.
 *
 * Everything is optional and falls back to the platform's own value, so setting one field
 * does not force you to restate the rest of the look. That is the reason appearance travels
 * in the JSON tree rather than as a typed codegen prop: codegen has no representation of
 * "absent" at any depth, so `rowBackground` unset would be indistinguishable from
 * `rowBackground: transparent`.
 *
 * Colours accept anything React Native accepts — `'red'`, `'#f0f'`, `'rgba(0,0,0,.5)'` — and
 * are normalised to `#RRGGBBAA` before crossing.
 */
export interface Appearance {
  /** Behind the cells. */
  background?: string
  /** Behind the cells, drawn over `background`. */
  backgroundGradient?: GradientSpec
  /** The cell itself — in a grouped style, the rounded card. */
  rowBackground?: string
  separator?: string
  labelColor?: string
  /** Second line and trailing value text. */
  secondaryLabelColor?: string
  headerTextColor?: string
  /**
   * Defaults to `opaque`, because a pinned header has to hide the rows passing beneath it — unless
   * it is deliberately a material, which is what Contacts does.
   */
  headerBackgroundStyle?: HeaderBackgroundStyle
  footerTextColor?: string
  /** Overall tint for accessories and interactive elements. */
  tintColor?: string
  /**
   * The whole vertical gap between one section and the next — not a contribution to it.
   * Unset keeps the platform's own default.
   */
  sectionSpacing?: number
  /** Default font for row labels and values. */
  font?: FontSpec
  /** Overrides `font` for section headers. */
  headerFont?: FontSpec
  /** Overrides `font` for section footers. */
  footerFont?: FontSpec
}

/** The whole payload, as it crosses on the `tree` prop. */
export interface Tree {
  sections: SectionSpec[]
  listAppearance?: ListAppearance
  /**
   * Android's Material 3 list style. Ignored on iOS, where the shape comes from `listAppearance`.
   *
   * Defaults to `segmented` for `insetGrouped` and `grouped`, and to `standard` for `plain` —
   * which is the mapping that makes an unchanged cross-platform screen look right on both.
   */
  androidListStyle?: AndroidListStyle
  appearance?: Appearance
  /**
   * Applied when the interface style is dark. Falls back to `appearance` field by field, so
   * setting only `appearance` gives you that look in both modes — the least surprising
   * behaviour, and the reason this is not a required counterpart.
   */
  darkAppearance?: Appearance
}

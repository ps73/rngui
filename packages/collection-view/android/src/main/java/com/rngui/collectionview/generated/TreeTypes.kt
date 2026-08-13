// Generated from src/tree.ts by scripts/gen-kotlin-types.mjs. Do not edit.
//
// Run `npm run gen:kotlin-types` after changing the descriptor types. The output is committed on
// purpose: a Gradle sync must not require Node, and a schema change should be visible in a diff.
//
// `npm run verify` regenerates and fails on any diff, so a stale copy cannot pass it. Nothing
// runs that automatically — this repository has no CI — so it is a check somebody has to invoke.
//
// Every field decodes leniently, and an unrecognised enum value degrades to `unknown` rather
// than failing the payload. `expo-updates` can ship a JS bundle newer than the native binary it
// runs against, so "this build doesn't know that row kind yet" has to mean one dull row rather
// than an empty list.

package com.rngui.collectionview.generated

import org.json.JSONObject
import org.json.JSONTokener

/**
 * Generated from `RowKind` in tree.ts.
 */
enum class RowKind(val raw: String) {
  default("default"),
  `value`("value"),
  subtitle("subtitle"),
  host("host"),
  switch("switch"),
  textField("textField"),
  textArea("textArea"),
  button("button"),
  menu("menu"),
  datePicker("datePicker"),
  slider("slider"),
  card("card"),
  chip("chip"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): RowKind = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `AccessoryKind` in tree.ts.
 */
enum class AccessoryKind(val raw: String) {
  none("none"),
  disclosure("disclosure"),
  checkmark("checkmark"),
  checkbox("checkbox"),
  radio("radio"),
  spinner("spinner"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): AccessoryKind = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `KeyboardType` in tree.ts.
 */
enum class KeyboardType(val raw: String) {
  default("default"),
  numeric("numeric"),
  decimal("decimal"),
  email("email"),
  phone("phone"),
  url("url"),
  asciiCapable("asciiCapable"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): KeyboardType = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `AutoCapitalize` in tree.ts.
 */
enum class AutoCapitalize(val raw: String) {
  none("none"),
  sentences("sentences"),
  words("words"),
  characters("characters"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): AutoCapitalize = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `ReturnKeyType` in tree.ts.
 */
enum class ReturnKeyType(val raw: String) {
  default("default"),
  done("done"),
  go("go"),
  next("next"),
  search("search"),
  send("send"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): ReturnKeyType = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `DatePickerMode` in tree.ts.
 */
enum class DatePickerMode(val raw: String) {
  date("date"),
  time("time"),
  dateAndTime("dateAndTime"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): DatePickerMode = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `DatePickerStyle` in tree.ts.
 */
enum class DatePickerStyle(val raw: String) {
  compact("compact"),
  `inline`("inline"),
  wheels("wheels"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): DatePickerStyle = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `ButtonRole` in tree.ts.
 */
enum class ButtonRole(val raw: String) {
  default("default"),
  destructive("destructive"),
  plain("plain"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): ButtonRole = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `SwipeActionStyle` in tree.ts.
 */
enum class SwipeActionStyle(val raw: String) {
  normal("normal"),
  destructive("destructive"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): SwipeActionStyle = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `HeaderBackgroundStyle` in tree.ts.
 */
enum class HeaderBackgroundStyle(val raw: String) {
  opaque("opaque"),
  blurred("blurred"),
  soft("soft"),
  transparent("transparent"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): HeaderBackgroundStyle = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `SectionLayout` in tree.ts.
 */
enum class SectionLayout(val raw: String) {
  list("list"),
  chips("chips"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): SectionLayout = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `HostBackground` in tree.ts.
 */
enum class HostBackground(val raw: String) {
  none("none"),
  card("card"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): HostBackground = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `ListAppearance` in tree.ts.
 */
enum class ListAppearance(val raw: String) {
  insetGrouped("insetGrouped"),
  grouped("grouped"),
  plain("plain"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): ListAppearance = byRaw[raw] ?: unknown
  }
}

/**
 * Generated from `AndroidListStyle` in tree.ts.
 */
enum class AndroidListStyle(val raw: String) {
  standard("standard"),
  segmented("segmented"),

  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): AndroidListStyle = byRaw[raw] ?: unknown
  }
}

/**
 * One entry in a `menu` row's `UIMenu`.
 */
data class MenuItemSpec(
  val id: String = "",
  val title: String = "",
  /**
   * SF Symbol name, e.g. `exclamationmark.2`.
   */
  val systemImage: String? = null,
  val destructive: Boolean? = null,
  val disabled: Boolean? = null,
) {
  companion object {
    fun from(json: JSONObject): MenuItemSpec =
      MenuItemSpec(
        id = json.string("id") ?: "",
        title = json.string("title") ?: "",
        systemImage = json.string("systemImage"),
        destructive = json.boolean("destructive"),
        disabled = json.boolean("disabled"),
      )
  }
}

/**
 * One button revealed by swiping a row.
 *
 * Lives on the row rather than being a separate concept because it is configured through the
 * *layout* — `UICollectionLayoutListConfiguration.trailingSwipeActionsConfigurationProvider` —
 * which is handed an index path and has to answer from the row's own descriptor.
 */
data class SwipeActionSpec(
  val id: String = "",
  val title: String? = null,
  /**
   * SF Symbol name. Shown instead of the title when both are set, as UIKit prefers.
   */
  val systemImage: String? = null,
  val style: SwipeActionStyle? = null,
  /**
   * Overrides the style's own colour. Normalised to `#RRGGBBAA` before crossing.
   */
  val backgroundColor: String? = null,
) {
  companion object {
    fun from(json: JSONObject): SwipeActionSpec =
      SwipeActionSpec(
        id = json.string("id") ?: "",
        title = json.string("title"),
        systemImage = json.string("systemImage"),
        style = json.string("style")?.let(SwipeActionStyle::from),
        backgroundColor = json.string("backgroundColor"),
      )
  }
}

/**
 * A single row.
 */
data class RowSpec(
  /**
   * Globally unique — not just unique within its section.
   *
   * These become `UICollectionViewDiffableDataSource` item identifiers, and a repeated one is
   * **fatal**: `appendItems` raises `NSInternalInconsistencyException`, *"Fatal: supplied item
   * identifiers are not unique"*. Native deduplicates before building the snapshot so malformed
   * input degrades to a missing row rather than a dead app, and the serializer reports the
   * collision under `__DEV__` where the offending call site is visible.
   */
  val id: String = "",
  val kind: RowKind = RowKind.default,
  val label: String? = null,
  /**
   * Second line, for `subtitle` rows, and the caption of a `card`.
   */
  val secondaryLabel: String? = null,
  /**
   * Trailing detail text, for `value` rows.
   */
  val value: String? = null,
  val accessory: AccessoryKind? = null,
  /**
   * A leading SF Symbol — the calendar and clock glyphs on Reminders' Date and Time rows.
   *
   * A symbol name rather than an image source: these are glyphs from the system set, they scale
   * with Dynamic Type for free, and they take the row's tint without an asset pipeline.
   */
  val systemImage: String? = null,
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
  val materialSymbol: String? = null,
  /**
   * Overrides the glyph's colour. Normalised to `#RRGGBBAA` before crossing.
   */
  val imageColor: String? = null,
  /**
   * Fills the platform's icon container behind the glyph and draws the glyph white.
   *
   * **The container's shape is the platform's, not the caller's**: a 29pt rounded square on iOS,
   * which is Settings' coloured tile, and a 40dp circle on Android, which is M3's leading avatar.
   * Android has never had the tile — it is Apple's, and drawing it there would be the clearest
   * possible case of one platform wearing the other's clothes. A screen that wants Pixel Settings
   * leaves this unset and gets the bare monochrome glyph Pixel Settings actually draws.
   *
   * Set, this replaces `imageColor` rather than combining with it: the point of a container is that
   * the colour is the *background*, and neither platform tints the glyph on top of it. Normalised
   * to `#RRGGBBAA` before crossing.
   */
  val imageBackground: String? = null,
  /**
   * One or two letters drawn in the container instead of a glyph — a contact's initials.
   *
   * The monogram avatar every address book falls back to when a person has no photo, and the one
   * leading element a symbol set cannot express: it is *derived from the row's own data* rather
   * than chosen from a fixed vocabulary. Both platforms draw it, in the container shape described
   * on [imageBackground] above.
   *
   * Wins over `systemImage` and `materialSymbol` where both are set, and needs `imageBackground` to
   * have something to sit in — letters floating unbounded where an icon would be read as a layout
   * bug, so a monogram without a container draws nothing and warns.
   *
   * Truncated to two characters by native. Longer is not a monogram, and silently drawing five
   * letters squeezed into a 40dp circle would be worse than the truncation.
   */
  val imageMonogram: String? = null,
  /**
   * The glyph's point size, for a bare symbol. Ignored when `imageBackground` is set, where the
   * container's own size decides.
   */
  val imageSize: Double? = null,
  /**
   * The red count bubble — Settings' unread badge.
   *
   * A string rather than a number because these are not always counts: iOS puts version numbers
   * and a bare `!` in the same bubble, and a caller who has "1" already formatted should not have
   * to unformat it.
   */
  val badge: String? = null,
  /**
   * The bubble's fill. Defaults to the system red. Normalised to `#RRGGBBAA` before crossing.
   */
  val badgeColor: String? = null,
  /**
   * Draws `secondaryLabel` in the tint colour rather than as grey detail text.
   *
   * This is the "Today" / "15:00" under Reminders' Date and Time rows: the tint is what marks the
   * value as the row's *current setting* and the row as something to tap, rather than as an
   * explanatory second line.
   */
  val secondaryLabelTinted: Boolean? = null,
  /**
   * Overrides the list's font for this row alone, falling back to it field by field.
   *
   * What a large title field needs — Reminders' title is set noticeably bigger than the notes
   * under it, and that is a property of the row rather than of the list.
   */
  val font: FontSpec? = null,
  /**
   * Whether tapping highlights the row and reports a press.
   */
  val selectable: Boolean? = null,
  /**
   * Greys the row out and stops it responding.
   *
   * Applied to the *control* as well as the label, which is the part that is easy to miss: a
   * disabled-looking row whose switch still flips is worse than no disabled state at all.
   */
  val disabled: Boolean? = null,
  /**
   * Overrides the list tint for this row alone — a destructive button, a highlighted accessory.
   * Normalised to `#RRGGBBAA` before crossing.
   */
  val tintColor: String? = null,
  /**
   * For `host` rows: which mounted React child belongs in this cell, by mount order.
   */
  val hostIndex: Int? = null,
  /**
   * For `host` rows: whether the cell draws the section's background.
   */
  val hostBackground: HostBackground? = null,
  /**
   * Row height in points.
   *
   * For a `host` row this is the space the cell reserves, and it is always a number JavaScript
   * decided — never something native measured. Fabric lays the child out with Yoga, so the view
   * has no `intrinsicContentSize` and an `.estimated` cell would measure it as zero. Either the
   * caller stated a height, or `Root` measured the mounted subtree with `onLayout` and sent the
   * result back through here.
   */
  val height: Double? = null,
  /**
   * `switch` state, and the checked state of a `checkbox` or `radio` accessory.
   */
  val on: Boolean? = null,
  /**
   * Current text of a `textField` or `textArea` row.
   */
  val text: String? = null,
  val placeholder: String? = null,
  val keyboardType: KeyboardType? = null,
  val autoCapitalize: AutoCapitalize? = null,
  val returnKeyType: ReturnKeyType? = null,
  val secure: Boolean? = null,
  /**
   * Caps how far a `textArea` grows before it starts scrolling internally. Unset means it grows
   * without limit, which is what the Reminders notes field does.
   */
  val maxLines: Int? = null,
  /**
   * `datePicker` value, as milliseconds since the epoch — the one date encoding JSON has.
   */
  val dateMillis: Double? = null,
  val datePickerMode: DatePickerMode? = null,
  val datePickerStyle: DatePickerStyle? = null,
  val minDateMillis: Double? = null,
  val maxDateMillis: Double? = null,
  /**
   * `slider` position, in the units [sliderMin]…[sliderMax] describe.
   *
   * **A controlled value, with one exception that native owns.** Like every other control here the
   * caller holds the state and native reports changes — but a slider reports them *per frame of a
   * drag*, so the commit carrying frame N routinely arrives while the thumb is at frame N+3. Native
   * therefore ignores incoming values for as long as a drag is in progress and takes them again on
   * release, which is the numeric form of the echo rule the text fields follow.
   */
  val sliderValue: Double? = null,
  /**
   * Defaults to 0.
   */
  val sliderMin: Double? = null,
  /**
   * Defaults to 1, so an unbounded slider is a fraction — which is what most of them are.
   */
  val sliderMax: Double? = null,
  /**
   * Quantises the value. Unset or 0 is continuous.
   *
   * Both platforms round to it, but only Android *draws* it: M3 marks each stop on the track, and
   * iOS has never had tick marks on a `UISlider`. Documented rather than faked — drawing our own
   * ticks on iOS would produce a control no iOS user has seen.
   */
  val sliderStep: Double? = null,
  /**
   * SF Symbols flanking the track — the small and large suns on a brightness slider.
   *
   * `UISlider` has slots for exactly these (`minimumValueImage`/`maximumValueImage`), and Android
   * has no equivalent property, so the Material slider is laid out between two icon views to the
   * same effect. Mapped through the Material Symbols table on Android like any other `systemImage`.
   */
  val sliderMinImage: String? = null,
  val sliderMaxImage: String? = null,
  /**
   * `button` emphasis.
   */
  val role: ButtonRole? = null,
  /**
   * `menu` entries, and which of them is currently chosen.
   */
  val menuItems: List<MenuItemSpec>? = null,
  val selectedItemId: String? = null,
  /**
   * Revealed by swiping from the trailing and leading edges respectively.
   */
  val trailingActions: List<SwipeActionSpec>? = null,
  val leadingActions: List<SwipeActionSpec>? = null,
) {
  companion object {
    fun from(json: JSONObject): RowSpec =
      RowSpec(
        id = json.string("id") ?: "",
        kind = json.string("kind")?.let(RowKind::from) ?: RowKind.default,
        label = json.string("label"),
        secondaryLabel = json.string("secondaryLabel"),
        value = json.string("value"),
        accessory = json.string("accessory")?.let(AccessoryKind::from),
        systemImage = json.string("systemImage"),
        materialSymbol = json.string("materialSymbol"),
        imageColor = json.string("imageColor"),
        imageBackground = json.string("imageBackground"),
        imageMonogram = json.string("imageMonogram"),
        imageSize = json.double("imageSize"),
        badge = json.string("badge"),
        badgeColor = json.string("badgeColor"),
        secondaryLabelTinted = json.boolean("secondaryLabelTinted"),
        font = json.obj("font")?.let(FontSpec::from),
        selectable = json.boolean("selectable"),
        disabled = json.boolean("disabled"),
        tintColor = json.string("tintColor"),
        hostIndex = json.int("hostIndex"),
        hostBackground = json.string("hostBackground")?.let(HostBackground::from),
        height = json.double("height"),
        on = json.boolean("on"),
        text = json.string("text"),
        placeholder = json.string("placeholder"),
        keyboardType = json.string("keyboardType")?.let(KeyboardType::from),
        autoCapitalize = json.string("autoCapitalize")?.let(AutoCapitalize::from),
        returnKeyType = json.string("returnKeyType")?.let(ReturnKeyType::from),
        secure = json.boolean("secure"),
        maxLines = json.int("maxLines"),
        dateMillis = json.double("dateMillis"),
        datePickerMode = json.string("datePickerMode")?.let(DatePickerMode::from),
        datePickerStyle = json.string("datePickerStyle")?.let(DatePickerStyle::from),
        minDateMillis = json.double("minDateMillis"),
        maxDateMillis = json.double("maxDateMillis"),
        sliderValue = json.double("sliderValue"),
        sliderMin = json.double("sliderMin"),
        sliderMax = json.double("sliderMax"),
        sliderStep = json.double("sliderStep"),
        sliderMinImage = json.string("sliderMinImage"),
        sliderMaxImage = json.string("sliderMaxImage"),
        role = json.string("role")?.let(ButtonRole::from),
        menuItems = json.array("menuItems")?.map { (it as? JSONObject)?.let(MenuItemSpec::from) ?: MenuItemSpec() },
        selectedItemId = json.string("selectedItemId"),
        trailingActions = json.array("trailingActions")?.map { (it as? JSONObject)?.let(SwipeActionSpec::from) ?: SwipeActionSpec() },
        leadingActions = json.array("leadingActions")?.map { (it as? JSONObject)?.let(SwipeActionSpec::from) ?: SwipeActionSpec() },
      )
  }
}

/**
 * The tappable control on the trailing edge of a section header — "See All", "Edit".
 *
 * No callback here, for the same reason no other spec has one: this is JSON. The section's `id` is
 * what native reports back, and `Root` dispatches from a registry keyed by it.
 */
data class SectionActionSpec(
  /**
   * The button's title. Drawn in the tint colour, as a header button is.
   */
  val title: String? = null,
  /**
   * SF Symbol name. Shown instead of the title when both are set, as UIKit prefers elsewhere.
   */
  val systemImage: String? = null,
  val disabled: Boolean? = null,
) {
  companion object {
    fun from(json: JSONObject): SectionActionSpec =
      SectionActionSpec(
        title = json.string("title"),
        systemImage = json.string("systemImage"),
        disabled = json.boolean("disabled"),
      )
  }
}

data class SectionSpec(
  /**
   * Unique among sections; becomes a diffable section identifier.
   *
   * Fatal if repeated, for the same reason `RowSpec.id` is — `appendSections` rejects a duplicate
   * exactly as `appendItems` does — and guarded the same way.
   */
  val id: String = "",
  /**
   * Header title. Pins to the top of the viewport in the `plain` appearance.
   */
  val header: String? = null,
  /**
   * A control on the header's trailing edge.
   *
   * Only meaningful with a `header` — UIKit gives a section no header view at all unless one is
   * asked for, and a button floating where no header exists has nowhere to be.
   */
  val action: SectionActionSpec? = null,
  /**
   * The grey explanatory text drawn under a group.
   */
  val footer: String? = null,
  /**
   * `list` unless set. `chips` makes the section a horizontally scrolling strip of pills.
   */
  val layout: SectionLayout? = null,
  /**
   * The letter this section contributes to the A–Z scrubber, when `showsSectionIndex` is on.
   *
   * Separate from `header` because the two genuinely differ: a Contacts header reads `A` but a
   * header could equally be `Recently Added`, and the scrubber has room for one glyph. A section
   * that sets no index title is skipped by the scrubber rather than given a blank stop, so a
   * list can mix indexed and unindexed sections.
   */
  val indexTitle: String? = null,
  val rows: List<RowSpec> = emptyList(),
) {
  companion object {
    fun from(json: JSONObject): SectionSpec =
      SectionSpec(
        id = json.string("id") ?: "",
        header = json.string("header"),
        action = json.obj("action")?.let(SectionActionSpec::from),
        footer = json.string("footer"),
        layout = json.string("layout")?.let(SectionLayout::from),
        indexTitle = json.string("indexTitle"),
        rows = json.array("rows")?.map { (it as? JSONObject)?.let(RowSpec::from) ?: RowSpec() } ?: emptyList(),
      )
  }
}

/**
 * Font selection. Set on the root as the default, or per slot to override.
 *
 * Leave `family` unset to keep whatever face the slot normally uses — which is not the same as
 * asking for `system-ui`, because a section header is heavier than a row label and only the
 * former keeps that.
 */
data class FontSpec(
  val family: String? = null,
  /**
   * Points. Omit to take the size of the text style the slot normally uses.
   */
  val size: Double? = null,
  /**
   * `'regular' | 'medium' | 'semibold' | 'bold' | …`, or `'100'`–`'900'`.
   */
  val weight: String? = null,
  /**
   * Variable-font axis overrides, as a compact `tag=value` list: `'wght=620,wdth=110'`.
   *
   * One flat string rather than a nested object so it stays a single scalar across the
   * boundary. Axes the face does not expose are ignored, which makes the same spec safe to
   * reuse with a static font.
   */
  val variations: String? = null,
  /**
   * Scale with Dynamic Type. Defaults to true.
   */
  val scaled: Boolean? = null,
) {
  companion object {
    fun from(json: JSONObject): FontSpec =
      FontSpec(
        family = json.string("family"),
        size = json.double("size"),
        weight = json.string("weight"),
        variations = json.string("variations"),
        scaled = json.boolean("scaled"),
      )
  }
}

/**
 * A linear gradient behind the content.
 *
 * Health's summary screens are the case: a tinted wash behind the cards that a flat `background`
 * cannot express. Drawn into the collection view's `backgroundView` as a `CAGradientLayer`, so it
 * scrolls with nothing and costs one layer.
 */
data class GradientSpec(
  /**
   * Two or more colours, normalised to `#RRGGBBAA` before crossing.
   */
  val colors: List<String> = emptyList(),
  /**
   * Optional stops in `0...1`. Must match `colors` in length, or it is ignored.
   */
  val locations: List<Double>? = null,
  /**
   * Degrees clockwise from top-to-bottom. `0` is vertical, `90` runs left to right.
   */
  val angle: Double? = null,
) {
  companion object {
    fun from(json: JSONObject): GradientSpec =
      GradientSpec(
        colors = json.array("colors")?.map { it as? String ?: "" } ?: emptyList(),
        locations = json.array("locations")?.map { (it as? Number)?.toDouble() ?: 0.0 },
        angle = json.double("angle"),
      )
  }
}

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
data class Appearance(
  /**
   * Behind the cells.
   */
  val background: String? = null,
  /**
   * Behind the cells, drawn over `background`.
   */
  val backgroundGradient: GradientSpec? = null,
  /**
   * The cell itself — in a grouped style, the rounded card.
   */
  val rowBackground: String? = null,
  val separator: String? = null,
  val labelColor: String? = null,
  /**
   * Second line and trailing value text.
   */
  val secondaryLabelColor: String? = null,
  val headerTextColor: String? = null,
  /**
   * Defaults to `opaque`, because a pinned header has to hide the rows passing beneath it — unless
   * it is deliberately a material, which is what Contacts does.
   */
  val headerBackgroundStyle: HeaderBackgroundStyle? = null,
  val footerTextColor: String? = null,
  /**
   * Overall tint for accessories and interactive elements.
   */
  val tintColor: String? = null,
  /**
   * The whole vertical gap between one section and the next — not a contribution to it.
   * Unset keeps the platform's own default.
   */
  val sectionSpacing: Double? = null,
  /**
   * Default font for row labels and values.
   */
  val font: FontSpec? = null,
  /**
   * Overrides `font` for section headers.
   */
  val headerFont: FontSpec? = null,
  /**
   * Overrides `font` for section footers.
   */
  val footerFont: FontSpec? = null,
) {
  companion object {
    fun from(json: JSONObject): Appearance =
      Appearance(
        background = json.string("background"),
        backgroundGradient = json.obj("backgroundGradient")?.let(GradientSpec::from),
        rowBackground = json.string("rowBackground"),
        separator = json.string("separator"),
        labelColor = json.string("labelColor"),
        secondaryLabelColor = json.string("secondaryLabelColor"),
        headerTextColor = json.string("headerTextColor"),
        headerBackgroundStyle = json.string("headerBackgroundStyle")?.let(HeaderBackgroundStyle::from),
        footerTextColor = json.string("footerTextColor"),
        tintColor = json.string("tintColor"),
        sectionSpacing = json.double("sectionSpacing"),
        font = json.obj("font")?.let(FontSpec::from),
        headerFont = json.obj("headerFont")?.let(FontSpec::from),
        footerFont = json.obj("footerFont")?.let(FontSpec::from),
      )
  }
}

/**
 * The whole payload, as it crosses on the `tree` prop.
 */
data class Tree(
  val sections: List<SectionSpec> = emptyList(),
  val listAppearance: ListAppearance? = null,
  /**
   * Android's Material 3 list style. Ignored on iOS, where the shape comes from `listAppearance`.
   *
   * Defaults to `segmented` for `insetGrouped` and `grouped`, and to `standard` for `plain` —
   * which is the mapping that makes an unchanged cross-platform screen look right on both.
   */
  val androidListStyle: AndroidListStyle? = null,
  val appearance: Appearance? = null,
  /**
   * Applied when the interface style is dark. Falls back to `appearance` field by field, so
   * setting only `appearance` gives you that look in both modes — the least surprising
   * behaviour, and the reason this is not a required counterpart.
   */
  val darkAppearance: Appearance? = null,
) {
  companion object {
    /**
     * Decodes a whole tree, or an empty one if the string is not an object.
     *
     * The only entry point the rest of the library needs, and the reason nothing outside this
     * file imports `org.json`. Malformed input renders an empty list rather than throwing:
     * a crash here would be a crash on a prop update, from a string JavaScript built.
     */
    @JvmStatic
    fun decode(json: String?): Tree {
      if (json.isNullOrEmpty()) return Tree()
      return runCatching { JSONTokener(json).nextValue() as? JSONObject }
        .getOrNull()
        ?.let(::from)
        ?: Tree()
    }

    fun from(json: JSONObject): Tree =
      Tree(
        sections = json.array("sections")?.map { (it as? JSONObject)?.let(SectionSpec::from) ?: SectionSpec() } ?: emptyList(),
        listAppearance = json.string("listAppearance")?.let(ListAppearance::from),
        androidListStyle = json.string("androidListStyle")?.let(AndroidListStyle::from),
        appearance = json.obj("appearance")?.let(Appearance::from),
        darkAppearance = json.obj("darkAppearance")?.let(Appearance::from),
      )
  }
}

// -----------------------------------------------------------------------------
// Reading helpers
//
// `private` on purpose: these are the generated decoders' business and nothing else's. A caller
// outside this file wanting JSON is a caller who should have been handed a decoded type.
//
// Each returns null for "absent or JSON null" — `JSONObject.isNull` is true for both — so the
// generated code can spell "missing takes the default" as a single `?:`.
// -----------------------------------------------------------------------------

private fun JSONObject.string(key: String): String? = if (isNull(key)) null else optString(key)

private fun JSONObject.double(key: String): Double? =
  if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun JSONObject.boolean(key: String): Boolean? = if (isNull(key)) null else optBoolean(key)

private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

/** The raw elements of an array, so callers can spell their own per-element coercion. */
private fun JSONObject.array(key: String): List<Any?>? =
  optJSONArray(key)?.let { array -> List(array.length()) { array.opt(it) } }


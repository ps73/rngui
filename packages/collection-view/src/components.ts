import { tagged, type Children } from './internal/tagged'
import type {
  AutoCapitalize,
  FontSpec,
  SectionLayout,
  ButtonRole,
  DatePickerMode,
  DatePickerStyle,
  KeyboardType,
  MenuItemSpec,
  ReturnKeyType,
  SwipeActionStyle,
} from './tree'

// ---------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------

export interface SectionProps extends Children {
  /**
   * Stable identity. Falls back to the element `key`, then to the section's position.
   */
  id?: string
  /** Header title. Pins to the top of the viewport in the `plain` appearance. */
  header?: string
  /** The grey explanatory text drawn under a group. */
  footer?: string
  /**
   * The letter this section contributes to the A–Z scrubber.
   *
   * Only read when the root sets `showsSectionIndex`. Sections without one are skipped by the
   * scrubber, so a list can mix indexed and unindexed sections.
   */
  indexTitle?: string
  /**
   * `chips` turns the section into a horizontally scrolling strip of pills.
   *
   * Its rows become chips regardless of what they contain, which is deliberate — a chip strip with
   * one stray list row in it is never what anyone meant.
   */
  layout?: SectionLayout
}
export const Section = tagged<SectionProps>('section', 'CollectionView.Section')

export interface RowProps extends Children {
  /**
   * Stable identity for this row, and what makes an insert animate rather than reload.
   *
   * Falls back to the element `key`, then to the row's position within its section.
   * Whatever it resolves to must be unique across the *whole* list, not just the section —
   * see `RowSpec.id`.
   */
  id?: string
  onPress?: () => void
  /** Fixed height in points. Omit to let the cell size itself. */
  height?: number
  /**
   * Overrides the list's font for this row, falling back to it field by field.
   *
   * What a large title field needs: Reminders sets its title noticeably bigger than the notes
   * beneath it, and that is a property of the row rather than of the list.
   */
  font?: FontSpec
}
export const Row = tagged<RowProps>('row', 'CollectionView.Row')

export interface HostProps extends Children {
  id?: string
  /**
   * How much vertical space the row reserves. Omit it and the subtree measures itself.
   *
   * Fabric lays the child out with Yoga, so the view it hands us has no `intrinsicContentSize`
   * and an `.estimated` cell would measure it as zero — the height has to come from
   * JavaScript either way. Left unset, `Root` reads it off the mounted subtree with `onLayout`
   * and sends it back down.
   *
   * **Stating it is still worth doing whenever the number is known.** Measuring costs a
   * render: the row is laid out at a placeholder height for one frame before the real value
   * arrives, which on first mount is a visible settle. A chart told `height={160}` never does
   * that.
   */
  height?: number
  onPress?: () => void
}
/**
 * A row that hosts a real React Native child — a chart, a map, any RN component.
 *
 * Unlike every other component here, this one's children genuinely *are* rendered: they
 * mount as Fabric children of the native view, and native reparents each into the
 * `contentView` of the cell that owns it. That reparenting is legal because the component
 * view overrides `mountChildComponentView:`/`unmountChildComponentView:` itself, which is
 * the whole reason this library is built on codegen rather than Nitro.
 *
 * The cost is that a hosted row cannot be recycled — every one is a distinct React subtree
 * with distinct state, so there is no pool of interchangeable views to draw from. Rows
 * built from the stock kinds recycle normally, so prefer those for anything that repeats,
 * and keep `Host` for the genuinely custom.
 */
export const Host = tagged<HostProps>('host', 'CollectionView.Host')

// ---------------------------------------------------------------------------
// Row slots
// ---------------------------------------------------------------------------

export const Label = tagged<Children>('label', 'CollectionView.Label')

export interface DescriptionProps extends Children {
  /**
   * Draws the text in the tint colour instead of grey.
   *
   * The "Today" / "15:00" under Reminders' Date and Time rows: tinted, the second line reads as the
   * row's current *setting*; grey, it reads as an explanation. Same text, different meaning.
   */
  tinted?: boolean
}

/** Second line under the label. Promotes the row to a `subtitle` cell. */
export const Description = tagged<DescriptionProps>(
  'description',
  'CollectionView.Description'
)

/** Trailing detail text. Promotes the row to a `value` cell. */
export const Value = tagged<Children>('value', 'CollectionView.Value')

/** A trailing disclosure chevron — the "this row pushes a screen" affordance. */
export const Chevron = tagged<Record<string, never>>(
  'chevron',
  'CollectionView.Chevron'
)

/** A trailing checkmark. */
export const Checkmark = tagged<Record<string, never>>(
  'checkmark',
  'CollectionView.Checkmark'
)

export interface IconProps {
  /** SF Symbol name, e.g. `calendar` or `clock`. */
  systemImage: string
  /** Overrides the glyph colour. Defaults to the row's tint. */
  color?: string
}

/**
 * A leading SF Symbol.
 *
 * System symbols rather than image assets: they scale with Dynamic Type, they take a tint, and they
 * need no asset pipeline — which is why Apple's own list rows use them.
 */
export const Icon = tagged<IconProps>('icon', 'CollectionView.Icon')

/** A trailing activity indicator, for a row whose value has not arrived yet. */
export const Spinner = tagged<Record<string, never>>(
  'spinner',
  'CollectionView.Spinner'
)

// ---------------------------------------------------------------------------
// Controls
//
// Each of these is a *slot*, exactly like `Label` — it renders nothing and is read out of the
// element tree. The row's native kind is inferred from which slot it contains, so there is never a
// `kind` prop that can disagree with the children. Callbacks are pulled off these elements into
// the handler registry and keyed by row id, because a function cannot ride inside serialized JSON.
// ---------------------------------------------------------------------------

export interface ToggleProps {
  /** Controlled, like every React form control: native reports a change, you decide. */
  value?: boolean
  onValueChange?: (value: boolean) => void
  disabled?: boolean
}

/** A trailing `UISwitch`. */
export const Switch = tagged<ToggleProps>('switch', 'CollectionView.Switch')

/**
 * A trailing filled/hollow circle — the multi-select affordance.
 *
 * An accessory rather than a row kind, so a `subtitle` row can carry one.
 */
export const Checkbox = tagged<ToggleProps>(
  'checkbox',
  'CollectionView.Checkbox'
)

/**
 * Visually a checkbox, semantically exclusive.
 *
 * UIKit has no radio accessory and no notion of exclusivity in a list — the difference between
 * this and `Checkbox` is that you are expected to clear the siblings yourself.
 */
export const Radio = tagged<ToggleProps>('radio', 'CollectionView.Radio')

export interface TextInputProps {
  value?: string
  onChangeText?: (text: string) => void
  onFocusChange?: (focused: boolean) => void
  placeholder?: string
  keyboardType?: KeyboardType
  autoCapitalize?: AutoCapitalize
  returnKeyType?: ReturnKeyType
  /** Masks input, and disables autofill the way `UITextField.isSecureTextEntry` does. */
  secure?: boolean
  disabled?: boolean
}

/** A single-line text field filling the row. */
export const TextField = tagged<TextInputProps>(
  'textField',
  'CollectionView.TextField'
)

export interface TextAreaProps extends TextInputProps {
  /**
   * Caps growth before the field starts scrolling internally. Unset grows without limit, which
   * is what the Reminders notes field does.
   */
  maxLines?: number
}

/** A multi-line field whose row grows with its content. */
export const TextArea = tagged<TextAreaProps>(
  'textArea',
  'CollectionView.TextArea'
)

export interface MenuProps {
  items: MenuItemSpec[]
  /** The chosen item's id. Shown as the row's trailing value and checked in the menu. */
  value?: string
  onSelect?: (itemId: string) => void
  disabled?: boolean
}

/** A trailing button that presents a `UIMenu` — the iOS Settings picker. */
export const Menu = tagged<MenuProps>('menu', 'CollectionView.Menu')

export interface DatePickerProps {
  /** Milliseconds since the epoch, or a `Date`. */
  value?: number | Date
  onChange?: (millis: number) => void
  mode?: DatePickerMode
  /** `compact` is the tappable pill; `inline` the open calendar; `wheels` the drum. */
  variant?: DatePickerStyle
  minimumDate?: number | Date
  maximumDate?: number | Date
  disabled?: boolean
}

/**
 * A `UIDatePicker`.
 *
 * For the Reminders pattern — a switch that reveals a picker — render the picker row
 * conditionally: `{dateOn && <Row id="date-picker"><DatePicker …/></Row>}`. The diffable data
 * source animates the insert and removal, so declarative state goes in and the expansion comes
 * out with nothing to coordinate.
 */
export const DatePicker = tagged<DatePickerProps>(
  'datePicker',
  'CollectionView.DatePicker'
)

export interface CardProps extends Children {
  /** The prominent line. */
  value?: string
  /** Small text under the value. */
  caption?: string
  /** SF Symbol drawn beside the title. */
  systemImage?: string
  /** Tints the title and the symbol. */
  color?: string
}

/**
 * A rich stacked cell: a title, a prominent value, a caption.
 *
 * The recyclable counterpart to `Host`. Anything a summary card usually needs is *described* here
 * rather than rendered, so a list of them costs pooled cells instead of React subtrees — which is
 * the whole reason to prefer it whenever the content repeats.
 */
export const Card = tagged<CardProps>('card', 'CollectionView.Card')

export interface ButtonProps extends Children {
  /** `destructive` is red; `plain` drops the tint for a neutral action. */
  role?: ButtonRole
  onPress?: () => void
  disabled?: boolean
}

/**
 * A tinted, centred action — "Delete Reminder", "Sign Out".
 *
 * Distinct from a pressable `Row`: that is a row that happens to respond to a tap, this is a
 * button that happens to live in a list, and they look nothing alike.
 */
export const Button = tagged<ButtonProps>('button', 'CollectionView.Button')

// ---------------------------------------------------------------------------
// Swipe actions
// ---------------------------------------------------------------------------

export interface SwipeActionProps {
  /** Unique within its row. Reported back so the right handler runs. */
  id: string
  title?: string
  /** SF Symbol name. UIKit prefers it over the title when both are set. */
  systemImage?: string
  style?: SwipeActionStyle
  /** Overrides the style's own colour. */
  backgroundColor?: string
  onPress?: () => void
}

export const SwipeAction = tagged<SwipeActionProps>(
  'swipeAction',
  'CollectionView.SwipeAction'
)

export interface SwipeActionsProps extends Children {
  /** Which edge to reveal from. Trailing is the iOS default and the one people expect. */
  edge?: 'trailing' | 'leading'
}

/**
 * Groups `SwipeAction`s for one edge of a row.
 *
 * Configured through the *layout* natively, not a delegate:
 * `UICollectionLayoutListConfiguration.trailingSwipeActionsConfigurationProvider` is handed an
 * index path and answers from the row's descriptor.
 */
export const SwipeActions = tagged<SwipeActionsProps>(
  'swipeActions',
  'CollectionView.SwipeActions'
)

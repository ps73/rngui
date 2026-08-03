import {
  Children as ReactChildren,
  Fragment,
  isValidElement,
  type ReactElement,
  type ReactNode,
} from 'react'
import { NODE_TAG } from './internal/tagged'
import { resolveColor } from './appearance'
import type {
  BadgeProps,
  ButtonProps,
  CardProps,
  DatePickerProps,
  DescriptionProps,
  IconProps,
  MenuProps,
  RowProps,
  SectionProps,
  SwipeActionProps,
  SwipeActionsProps,
  TextAreaProps,
  ToggleProps,
} from './components'
import type {
  AccessoryKind,
  RowKind,
  RowSpec,
  SectionActionSpec,
  SectionSpec,
  SwipeActionSpec,
} from './tree'

/**
 * Control slots, in resolution order.
 *
 * A row holds one control. The order decides which wins if a caller writes two, which is reported
 * in `__DEV__` — resolving it deterministically beats rendering whichever the element order
 * happened to put first.
 */
const CONTROL_TAGS = [
  'card',
  'switch',
  'textField',
  'textArea',
  'datePicker',
  'menu',
  'button',
] as const

/** Accessory slots, in resolution order. First one found wins. */
const ACCESSORY_TAGS: ReadonlyArray<readonly [string, AccessoryKind]> = [
  ['chevron', 'disclosure'],
  ['checkmark', 'checkmark'],
  ['checkbox', 'checkbox'],
  ['radio', 'radio'],
  ['spinner', 'spinner'],
]

/**
 * Callbacks live here rather than in the serialized tree.
 *
 * Descriptors cross into native as plain JSON, so a function cannot ride along inside one.
 * Rows are addressed by id instead: native reports `{ rowId }` and the view dispatches to
 * the handler registered here. The registry is rebuilt in the same pass as the descriptors,
 * so a handler can never drift from the row it belongs to.
 */
export interface HandlerRegistry {
  press: Map<string, () => void>
  switchChange: Map<string, (value: boolean) => void>
  textChange: Map<string, (text: string) => void>
  focusChange: Map<string, (focused: boolean) => void>
  dateChange: Map<string, (millis: number) => void>
  menuSelect: Map<string, (itemId: string) => void>
  /**
   * Keyed by row, then by action — native reports `{ rowId, actionId }` because a swipe
   * configuration is built per row and each button inside it carries its own callback.
   */
  swipeAction: Map<string, Record<string, () => void>>
  /** Keyed by *section* id — the one handler that does not belong to a row. */
  sectionAction: Map<string, () => void>
}

export function createRegistry(): HandlerRegistry {
  return {
    press: new Map(),
    switchChange: new Map(),
    textChange: new Map(),
    focusChange: new Map(),
    dateChange: new Map(),
    menuSelect: new Map(),
    swipeAction: new Map(),
    sectionAction: new Map(),
  }
}

/** A `<CollectionView.Host>` child, paired with the height its row reserves. */
export interface HostedChild {
  node: ReactNode
  /**
   * `undefined` when the caller stated no height, which is what tells `Root` to measure this
   * subtree rather than constrain it. The wrapper must then be left unconstrained vertically —
   * giving it a height is what would make `onLayout` report that height straight back.
   */
  height: number | undefined
  /** The row this child belongs to, so a measurement can be filed against it. */
  rowId: string
}

/**
 * Heights measured from mounted subtrees, keyed by row id.
 *
 * Threaded in rather than merged afterwards because the height belongs in the `RowSpec`, and
 * walking the finished section tree to patch it would mean knowing the shape twice.
 */
export type MeasuredHeights = ReadonlyMap<string, number>

/**
 * What a `host` row reserves before anything has measured it.
 *
 * Not zero, which is what an unset height used to mean: a zero-height row is not laid out, so
 * nothing about it is visible until the measurement lands, and a list of them starts as a stack
 * of nothing. One standard row's worth is wrong too, but it is wrong by less and it settles.
 */
export const UNMEASURED_HOST_HEIGHT = 44

/** Shared so that "nothing measured yet" is a stable reference and never re-runs a memo. */
export const EMPTY_MEASUREMENTS: MeasuredHeights = new Map()

export interface Serialized {
  sections: SectionSpec[]
  /**
   * React children of `Host` rows, in the order their rows appear — the one thing that is
   * *not* serialized. They are rendered as real children of the native view so Fabric
   * mounts them, and `RowSpec.hostIndex` is the index into this array.
   */
  hosted: HostedChild[]
}

// ---------------------------------------------------------------------------
// Element-tree helpers
// ---------------------------------------------------------------------------

/**
 * Props are genuinely heterogeneous here — every tagged component has its own shape, and
 * the walker narrows to the right one only after reading the element's tag.
 */
type AnyElement = ReactElement<Record<string, unknown>>

function tagOf(node: ReactNode): string | undefined {
  if (!isValidElement(node)) return undefined
  const type = node.type as unknown as Record<symbol, string> | undefined
  return type?.[NODE_TAG]
}

/**
 * Flattens children into a list of elements, descending transparently into fragments so
 * that `<>…</>` and `{cond && <Row/>}` and `items.map(…)` all behave the way they look.
 */
function elements(children: ReactNode): AnyElement[] {
  const out: AnyElement[] = []
  for (const child of ReactChildren.toArray(children)) {
    if (!isValidElement(child)) continue
    if (child.type === Fragment) {
      const props = child.props as { children?: ReactNode }
      out.push(...elements(props.children))
    } else {
      out.push(child as AnyElement)
    }
  }
  return out
}

/** Concatenates the string and number leaves of a subtree, ignoring everything else. */
function textOf(node: ReactNode): string | undefined {
  let text = ''
  const visit = (value: ReactNode): void => {
    if (value == null || typeof value === 'boolean') return
    if (typeof value === 'string' || typeof value === 'number') {
      text += String(value)
      return
    }
    if (Array.isArray(value)) {
      value.forEach(visit)
      return
    }
    if (isValidElement(value)) {
      visit((value.props as { children?: ReactNode }).children)
    }
  }
  visit(node)
  return text.length > 0 ? text : undefined
}

function findByTag(
  children: AnyElement[],
  tag: string
): AnyElement | undefined {
  return children.find((child) => tagOf(child) === tag)
}

function childrenOf(element: AnyElement): ReactNode {
  return (element.props as { children?: ReactNode }).children
}

/**
 * A key that is unique among an element's *siblings*.
 *
 * `React.Children.toArray` synthesizes keys (`.0`, `.1`, `.$userKey`) for every child, so
 * `element.key` is almost always set — but it is only unique within one parent. Callers
 * have to namespace it, or rows in different sections collide and the diffable data source
 * silently drops the duplicates.
 */
function siblingKey(element: AnyElement, index: number): string {
  return element.key != null ? String(element.key) : String(index)
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/** Accepts a `Date` or raw milliseconds, since JSON has no date type. */
function millisOf(value: number | Date | undefined): number | undefined {
  if (value == null) return undefined
  return value instanceof Date ? value.getTime() : value
}

function serializeRow(
  element: AnyElement,
  rowId: string,
  registry: HandlerRegistry,
  hosted: HostedChild[],
  measured: MeasuredHeights
): RowSpec {
  const tag = tagOf(element)
  const isHost = tag === 'host'
  const props = element.props as unknown as RowProps
  // A Host's children belong to Fabric, so they are never walked as descriptors.
  const children = isHost ? [] : elements(childrenOf(element))

  if (props.onPress != null) registry.press.set(rowId, props.onPress)

  const row: RowSpec = {
    id: rowId,
    kind: 'default',
    selectable: props.onPress != null,
  }
  if (props.height != null) row.height = props.height
  if (props.font != null) row.font = props.font

  if (isHost) {
    row.kind = 'host'
    row.hostIndex = hosted.length
    // A stated height wins outright; otherwise use whatever the last measurement found, and the
    // placeholder until there has been one. `row.height` is never left unset for a host row —
    // native has nothing to fall back on.
    row.height = props.height ?? measured.get(rowId) ?? UNMEASURED_HOST_HEIGHT
    hosted.push({
      node: childrenOf(element),
      height: props.height,
      rowId,
    })
    return row
  }

  const label = findByTag(children, 'label')
  if (label != null) row.label = textOf(childrenOf(label))

  const description = findByTag(children, 'description')
  if (description != null) {
    row.secondaryLabel = textOf(childrenOf(description))
    const { tinted } = description.props as unknown as DescriptionProps
    if (tinted === true) row.secondaryLabelTinted = true
  }

  const icon = findByTag(children, 'icon')
  if (icon != null) {
    const { systemImage, materialSymbol, monogram, color, background, size } =
      icon.props as unknown as IconProps
    if (systemImage != null) row.systemImage = systemImage
    if (materialSymbol != null) row.materialSymbol = materialSymbol
    if (monogram != null) row.imageMonogram = monogram
    const resolved = resolveColor(color)
    if (resolved != null) row.imageColor = resolved
    const resolvedBackground = resolveColor(background)
    if (resolvedBackground != null) row.imageBackground = resolvedBackground
    if (size != null) row.imageSize = size
  }

  const value = findByTag(children, 'value')
  if (value != null) row.value = textOf(childrenOf(value))

  const badge = findByTag(children, 'badge')
  if (badge != null) {
    const text = textOf(childrenOf(badge))
    // Deliberately not inferred as a row kind or an accessory: a badge sits *alongside* whatever
    // the row already has, so a value row with a badge is still a value row.
    if (text !== '') row.badge = text
    const { color } = badge.props as unknown as BadgeProps
    const resolvedBadge = resolveColor(color)
    if (resolvedBadge != null) row.badgeColor = resolvedBadge
  }

  serializeSwipeActions(children, row, rowId, registry)
  // Passed through so a control can distinguish "this row has no tap target" from "the caller
  // deliberately gave the row its own action". A Reminders Date row is both: the switch enables the
  // reminder, and tapping the row collapses the picker.
  const control = serializeControl(
    children,
    row,
    rowId,
    registry,
    props.onPress != null
  )
  serializeAccessory(children, row, rowId, registry)

  // Inferred rather than declared: a row containing a control *is* that kind of row, one with a
  // second line *is* a subtitle cell, and one with trailing detail text *is* a value cell. Making
  // the caller restate it in a `kind` prop would be a second source of truth that can disagree
  // with the children.
  row.kind =
    control ??
    (row.secondaryLabel != null
      ? 'subtitle'
      : row.value != null
        ? 'value'
        : 'default')

  return row
}

/**
 * Reads whichever control slot the row contains, and returns the kind it implies.
 *
 * Order is priority: a row with both a `TextField` and a `Switch` is malformed, and resolving it
 * silently beats rendering something arbitrary. Reported in `__DEV__` rather than thrown, because
 * a mistake in one row should not blank the whole list.
 */
function serializeControl(
  children: AnyElement[],
  row: RowSpec,
  rowId: string,
  registry: HandlerRegistry,
  rowHasPress: boolean
): RowKind | undefined {
  const found = CONTROL_TAGS.filter((tag) => findByTag(children, tag) != null)
  if (found.length === 0) return undefined
  if (__DEV__ && found.length > 1) {
    console.error(
      `[@rngui/collection-view] Row "${rowId}" contains more than one control ` +
        `(${found.join(', ')}). Only ${found[0]} is used — a row holds one control.`
    )
  }

  const tag = found[0]!
  const element = findByTag(children, tag)!
  const props = element.props as Record<string, unknown>
  if (props.disabled === true) row.disabled = true

  switch (tag) {
    case 'switch': {
      const { value, onValueChange } = props as ToggleProps
      row.on = value ?? false
      if (onValueChange != null) registry.switchChange.set(rowId, onValueChange)
      // The switch is its own tap target, so the row is not selectable *unless* the caller gave it
      // an action of its own. Without that exception a row could not both carry a switch and respond
      // to a tap, which is exactly what an expandable Date row is.
      row.selectable = rowHasPress
      return 'switch'
    }
    case 'textField':
    case 'textArea': {
      const input = props as TextAreaProps
      row.text = input.value ?? ''
      if (input.placeholder != null) row.placeholder = input.placeholder
      if (input.keyboardType != null) row.keyboardType = input.keyboardType
      if (input.autoCapitalize != null)
        row.autoCapitalize = input.autoCapitalize
      if (input.returnKeyType != null) row.returnKeyType = input.returnKeyType
      if (input.secure != null) row.secure = input.secure
      if (tag === 'textArea' && input.maxLines != null) {
        row.maxLines = input.maxLines
      }
      if (input.onChangeText != null) {
        registry.textChange.set(rowId, input.onChangeText)
      }
      if (input.onFocusChange != null) {
        registry.focusChange.set(rowId, input.onFocusChange)
      }
      row.selectable = rowHasPress
      return tag
    }
    case 'menu': {
      const menu = props as unknown as MenuProps
      row.menuItems = menu.items
      if (menu.value != null) row.selectedItemId = menu.value
      if (menu.onSelect != null) registry.menuSelect.set(rowId, menu.onSelect)
      row.selectable = rowHasPress
      return 'menu'
    }
    case 'datePicker': {
      const picker = props as DatePickerProps
      const millis = millisOf(picker.value)
      // Defaulted here rather than natively so that "no value" means *now* at the moment the tree
      // was built, which is what an uncontrolled picker should show — and so the value native
      // reports back matches what it displays from the first frame.
      row.dateMillis = millis ?? Date.now()
      if (picker.mode != null) row.datePickerMode = picker.mode
      if (picker.variant != null) row.datePickerStyle = picker.variant
      const min = millisOf(picker.minimumDate)
      const max = millisOf(picker.maximumDate)
      if (min != null) row.minDateMillis = min
      if (max != null) row.maxDateMillis = max
      if (picker.onChange != null)
        registry.dateChange.set(rowId, picker.onChange)
      row.selectable = rowHasPress
      return 'datePicker'
    }
    case 'card': {
      const card = props as unknown as CardProps
      row.label = textOf(childrenOf(element)) ?? row.label
      if (card.value != null) row.value = card.value
      if (card.caption != null) row.secondaryLabel = card.caption
      if (card.systemImage != null) row.systemImage = card.systemImage
      const color = resolveColor(card.color)
      if (color != null) row.tintColor = color
      row.selectable = rowHasPress
      return 'card'
    }
    case 'button': {
      const button = props as ButtonProps
      row.label = textOf(childrenOf(element))
      if (button.role != null) row.role = button.role
      if (button.onPress != null) registry.press.set(rowId, button.onPress)
      row.selectable = button.onPress != null && button.disabled !== true
      return 'button'
    }
  }
}

/**
 * The trailing decoration, which is a *separate* axis from the control.
 *
 * `Checkbox` and `Radio` land here rather than becoming row kinds, which is what lets a subtitle
 * row carry one. Their tap target is the whole row — UIKit has no tappable checkbox accessory in a
 * list — so a toggle callback becomes a press handler that inverts the current value.
 */
function serializeAccessory(
  children: AnyElement[],
  row: RowSpec,
  rowId: string,
  registry: HandlerRegistry
): void {
  for (const [tag, kind] of ACCESSORY_TAGS) {
    const element = findByTag(children, tag)
    if (element == null) continue
    row.accessory = kind

    if (kind === 'checkbox' || kind === 'radio') {
      const { value, onValueChange, disabled } = element.props as ToggleProps
      row.on = value ?? false
      if (disabled === true) row.disabled = true
      if (onValueChange != null && disabled !== true) {
        const next = !(value ?? false)
        registry.press.set(rowId, () => onValueChange(next))
        row.selectable = true
      }
    }
    return
  }
}

function serializeSwipeActions(
  children: AnyElement[],
  row: RowSpec,
  rowId: string,
  registry: HandlerRegistry
): void {
  const groups = children.filter((child) => tagOf(child) === 'swipeActions')
  if (groups.length === 0) return

  const handlers: Record<string, () => void> = {}

  for (const group of groups) {
    const { edge = 'trailing' } = group.props as unknown as SwipeActionsProps
    const specs: SwipeActionSpec[] = []

    for (const action of elements(childrenOf(group))) {
      if (tagOf(action) !== 'swipeAction') continue
      const props = action.props as unknown as SwipeActionProps
      const spec: SwipeActionSpec = { id: props.id }
      if (props.title != null) spec.title = props.title
      if (props.systemImage != null) spec.systemImage = props.systemImage
      if (props.style != null) spec.style = props.style
      // Normalised here for the same reason appearance colours are: native parses `#RRGGBBAA`
      // and nothing else, but callers write `'red'` and theming libraries emit whatever they emit.
      const background = resolveColor(props.backgroundColor)
      if (background != null) spec.backgroundColor = background
      specs.push(spec)
      if (props.onPress != null) handlers[props.id] = props.onPress
    }

    if (specs.length === 0) continue
    if (edge === 'leading') row.leadingActions = specs
    else row.trailingActions = specs
  }

  if (Object.keys(handlers).length > 0) {
    registry.swipeAction.set(rowId, handlers)
  }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

const ROW_TAGS: ReadonlySet<string> = new Set(['row', 'host'])

/**
 * Walks the compound-component tree into the flat descriptors the native view takes,
 * filling `registry` with the callbacks found along the way.
 *
 * Rows written directly under the root are gathered into one implicit unheaded section, so
 * a short list does not need a `<Section>` wrapper.
 */
export function serialize(
  children: ReactNode,
  registry: HandlerRegistry,
  measured: MeasuredHeights = EMPTY_MEASUREMENTS
): Serialized {
  const sections: SectionSpec[] = []
  const hosted: HostedChild[] = []

  const rowIdFor = (
    child: AnyElement,
    sectionId: string,
    index: number
  ): string => {
    const explicit = (child.props as unknown as RowProps).id
    return explicit ?? `${sectionId}/${siblingKey(child, index)}`
  }

  const buildRows = (rows: AnyElement[], sectionId: string): RowSpec[] =>
    rows.map((child, index) =>
      serializeRow(
        child,
        rowIdFor(child, sectionId, index),
        registry,
        hosted,
        measured
      )
    )

  let loose: AnyElement[] = []
  const flushLoose = () => {
    if (loose.length === 0) return
    const sectionId = `s${sections.length}`
    sections.push({ id: sectionId, rows: buildRows(loose, sectionId) })
    loose = []
  }

  for (const child of elements(children)) {
    const tag = tagOf(child)
    if (tag === 'section') {
      flushLoose()
      const props = child.props as unknown as SectionProps
      const sectionId = props.id ?? `s${siblingKey(child, sections.length)}`
      const section: SectionSpec = {
        id: sectionId,
        rows: buildRows(
          elements(childrenOf(child)).filter((row) =>
            ROW_TAGS.has(tagOf(row) ?? '')
          ),
          sectionId
        ),
      }
      if (props.header != null) section.header = props.header
      if (props.footer != null) section.footer = props.footer
      if (props.indexTitle != null) section.indexTitle = props.indexTitle
      if (props.layout != null) section.layout = props.layout
      if (props.action != null) {
        const { title, systemImage, disabled, onPress } = props.action
        if (__DEV__ && props.header == null) {
          console.warn(
            `[@rngui/collection-view] Section "${sectionId}" has an \`action\` but no \`header\`. ` +
              'UIKit builds no header view for a section that asked for none, so the action has ' +
              'nowhere to draw and is ignored.'
          )
        }
        const spec: SectionActionSpec = {}
        if (title != null) spec.title = title
        if (systemImage != null) spec.systemImage = systemImage
        if (disabled === true) spec.disabled = true
        section.action = spec
        if (onPress != null && disabled !== true) {
          registry.sectionAction.set(sectionId, onPress)
        }
      }
      // A chip section's rows *are* chips, whatever they contain. Overriding the inferred kind here
      // rather than asking the caller to say it twice keeps the section as the single source of
      // truth for its own layout.
      if (props.layout === 'chips') {
        for (const row of section.rows) row.kind = 'chip'
      }
      sections.push(section)
    } else if (tag != null && ROW_TAGS.has(tag)) {
      loose.push(child)
    }
  }
  flushLoose()

  if (__DEV__) warnOnDuplicateIds(sections)

  // Structure only. Appearance is composed by `Root`, which owns the presets and the colour
  // normalisation — keeping the two apart means a theme change never walks the element tree.
  return { sections, hosted }
}

/**
 * Row and section ids become diffable-data-source identifiers, which have to be unique.
 *
 * **A duplicate is a crash, not a cosmetic problem.** `NSDiffableDataSourceSnapshot` raises
 * `NSInternalInconsistencyException` — *"Fatal: supplied item identifiers are not unique"* — from
 * `appendItems`, and the same from `appendSections`. Native deduplicates before it builds the
 * snapshot so that a release build degrades to a missing row instead of a dead app, but the
 * missing row is still wrong and the cause is only visible from here.
 *
 * Sections are checked as well as rows, in their own namespace: a section id collides only with
 * another section id, while a row id has to be unique across the whole tree, because that is the
 * scope each identifier lives in natively.
 */
function warnOnDuplicateIds(sections: SectionSpec[]): void {
  const seenSections = new Set<string>()
  const duplicateSections = new Set<string>()
  const seenRows = new Set<string>()
  const duplicateRows = new Set<string>()

  for (const section of sections) {
    if (seenSections.has(section.id)) duplicateSections.add(section.id)
    else seenSections.add(section.id)

    for (const row of section.rows) {
      if (seenRows.has(row.id)) duplicateRows.add(row.id)
      else seenRows.add(row.id)
    }
  }

  if (duplicateSections.size > 0) {
    console.error(
      `[@rngui/collection-view] Duplicate section ids: ${[...duplicateSections].join(', ')}. ` +
        'Native drops the repeats to avoid a crash, so those sections and every row in them ' +
        'will not appear — give each `Section` a unique `id`.'
    )
  }

  if (duplicateRows.size > 0) {
    console.error(
      `[@rngui/collection-view] Duplicate row ids: ${[...duplicateRows].join(', ')}. ` +
        'Row ids must be unique across the whole list, not just within a section. Native drops ' +
        'the repeats to avoid a crash, so those rows will not appear — give each row a unique ' +
        '`id`.'
    )
  }
}

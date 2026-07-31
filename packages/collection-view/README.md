# @rngui/collection-view

A real iOS `UICollectionView` for React Native — `UICollectionViewListCell`,
`UICollectionViewCompositionalLayout` and `UICollectionViewDiffableDataSource`, driven by a
Radix-style compound API.

Not a reimplementation of a list. The cells are UIKit's, the recycling is UIKit's, the swipe
actions, the pressed states, the section-header pinning and the large-title collapse are all
UIKit's. What this package adds is a way to describe them from React.

```bash
npm install @rngui/collection-view
```

Requires the New Architecture. **iOS only today** — Android is a stub that accepts every prop and
renders nothing, so shared screens keep building and running while the `RecyclerView` backend is
written.

## A first list

```tsx
import { CollectionView } from '@rngui/collection-view'

export function Settings() {
  return (
    <CollectionView.Root>
      <CollectionView.Section
        header="General"
        footer="Applies to this device only."
      >
        <CollectionView.Row id="wifi" onPress={openWifi}>
          <CollectionView.Icon systemImage="wifi" />
          <CollectionView.Label>Wi-Fi</CollectionView.Label>
          <CollectionView.Value>Not Connected</CollectionView.Value>
          <CollectionView.Chevron />
        </CollectionView.Row>

        <CollectionView.Row id="airplane">
          <CollectionView.Label>Airplane Mode</CollectionView.Label>
          <CollectionView.Switch value={airplane} onValueChange={setAirplane} />
        </CollectionView.Row>
      </CollectionView.Section>
    </CollectionView.Root>
  )
}
```

**The children are never mounted.** Everything except `Host` renders `null` and exists only to be
read: the tree is walked into flat descriptors, handed across as one JSON string, and UIKit builds
and recycles its own cells from it. A thousand rows are a thousand descriptors and however many
cells fit on screen.

That is also why a row's _kind_ is never stated. It is inferred from which slots the row fills — a
row with a `Value` is a value cell, one with a `Description` is a subtitle cell — so there is no
second source of truth to disagree with the children.

## Components

| Structure |                                                          |
| --------- | -------------------------------------------------------- |
| `Root`    | The list itself. Props below.                            |
| `Section` | `id` · `header` · `footer` · `indexTitle` · `layout`     |
| `Row`     | `id` · `onPress` · `height` · `font`                     |
| `Host`    | `id` · `height` · `onPress` — hosts a real React subtree |

| Row slots     |                                                                    |
| ------------- | ------------------------------------------------------------------ |
| `Label`       | The title line                                                     |
| `Description` | Second line. `tinted` draws it in the tint colour rather than grey |
| `Value`       | Trailing detail text                                               |

| Accessories                         |                                        |
| ----------------------------------- | -------------------------------------- |
| `Icon`                              | `systemImage` · `color` — an SF Symbol |
| `Chevron` · `Checkmark` · `Spinner` |                                        |
| `Checkbox` · `Radio`                | `value` · `onValueChange` · `disabled` |

| Controls     |                                                                                                                                          |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `Switch`     | `value` · `onValueChange` · `disabled`                                                                                                   |
| `TextField`  | `value` · `onChangeText` · `onFocusChange` · `placeholder` · `keyboardType` · `autoCapitalize` · `returnKeyType` · `secure` · `disabled` |
| `TextArea`   | the above plus `maxLines` — grows with its content                                                                                       |
| `Menu`       | `items` · `value` · `onSelect` · `disabled` — a `UIMenu`                                                                                 |
| `DatePicker` | `value` · `onChange` · `mode` · `variant` · `minimumDate` · `maximumDate`                                                                |
| `Button`     | `role` · `onPress` · `disabled`                                                                                                          |
| `Card`       | `value` · `caption` · `systemImage` · `color` — a rich stacked cell that still recycles                                                  |

| Swipe actions  |                                                                          |
| -------------- | ------------------------------------------------------------------------ |
| `SwipeActions` | `edge` — `'trailing'` (default) or `'leading'`                           |
| `SwipeAction`  | `id` · `title` · `systemImage` · `style` · `backgroundColor` · `onPress` |

Enums: `keyboardType` is `default | numeric | decimal | email | phone | url | asciiCapable`,
`autoCapitalize` is `none | sentences | words | characters`, `returnKeyType` is
`default | done | go | next | search | send`, `mode` is `date | time | dateAndTime`, `variant` is
`compact | inline | wheels`, `role` is `default | destructive | plain`, and a swipe action's
`style` is `normal | destructive`.

## `Root`

**Layout.** `listAppearance` is `insetGrouped` (the Settings look, and the default), `grouped`, or
`plain` — the last pins section headers to the top of the viewport as you scroll.

**Appearance.** `appearance` and `darkAppearance` take the same shape, and anything left unset keeps
the platform's own value:

```tsx
<CollectionView.Root
  appearance={{ tintColor: '#0FA3A3', font: { design: 'rounded' } }}
  darkAppearance={{ tintColor: '#5AC8C8' }}
/>
```

Fields: `background` · `backgroundGradient` · `rowBackground` · `separator` · `labelColor` ·
`secondaryLabelColor` · `headerTextColor` · `headerBackgroundStyle` · `footerTextColor` ·
`tintColor` · `sectionSpacing` · `font` · `headerFont` · `footerFont`. A `FontSpec` is
`{ family, design, size, weight, variations, scaled }`, where `design` is
`default | rounded | serif | monospaced` — the `ui-rounded` family and friends.

**The two appearances resolve natively, not in JavaScript.** Each colour crosses as a light/dark
pair and becomes a `UIColor(dynamicProvider:)`, so switching interface style restyles the whole list
without a React render. `darkAppearance` deliberately does _not_ inherit `appearance` field by
field — that is what keeps "fall back to the platform's own colour" expressible.

`inverted` swaps the grouped look: a plain background with tinted rows rather than iOS's tinted
background with plain cards. It is a preset, so anything in `appearance` still wins.

`colorScheme` (`system | light | dark`) pins the interface style rather than following the device.
Needed whenever the app has its own theme switch, because `UIListContentConfiguration` draws labels,
separators and accessories with _system_ colours that follow the device.

**Insets and keyboard.** `contentInset` · `contentInsetAdjustmentBehavior` ·
`automaticallyAdjustContentInsets` · `automaticallyAdjustsScrollIndicatorInsets` ·
`automaticallyAdjustKeyboardInsets` · `keyboardAware` · `keyboardAwareOffset` ·
`keyboardDismissMode`, named as `ScrollView` names them because they are the same `UIScrollView`
properties.

Two deliberate divergences. `contentInsetAdjustmentBehavior` defaults to `automatic` rather than
`ScrollView`'s `never`, because this is normally a screen's only scroll view under a native stack
and `never` leaves the content starting behind the navigation bar. And `keyboardAware` targets the
**caret** rather than the row, which is the difference between seeing the line you are typing and
seeing the middle of a tall text area.

**Scrolling.** `scrollEnabled` · `decelerationRate` · `showsVerticalScrollIndicator` ·
`sectionIndex` · `onScroll` · `onContentSizeChange` · `onVisibleRangeChange`.

`sectionIndex` is the A–Z scrubber, built from each section's `indexTitle`. Pass `true` or
`{ rowHeight, callout }`. Sections without an `indexTitle` are skipped rather than given a blank
stop, and when there is not enough vertical room the bar thins itself out with `•` separators the
way the system one does.

The scroll callbacks are what turn the native events on — a list that does not listen never pays for
them. If you attach a reanimated handler instead (which subscribes by view tag, not by prop), set
`tracksScroll` so native knows somebody is listening.

## Hosting React children

`Host` is the escape hatch for content no row kind describes — a chart, a map, a custom control:

```tsx
<CollectionView.Host id="chart" height={160}>
  <MyChart />
</CollectionView.Host>
```

Its children genuinely _are_ rendered: they mount as Fabric children of the native view, and native
reparents each into the `contentView` of the cell that owns it. The child is a real subview — UIKit
clips it, hit-tests it and scrolls it, with no floating overlay and no per-frame repositioning.

Omit `height` and the subtree measures itself: `Root` reads it with `onLayout` and sends it back
down. State it whenever you know it, though — measuring costs one extra render, which on first mount
is a visible settle.

**A hosted row cannot recycle.** Every one is a distinct React subtree with distinct state, so there
is no pool of interchangeable views to draw from. Prefer `Card` for anything that repeats, and for a
long list of hosted rows, window them:

```tsx
const [range, setRange] = useState({ firstIndex: 0, lastIndex: 0 })

<CollectionView.Root onVisibleRangeChange={setRange}>
  {rows.map((row, index) => (
    <CollectionView.Host key={row.id} id={row.id} height={72}>
      {index >= range.firstIndex - 4 && index <= range.lastIndex + 4 ? <Row {...row} /> : null}
    </CollectionView.Host>
  ))}
</CollectionView.Root>
```

The rows all still exist — same ids, same heights, same place in the scroll — so nothing jumps. Only
the content comes and goes. The indices are into the flattened row list, every section's rows
concatenated in tree order.

## Bottom sheets

```tsx
import { BottomSheetCollectionView } from '@rngui/collection-view/bottom-sheet'
```

A separate entry point, because importing it pulls in `@gorhom/bottom-sheet`,
`react-native-reanimated` and `react-native-gesture-handler` — all optional peers, so the cost falls
on the import rather than on everyone who renders a list.

It takes `Root`'s props, minus the few the sheet owns. Two things are worth knowing:
`contentInsetAdjustmentBehavior` is pinned to `never` (the sheet locks the list by scrolling it to
`0`, which only works if `0` is the top), so pass `contentInset={{ bottom }}` yourself if the sheet
reaches the screen edge. And `onScroll` arrives wrapped in `{ nativeEvent }` here, because gorhom
re-wraps the event before handing it on.

## Why React Native codegen, not Nitro

Nitro is a better authoring experience for almost everything, and it cannot do the one thing this
component is built around. Nitro assigns its view as a Fabric component's `contentView`, so React's
children arrive as _siblings_, and `RCTViewComponentView`'s default mount/unmount assert that a
child is exactly where React put it. A hosted view can then only be floated _over_ its cell.

A hand-written Fabric component view overrides `mountChildComponentView:` /
`unmountChildComponentView:` and owns child placement outright — the same trick
`react-native-screens` uses to move children into view controllers. That is what makes
`<CollectionView.Host>` a genuine subview of `cell.contentView`.

## Android

The view manager accepts the full prop contract and renders nothing, and `Root` withholds hosted
children there rather than piling them at the origin. In development it warns once, in Metro's
console, so a blank list is never a mystery.

It is a `ViewGroupManager` rather than a `SimpleViewManager` deliberately: the iOS component hosts
arbitrary React children, and a manager whose view is not a group makes the mounting layer throw the
moment one arrives — which would turn "not implemented yet" into "importing this crashes the app".

The manager implements the _generated_ codegen interface rather than declaring its own
`@ReactProp` setters, so the Kotlin compiler is what keeps it in step with the TypeScript spec.

## License

AGPL-3.0-only.

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

Requires the New Architecture. **iOS and Android**: a real `UICollectionView` on one, a real
`RecyclerView` on the other. Each platform gets its own idiom rather than one drawn in the other's
colours — see [Platform differences](#platform-differences) for where they deliberately diverge, and
[what does nothing on Android](#documented-no-ops) for the short list of props that are accepted and
ignored.

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

| Structure |                                                                         |
| --------- | ----------------------------------------------------------------------- |
| `Root`    | The list itself. Props below.                                           |
| `Section` | `id` · `header` · `footer` · `indexTitle` · `layout` · `action`         |
| `Row`     | `id` · `onPress` · `height` · `font`                                    |
| `Host`    | `id` · `height` · `background` · `onPress` — hosts a real React subtree |

| Row slots     |                                                                    |
| ------------- | ------------------------------------------------------------------ |
| `Label`       | The title line                                                     |
| `Description` | Second line. `tinted` draws it in the tint colour rather than grey |
| `Value`       | Trailing detail text                                               |

| Accessories                         |                                                                                 |
| ----------------------------------- | ------------------------------------------------------------------------------- |
| `Icon`                              | `systemImage` · `materialSymbol` · `monogram` · `color` · `background` · `size` |
| `Badge`                             | `color` — the red count bubble, text as children                                |
| `Chevron` · `Checkmark` · `Spinner` |                                                                                 |
| `Checkbox` · `Radio`                | `value` · `onValueChange` · `disabled`                                          |

| Controls     |                                                                                                                                                   |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Switch`     | `value` · `onValueChange` · `disabled`                                                                                                            |
| `TextField`  | `value` · `onChangeText` · `onFocusChange` · `placeholder` · `keyboardType` · `autoCapitalize` · `returnKeyType` · `secure` · `unit` · `disabled` |
| `TextArea`   | the above except `unit`, plus `maxLines` — it grows with its content, and a suffix has no line to sit on                                          |
| `Menu`       | `items` · `value` · `onSelect` · `disabled` — a `UIMenu`                                                                                          |
| `DatePicker` | `value` · `onChange` · `mode` · `variant` · `minimumDate` · `maximumDate`                                                                         |
| `Slider`     | `value` · `onValueChange` · `onSlidingComplete` · `minimumValue` · `maximumValue` · `step` · `minimumImage` · `maximumImage` · `disabled`         |
| `Button`     | `role` · `onPress` · `disabled`                                                                                                                   |
| `Card`       | `value` · `caption` · `systemImage` · `color` — a rich stacked cell that still recycles                                                           |

| Swipe actions  |                                                                          |
| -------------- | ------------------------------------------------------------------------ |
| `SwipeActions` | `edge` — `'trailing'` (default) or `'leading'`                           |
| `SwipeAction`  | `id` · `title` · `systemImage` · `style` · `backgroundColor` · `onPress` |

**Both edges.** A row takes one `SwipeActions` group per edge. `edge` defaults to `'trailing'`,
revealed by swiping left; `'leading'` is revealed by swiping right, the same handedness on both
platforms:

```tsx
<CollectionView.Row id={task.id}>
  <CollectionView.Label>{task.title}</CollectionView.Label>
  <CollectionView.SwipeActions>
    <CollectionView.SwipeAction
      id="delete"
      title="Delete"
      systemImage="trash"
      style="destructive"
      onPress={() => remove(task.id)}
    />
  </CollectionView.SwipeActions>
  <CollectionView.SwipeActions edge="leading">
    <CollectionView.SwipeAction
      id="complete"
      title="Complete"
      systemImage="checkmark.circle"
      backgroundColor="#34C759"
      onPress={() => complete(task.id)}
    />
  </CollectionView.SwipeActions>
</CollectionView.Row>
```

An `id` is unique per **row**, not per edge — a row's handlers are keyed by action id alone, so
`delete` on both edges would keep only the last one registered. Nothing warns about it.

Pressing an action never removes the row. Native reports the tap and springs the row back, and the
row leaves on the next commit as an animated diff, so the layout and the data source never disagree
about whether it is gone.

**On Android, a swipe competes with the system back gesture**, and this is the failure people
actually hit. Back is an inward drag from _either_ screen edge, so a leading swipe collides with it
on the left exactly as a trailing swipe does on the right. The list publishes
`systemGestureExclusionRects` for the rows that carry actions, which is what makes swiping work at
all on a gesture-navigation device. But the platform rations that budget to **200dp per edge** and
drops the rest, keeping the topmost rects — so on a list where every row is swipeable, rows past
roughly the first four still lose near the screen edge. Invisible on an emulator with three-button
navigation.

An `Icon` is grey by default, not tinted — in a list these are labels for the row, and a tinted
glyph reads as an interactive control. Give it a `background` and it becomes the platform's own
icon container instead — Settings' 29pt continuous-corner square on iOS, M3's 40dp circle on
Android — rendered once per symbol-and-colour and cached, with the layout width reserved so
untiled rows in the same section still line up. `monogram` puts one or two letters in that
container rather than a glyph, which is the avatar an address book falls back to; that one is a
circle on both, because an avatar always has been. A `size` reserves its width the same way, which is what keeps a large glyph from
eating the row's leading margin.

A `Badge` takes its text as children rather than a number, because iOS puts version strings and a
bare `!` in the same bubble. It sits _inside_ the disclosure chevron rather than replacing it, and
it is not a row kind — a value row with a badge is still a value row.

A `Section`'s `action` is the "Show All" beside a header title —
`{ title?, systemImage?, disabled?, onPress? }`. It becomes a real `UIButton` in the header's own
trailing accessory slot, so it lines up with the disclosure chevrons below it and keeps its own
pressed and disabled states. It needs a `header` to attach to: UIKit builds no header view for a
section that asked for none, and `__DEV__` warns rather than dropping the action silently.

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
  appearance={{ tintColor: '#0FA3A3', font: { family: 'ui-rounded' } }}
  darkAppearance={{ tintColor: '#5AC8C8' }}
/>
```

Fields: `background` · `backgroundGradient` · `rowBackground` · `separator` · `labelColor` ·
`secondaryLabelColor` · `headerTextColor` · `headerBackgroundStyle` · `footerTextColor` ·
`tintColor` · `sectionSpacing` · `font` · `headerFont` · `footerFont`. A `FontSpec` is
`{ family, size, weight, variations, scaled }`.

**`family` takes the same names React Native's `<Text>` takes.** Five of them are generic —
`system-ui`, `ui-sans-serif`, `ui-serif`, `ui-rounded`, `ui-monospace` — and they are CSS's names,
which React Native's iOS text layer already maps onto `UIFontDescriptor.SystemDesign`. Using the
same spelling means one vocabulary on a screen that mixes rows and `<Text>`, instead of two names
for SF Rounded depending on which component is asking. On Android they resolve to the matching
system face, with `ui-rounded` degrading to the default one because Android has none.

Anything else is a font your app registered — with `expo-font`, the face is handed to Core Text and
resolved here by name, so no per-row styling is involved. Leaving `family` unset is different again:
it keeps whatever face the slot normally uses, which is how `font: { size: 20 }` on a section header
stays a header rather than collapsing to a plain system label.

`variations` drives a variable font's axes, written flat: `'wght=550,opsz=20'`. This is the only
way to reach a weight that is not one of the nine `weight` names, because those nine are the only
files a static family ships — an axis is continuous, so `wght=350` is as real as `wght=400`. Axes
the face does not expose are ignored, which makes one spec safe to share with a static family.

**The two appearances resolve natively, not in JavaScript.** Each colour crosses as a light/dark
pair and becomes a `UIColor(dynamicProvider:)`, so switching interface style restyles the whole list
without a React render. `darkAppearance` deliberately does _not_ inherit `appearance` field by
field — that is what keeps "fall back to the platform's own colour" expressible.

`headerBackgroundStyle` decides how a **pinned** header paints itself, so it only means anything in
the `plain` appearance. `opaque` hides the rows passing under it; `blurred` is a material that stops
in a straight line, which is how iOS drew these before 26; `soft` fades that material out so there
is no line at all, matching what `UIScrollEdgeEffect`'s soft style does to a navigation bar.

**`transparent` is what the system Contacts app actually uses**, and it is worth saying because the
other three look like the obvious answers. There is no background at all: the letter is a small grey
glyph floating over the rows, and the only thing softening the top of the screen is the navigation
bar's own scroll edge effect. Any material is one surface too many, and it reads as a second edge
travelling down the screen.

That only works when each row leads with something the letter can pass over — Contacts has an
avatar in every row. Over a bare label it is unreadable, and that is when the other three earn
their keep.

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

**Pull to refresh.** `refreshControl` takes a `RefreshControl` element, exactly as `ScrollView`
takes one:

```tsx
<CollectionView.Root
  refreshControl={
    <RefreshControl refreshing={busy} onRefresh={reload} tintColor="#0FA3A3" />
  }
/>
```

`refreshing` and `onRefresh` are also accepted directly, which is `FlatList`'s shorthand for the
same thing. The element wins if both are given.

**The element is read, never rendered.** Its props are unpacked onto the native view, which drives a
real `UIRefreshControl` and a real `SwipeRefreshLayout` — so it can be React Native's own
`RefreshControl` or anything carrying the same props, but it never mounts, never runs an effect, and
never appears in the hierarchy as itself. That is not a shortcut: the React children of this
component are _exactly_ the `Host` subtrees, addressed positionally, so one extra mounted child
would shift every hosted row. And on Android React Native's control is a wrapper _around_ the
scrollable, which could not have been a child of it in any case.

`refreshing` is **controlled**, as `RefreshControl` documents. Set it to `true` inside `onRefresh` or
the spinner stops on the next render. A prop cannot express that on its own — a pull starts the
spinner natively while JavaScript still says `false`, so a caller who does nothing changes no prop
and there would be nothing to stop it. `Root` notices the disagreement after its own render and
corrects it with a native command, which is what React Native's `RefreshControl` does too.

`enabled` is React Native's Android-only prop and works on both here; the divergence is additive.
`colors`, `progressBackgroundColor` and `size` are Android's, and do nothing on iOS — a
`UIRefreshControl` has one colour and one size. `title` and `titleColor` are the mirror image. There
is no `horizontal` mode to guard against: this list is vertical by construction.

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

**The cell draws no background unless you ask for one.** Pass `background="card"` and the row takes
the section's background, corner treatment and separators exactly as a described row in the same
section does, theme changes included:

```tsx
<CollectionView.Host id="chart" height={160} background="card">
  <MyChart />
</CollectionView.Host>
```

The default is `"none"` because a hosted subtree usually brings its own surface, and a card behind
one that does reads as two cards with mismatched corners. A row that opts out draws no separators
either — fencing a backgroundless row between two hairlines is not opting out of anything.

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

**`refreshControl` is dropped here**, and it is the one prop that is real on `Root` and deliberately
not forwarded. Inside a sheet the pull and the sheet's own collapse are the same gesture: at
`contentOffset 0` the list cannot scroll up, so gesture-handler never activates and the sheet keeps
the drag — and below the tallest detent gorhom locks the list outright, which switches the refresh
layout off on Android and stops the rubber-band on iOS. A control that renders and never fires is
worse than one that was never accepted. `@gorhom/bottom-sheet` has the same conflict with a plain
`BottomSheetScrollView`.

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

A `RecyclerView` with Material 3 grouped cards, not a `FlatList` in a trench coat. The manager
implements the _generated_ codegen interface rather than declaring its own `@ReactProp` setters, so
the Kotlin compiler is what keeps it in step with the TypeScript spec — a prop cannot be added on
one side and forgotten on the other.

The descriptor model is generated too: `scripts/gen-kotlin-types.mjs` emits `data class`es and
hand-written `org.json` decoders from the same `src/tree.ts` the Swift model comes from, and both
platforms decode the _same_ fixture files in their tests. kotlinx.serialization is not used because
it is a compiler plugin, and React Native app templates — Expo's included — do not put one on the
root project's classpath.

`prop → native` in one place:

| Prop                             | Android                                                                                     |
| -------------------------------- | ------------------------------------------------------------------------------------------- |
| `listAppearance`                 | grouped cards with first/last/middle corner shapes; `plain` is edge-to-edge                 |
| `appearance` / `darkAppearance`  | resolved against the configuration; a theme flip rebinds the visible rows with no JS commit |
| `colorScheme`                    | overrides the device's night mode for this list                                             |
| `sectionIndex`                   | a fast-scroller thumb with a letter bubble — **not** an A–Z rail                            |
| `contentInset*`                  | padding with `clipToPadding = false`, so rows scroll _through_ the inset                    |
| `contentInsetAdjustmentBehavior` | system-bar insets; `never` applies none                                                     |
| `decelerationRate`               | `0` suppresses the fling exactly; other values approximate through fling velocity           |
| `scrollTo`                       | `scrollToPosition(0)` for the `(0, 0)` case, which is exact                                 |

### Material 3

The Android side follows [the M3 list spec](https://m3.material.io/components/lists/specs) rather
than translating the iOS look. Concretely:

- Every default colour is an **M3 colour role** — `surface`, `surfaceContainer`, `onSurface`,
  `onSurfaceVariant`, `outlineVariant`, `primary`, `secondaryContainer` — resolved from the app's
  Material theme, so an unthemed list inherits dynamic colour on Android 12+. Anything set in
  `appearance` still wins.
- Selection controls are the real components: `MaterialSwitch`, `MaterialCheckBox`,
  `MaterialRadioButton`. The row owns the tap; the control displays state.
- Items are 56dp minimum with 16dp horizontal padding.
- A **selected** item — a checked checkbox or radio — takes `secondaryContainer` and a larger corner
  radius. The shape changing is the point; colour alone is not how M3 signals selection.

`androidListStyle` picks between the two arrangements the spec defines:

|            | `standard`                   | `segmented`              |
| ---------- | ---------------------------- | ------------------------ |
| Items      | flush                        | own container, 4dp gap   |
| Separators | dividers                     | none — the gaps separate |
| Corners    | square, or grouped-card ends | uniformly rounded        |

Unset follows `listAppearance`: `segmented` for `insetGrouped` and `grouped`, `standard` for
`plain`. It is ignored on iOS, where the shape comes from `listAppearance` alone.

The library brings `com.google.android.material` and themes its own context with
`Theme.Material3.DayNight`. That is not optional — Material widgets read theme attributes at
construction and crash without them — and supplying it here rather than requiring one of the
consuming app is what keeps this a library you install rather than one you configure.

### Platform differences

Decisions, not gaps. Each one is the platform's own idiom rather than the other's.

| Concept                             | iOS                                                                                                                                                                                                                                                     | Android                                                                                                                                                                                                                                                                                                   |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `insetGrouped`                      | `UICollectionLayoutListConfiguration`                                                                                                                                                                                                                   | M3 containers — `segmented` by default, see `androidListStyle`                                                                                                                                                                                                                                            |
| `plain` + pinned headers            | free from compositional layout                                                                                                                                                                                                                          | a hand-written `ItemDecoration`, with push-off                                                                                                                                                                                                                                                            |
| `systemImage`                       | SF Symbols                                                                                                                                                                                                                                              | Material Symbols, via a curated map — **partial by nature**; see `materialSymbol`                                                                                                                                                                                                                         |
| Section index                       | an A–Z rail                                                                                                                                                                                                                                             | a fast-scroller thumb with a letter bubble. Android has never had a rail                                                                                                                                                                                                                                  |
| `plain` pinned headers              | **iOS 26+ only** — see below                                                                                                                                                                                                                            | always pinned, via a hand-written `ItemDecoration`                                                                                                                                                                                                                                                        |
| Swipe actions                       | `UISwipeActionsConfiguration`                                                                                                                                                                                                                           | `ItemTouchHelper` revealing a tray — **off-idiom**; Material says swipe means _dismiss_, and an Android-first design should reach for an overflow menu                                                                                                                                                    |
| `SwipeActions` `edge="leading"`     | a full-height slab, as on the trailing edge                                                                                                                                                                                                             | the same mirrored tray — but the right-swipe competes with the system back gesture, and only the first ~200dp of swipeable rows win it                                                                                                                                                                    |
| `TextField` `unit`                  | a trailing `UILabel` in the field's stack. Both static labels are hidden from VoiceOver and the field speaks for them                                                                                                                                   | a trailing `TextView` — and the row gains the leading label it never drew. The label stays a node, points at the field with `labelFor` and carries the unit in its description; TalkBack drops an editable node's hint once it holds text, so the hint alone would go quiet exactly when there is a value |
| `datePickerStyle: 'compact'` `font` | family and weight are pushed onto the pill's own labels; `size` is ignored, because UIKit does not grow the pill's background to fit one. `UIDatePicker` has no font API, so a future iOS that draws the pill differently falls back to the system font | the date is an ordinary `TextView` and takes the row's font whole, `size` included                                                                                                                                                                                                                        |
| `datePickerStyle: 'wheels'`         | a drum picker                                                                                                                                                                                                                                           | no M3 equivalent exists; falls back to the platform dialog and warns once                                                                                                                                                                                                                                 |
| `datePickerMode: 'dateAndTime'`     | one combined wheel                                                                                                                                                                                                                                      | two dialogs, chained — Material has no combined picker                                                                                                                                                                                                                                                    |
| `Slider`                            | `UISlider` — thin track, capsule knob                                                                                                                                                                                                                   | `com.google.android.material.slider.Slider` — M3 Expressive's thick track, gap and handle bar                                                                                                                                                                                                             |
| `Slider` `step`                     | enforced, but not drawn                                                                                                                                                                                                                                 | enforced **and drawn**, as tick marks. `UISlider` has never had them                                                                                                                                                                                                                                      |
| `Slider` min/max images             | `minimumValueImage` slots                                                                                                                                                                                                                               | icon views laid out either side; Material's slider has no such property                                                                                                                                                                                                                                   |
| `Icon` `background`                 | Settings' 29pt rounded square                                                                                                                                                                                                                           | M3's 40dp circle. The tile is Apple's — Android's leading element is a bare icon or a round avatar                                                                                                                                                                                                        |
| `Icon` `monogram`                   | initials on a circle                                                                                                                                                                                                                                    | the same, and the one case where the container shape does **not** differ: an avatar is round on both                                                                                                                                                                                                      |
| Overscroll                          | rubber-band bounce                                                                                                                                                                                                                                      | stretch or glow                                                                                                                                                                                                                                                                                           |
| `refreshControl`                    | `UIRefreshControl` — one colour, and a caption under it                                                                                                                                                                                                 | `SwipeRefreshLayout` — a circle that cycles `colors`, and no room for a caption                                                                                                                                                                                                                           |
| `refreshControl` unstyled           | the system tint                                                                                                                                                                                                                                         | the list's own `appearance.tintColor`, rather than the stock blue                                                                                                                                                                                                                                         |
| `contentSize.height`                | exact                                                                                                                                                                                                                                                   | an estimate, from `computeVerticalScrollRange()`                                                                                                                                                                                                                                                          |

`keyboardShouldPersistTaps` is `ScrollView`'s prop by the same name — `never` (default), `always`,
or `handled`, where "handled" means the row under the finger has an `onPress`. One deliberate
difference from `ScrollView`: there, `never` also swallows the tap that dismissed the keyboard; here
the row's `onPress` still fires, because the tap target _is_ the row and eating the first tap after
typing reads as a dropped tap. `keyboardDismissMode` defaults to `onDrag` on both platforms, so
scrolling dismisses.

**Sticky headers in a `plain` list need iOS 26.** Below that they scroll with their rows, and the
reason is a trade rather than an omission: `pinToVisibleBounds` on a `.list(using:)` section does
not pin on iOS 18 _and_ it silently disables that section's swipe actions. Both symptoms, one
cause — swipe works on 18 in a grouped list, which never sets the pin, and fails in a plain one,
which did. Setting it was costing a working feature to buy a broken one, so it is now gated on 26.
Android is unaffected: its pinned headers are drawn by an `ItemDecoration` of our own and owe
nothing to UIKit.

`contentOffset.y` is **exact on both**. On Android it is accumulated from `onScrolled`'s `dy` rather
than read from `computeVerticalScrollOffset()`, which is an average-item-height estimate that does
not return exactly zero at the top — and `@gorhom/bottom-sheet` compares it against `0`.

### `materialSymbol`

`systemImage` names an SF Symbol, and the two icon sets overlap in meaning but never in naming, so
Android carries a curated map. An unmapped name renders nothing and warns once. Set `materialSymbol`
on an `<Icon>` to name the Android glyph directly; it wins over `systemImage` there and is ignored on
iOS.

The bundled face is **subset** — the full Material Symbols variable font is 14 MB — so a
`materialSymbol` outside the subset also renders nothing and warns. Add it to
`scripts/symbol-map.mjs` and re-run `npm run gen:material-symbols`.

### Documented no-ops

Accepted so shared screens keep type-checking, and deliberately doing nothing:

| Prop                                        | Why                                                                                                                                                                   |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sectionIndexRowHeight`                     | sets the per-letter height of an A–Z rail. There is no rail on Android                                                                                                |
| `automaticallyAdjustsScrollIndicatorInsets` | the indicator is drawn inside the list's padding already                                                                                                              |
| `keyboardDismissMode: 'interactive'`        | maps to `onDrag`. Android has no interactive dismissal                                                                                                                |
| `datePickerStyle: 'inline' \| 'wheels'`     | both fall back to the platform dialog, and warn once                                                                                                                  |
| `RefreshControl` `title` / `titleColor`     | Material's indicator is a bare circle with no room for a caption                                                                                                      |
| `RefreshControl` `tintColor`                | the Android equivalent is `colors`, a list. Left unset, the indicator resolves through `appearance.tintColor` instead, which is the value a themed screen already set |

### Insets and navigation headers

Content insets are computed from **how much system chrome actually overlaps the list**, not from the
window's own insets. Under a transparent header the list starts at the top of the window and takes
the full status-bar inset; under an ordinary opaque toolbar it starts below one already, and taking
it again would leave a bar-shaped gap.

What Android has no equivalent for is the _header's own_ height. On iOS
`contentInsetAdjustmentBehavior: automatic` folds in the navigation bar because UIKit knows how tall
it is; a toolbar's height is not a window inset, so `headerTransparent` on Android is a promise the
list cannot keep on its own. Use an opaque header, or pass the height yourself through
`contentInset={{ top }}`.

### Host rows

`<CollectionView.Host>` children are mounted into an invisible parking bay and reparented into the
cell that owns them. A holder releases its child only if it still owns it — during a reload the
incoming holder binds _before_ the outgoing one is recycled, so an unguarded release blanks a row
that is on screen and correct.

The bay is a view of ours that is invisible, never React's view made invisible, and the distinction
is load-bearing on iOS. React pools component views app-wide and never restores `hidden` on the way
out — `prepareForRecycle` does not touch it, and the one assignment in `updateLayoutMetrics:` is
gated on old metrics that a recycled view never has. A child handed back hidden is therefore hidden
for the rest of the process, in whatever unrelated screen draws it next. Moving a child between the
bay and a cell is the only thing that changes its visibility.

## License

AGPL-3.0-only.

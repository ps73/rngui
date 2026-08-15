# Changelog

## Unreleased

**`TextField` takes a `unit`.** The `cm` in `Height 187 cm`, drawn at the trailing edge of the row:
a leading label, the editable value right-aligned against it, and the suffix beside that. Kept out
of `value` deliberately — a unit folded into the text comes straight back through `onChangeText` as
something the caller has to parse off again, so native draws it and never sends it anywhere.

Two details are load-bearing rather than cosmetic. A unit right-aligns the field **even on a row
with no label**, because a suffix separated from its value by the width of the row has stopped
being a suffix; a field with neither still fills the row, which is the Reminders title look,
unchanged. And the unit is part of the value's hit target on both platforms — `187` and `cm` are
one value to a reader, so a tap on either focuses the field.

Each platform says that to its screen reader the way that platform says it. On iOS both static
labels are hidden and the field speaks for them — "Height, cm" — because a `UILabel` beside a
`UITextField` in one cell is not a relationship VoiceOver knows about. Android has one: the label
stays an ordinary node and points at the field with `labelFor`, which is how its own Settings rows
read, and the unit — which is not a label — becomes the field's hint text. What neither does is put
any of it in a content description: that **replaces** the spoken text on an editable node, so a
field holding `187` would announce as "Height, cm" and the value the row exists for would go
unsaid.

`unit` is an unrestricted string, so neither platform lets it starve the field it belongs to, and
both reserve the same 44pt/dp of it — the tap target the rest of iOS is built on. iOS orders who
yields by priority: field first, then label, then unit, none of them `.required`, so a long one
truncates rather than making a stack pinned to both margins unsatisfiable.

Android cannot express that order, and per-view dp caps alone are a ceiling rather than a floor — a
row is free to combine a leading icon with both of them on a 320dp screen or in a multi-window split
until nothing is left, and a weighted child cannot defend itself with `minWidth` because
`LinearLayout` measures it with an exact spec computed from the leftovers. So the row does the
arithmetic in `onMeasure`, where its real width is known and the icon has been measured: what
remains after the field's 44dp is what the label and the unit divide. Measured on a 274dp window
with an icon, a 33-character label and a 27-character unit, the field comes out at exactly the 115px
that 44dp rounds to.

Not offered on `TextArea`, and refused in three places rather than documented in one: the type does
not carry it, the serializer gates on the tag so props spread from a shared object cannot smuggle
one in, and neither platform reads it for that kind. A field that grows with its content has no
line for a suffix to sit on.

**Android text rows draw the label they were always given.** `<Row><Label>Height</Label><TextField/></Row>`
rendered the field and nothing else: the row built its view tree per kind, and the `textField`
branch added only the `EditText`, so the label was silently dropped rather than documented as a
difference. iOS's `TextFieldCell` has drawn one since it existed. The label now sits leading, added
directly rather than through the two-line text column — a text row has no second line, and that
column's weight would have split the row down the middle with the field instead of letting the
label take what it needs. Rows that never had a label are unaffected: a `GONE` child in a
horizontal `LinearLayout` costs no width and no margin.

**An Android text field no longer twitches once per keystroke.** Every keystroke round-trips
through JavaScript and comes back as a commit a few hundred milliseconds later, and each commit
re-applied the field's whole keyboard configuration. That is not merely wasteful: `setSingleLine`
installs a fresh `SingleLineTransformationMethod`, which re-sets the text and drops the field's
horizontal scroll offset, and `setHint` calls `checkForRelayout` without comparing. So the value
jumped and re-settled on every commit — measurable as a third, intermediate text position between
the one before a keystroke and the one after.

The four properties that describe _how a field is typed into_ — input type, IME options, single-line,
max lines — are now one value, applied only when it actually differs, and the label and unit text
are compared before assignment for the same reason. Visible on any text row; obvious on a `unit`
row, where the value sits against a fixed suffix that makes the wobble easy to see.

**A compact date picker follows the row's `font` on iOS.** It always did on Android, where the date
is an ordinary `TextView`, so a row with a custom family rendered differently per platform for no
reason a caller could see.

`UIDatePicker` has no font API — no property, no content configuration, no appearance attribute
that reaches the pill's text — so the row's resolved font is pushed onto the labels UIKit builds
the pill from, and re-applied from the picker's own layout pass, which is the only hook that
survives a date change and a popover dismissal alike. This is the library's first assumption about
a system control's internals and it is worth saying what kind: `UIView.subviews` and `UILabel.font`
are both public, nothing reads a private selector or an ivar by key, and a future iOS that draws
the pill some other way costs the override and nothing else — the row keeps the system font.

That failure mode is why the two alternatives lost. An appearance proxy is process-global, so it
could not express the per-row fonts `RowSpec.font` exists for and would reach into every other
`UIDatePicker` in the host app. Drawing our own pill over an invisible real picker would mean
reimplementing UIKit's date formatting to ship a control no iOS user has seen — the same trade this
library already refuses for slider tick marks.

Scoped to `compact`. `inline` and `wheels` draw dozens of labels UIKit recycles as they scroll, and
the calendar popover keeps stock typography because it is presented outside the picker's subtree —
which is correct, it is a system surface.

**Family and weight travel; `size` does not, and that is enforced rather than documented.** UIKit
lays the pill's rounded background out to metrics it chose and does not grow it to fit metrics it
did not, so a `size` here buys clipped or re-abbreviated text — `15. Aug 2026` becomes `15.08…` —
in exchange for a number whose effect a caller cannot see until they try it. Each of the pill's
labels therefore keeps its own point size and takes only the face. Dynamic Type, which the picker
already follows on its own, is how the pill gets bigger.

## 0.3.0

**A `Host` row could blank an unrelated screen, and the mechanism was one line of ours.** iOS parked
each mounted child by setting `hidden` on it, and `HostCell.detach` set it again on the way back to
the bay — but nothing ever cleared it. React never clears it either: `prepareForRecycle` resets
props, event emitter and layout metrics and leaves `hidden` alone, and the one place that assigns it
(`UIView+ComponentViewProtocol`'s `updateLayoutMetrics:`) is gated on the old metrics comparing equal
to `EmptyLayoutMetrics`, which a recycled view never has because `RCTViewComponentView` substitutes
its own stored metrics. So every hosted child this library ever unmounted went back into React's
**app-wide** recycle pool permanently invisible, and the next screen to mount a plain `View` drew one
of ours and rendered nothing. No exception, no redbox: the first mount always worked, later ones did
not, and the blame landed on whatever unrelated screen happened to draw the poisoned view.

The fix is structural rather than a repair. iOS now has the parking bay Android always had — a view
of ours that is invisible, zero-sized and clipping — and nothing in the library writes `isHidden`,
`visibility` or any other property on a view React owns. Moving a child between the bay and a cell is
the only thing that changes whether it draws. A `#if DEBUG` check in `mountChildComponentView:` logs
any child that arrives hidden, which turns this whole class of bug into one line naming the screen
that caused it. Android's two redundant `visibility` writes went with it.

Worth stating because it is the natural fix and it would not have worked: implementing
`prepareForRecycle` here is dead code. `+shouldBeRecycled` returns `NO`, and `RCTComponentViewRegistry`
branches on exactly that — a view that declines recycling is sent `-invalidate` and returned, never
reaching `prepareForRecycle`.

**Teardown now happens when React says so, not whenever ARC gets there.** The component view
implements `-invalidate` — the hook React actually runs for a class that declines recycling — which
returns every hosted child, invalidates the content-size KVO, unregisters and releases the keyboard
observer, and nils the event blocks. The
hosted-child sweep is the same invariant the parking bay exists to hold, one level up: nothing should
still be parented into a collection view React has finished with. A `#if DEBUG` check reports any
child still held at that point, which would mean it reached the recycle pool without passing through
`unmountChildComponentView:`.

No leak is being claimed here, and an earlier draft of this entry claimed one. It said
`UITapGestureRecognizer` retains its target and so closed a cycle through the collection view. It does
not — a recogniser's target-action storage is not a strong edge, the same graph deallocates unaided,
and a `deinit` probe on the host fires on the first pop. Thanks to review for catching it.

**A hosted row can take the section's card.** `Host` gains `background`, defaulting to `"none"`.
`"card"` gives the row the same background, corner treatment and separators as a described row in the
same section, on both platforms — `HostCell` is a `UICollectionViewListCell` now, because a
`UIBackgroundConfiguration` carries a corner radius but no masked corners, so first-and-last-in-section
rounding is not expressible without one. Opt-in rather than default: a hosted subtree usually draws
its own surface, and a card behind one that does is two cards with mismatched corners. A row that
declines the card declines the separators with it.

**Leading swipe actions, documented.** No behaviour change: `SwipeActions` has taken
`edge="leading"` since 0.1.0 and both backends have always honoured it. But the README said so in
one table cell, no example used it, and it was reported as missing — which is a documentation bug
with the same cost as a real one. The README now carries a worked two-edge example, the rule that
action ids are unique per row rather than per edge, and the Android caveat that a right-swipe
competes with the system back gesture and that `systemGestureExclusionRects` is rationed to 200dp
per edge. The example app's Reminders and Contacts screens now swipe both ways.

**Pull to refresh.** `Root` takes `refreshControl`, spelled the way `ScrollView` spells it — a
`RefreshControl` element — plus `refreshing` / `onRefresh` directly, which is `FlatList`'s
shorthand. It drives a real `UIRefreshControl` on iOS and a real `SwipeRefreshLayout` on Android.

The element is **read rather than mounted**: its props are unpacked onto the native view. The React
children of this component are exactly the `Host` subtrees and are addressed positionally, so one
extra mounted child would shift every hosted row — and on Android React Native's own control is a
wrapper around the scrollable, which could never have been a child of it.

`refreshing` is controlled, as React Native documents it. Holding that up needs more than a prop:
a pull starts the spinner natively while JavaScript still says `false`, so `Root` detects the
disagreement after its own render and corrects it with a native command.

Two small divergences, both additive: `enabled` works on iOS as well as Android, and an Android
indicator with no `colors` resolves through the list's own `appearance.tintColor` rather than
staying the stock blue. `refreshControl` is deliberately **not** forwarded by
`BottomSheetCollectionView` — inside a sheet the pull and the sheet's collapse are the same
gesture. All of it is in the README under `Root` and _Platform differences_.

## 0.2.0

Reconstructed from the commits after the fact — 0.2.0 was published without notes, and the gap was
noticed while cutting 0.3.0. Recorded rather than left blank because the release carries a breaking
change.

**Breaking: `FontDesign` is gone, and `font.family` says what it used to.** `{ design: 'rounded' }`
was a second vocabulary for something React Native already names: `RCTFontUtils` maps `system-ui`,
`ui-sans-serif`, `ui-serif`, `ui-rounded` and `ui-monospace` onto `UIFontDescriptor.SystemDesign`
for `<Text>`, so a screen mixing rows and labels had two names for SF Rounded depending on which
component was asking. `family` now takes those five names or an app face, and the old rule that
naming a family made `design` moot is unexpressible rather than documented. Anything passing
`design` must move to `family`.

Android carries its own table for it, deliberately. `ReactFontManager` has no entry for the generic
names — handed `ui-monospace` it finds no asset and falls through to `Typeface.create`, which
Android answers with the default sans — so deferring to React Native on both platforms would have
meant `ui-monospace` being monospaced on one and not the other. Note it is spelled `ui-monospace`,
as React Native and CSS spell it, and UIKit's `ui-monospaced` is not aliased to it.

**Section headers and footers ignored every font field on Android.** `RowStyle.of` had resolved
`headerFont` and `footerFont` since the M6 pass, and `LabelHolder.bind` set the text and the colour
and stopped — so both fields, and the `font` they fall back to, reached the holder and were dropped.
Rows were always right; only the supplementary path was wrong, which is why a screenshot review
never caught it: a header is small, grey and five words long. Found by hashing the pixels of one
header across two families and getting the same hash twice.

## 0.1.0

The first published release. Both backends are real platform views: a `UICollectionView` on iOS
and a `RecyclerView` on Android, described from React through a Radix-style compound API.

**The list.** `insetGrouped`, `grouped` and `plain` styles, section headers and footers with a
trailing header action, pinned headers, an A–Z section index on iOS and a fast-scroller thumb on
Android, and diffable updates that survive recycling.

**Rows.** `Label` / `Description` / `Value` slots; `Icon` (SF Symbols, Material Symbols, monograms,
platform icon containers), `Badge`, `Chevron`, `Checkmark`, `Spinner`, `Checkbox`, `Radio`;
`Switch`, `TextField`, `TextArea`, `Menu`, `DatePicker`, `Slider`, `Button` and `Card` controls;
leading and trailing swipe actions.

**React children in a recycled cell.** `<CollectionView.Host>` mounts a real React subtree inside
`cell.contentView` (iOS) or the holder's container (Android) — clipped, hit-tested and scrolled by
the platform rather than floated over it.

**Scrolling contract.** Content insets computed from actual chrome overlap, keyboard avoidance
including focus following, imperative scrolling, scroll events, and a `@gorhom/bottom-sheet`
integration exported from `@rngui/collection-view/bottom-sheet`.

**Theming.** Light and dark appearance resolution, tint and background overrides, per-row fonts,
and Material 3 Expressive styling on Android rather than iOS drawn in Android colours.
`FontSpec.family` takes the same names React Native's `<Text>` takes — the five CSS generic
families (`system-ui`, `ui-sans-serif`, `ui-serif`, `ui-rounded`, `ui-monospace`) or a face the app
registered — so a row and a label beside it resolve identically.

### Known limits

- Android has been verified on a Pixel 10 emulator (API 37). The API 24 floor, API 31 and low-RAM
  devices are not yet covered.
- A short list of props is accepted and deliberately does nothing on Android, and two more fall
  back with a warning. Both lists are in the README under _Documented no-ops_.
- `headerTransparent` cannot compute a toolbar's height on Android; pass `contentInset={{ top }}`.
- Material Symbols ship as a subset face. An unmapped name renders nothing and warns.

### Packaging

- Requires the New Architecture.
- Peer dependencies: `react`, `react-native`, `react-native-gesture-handler`,
  `react-native-reanimated`, and `@gorhom/bottom-sheet` >= 5 for the sheet entry point.
- AGPL-3.0-only. The license text now ships inside the package.

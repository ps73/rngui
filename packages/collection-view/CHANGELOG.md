# Changelog

## Unreleased

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
returns every hosted child, drops the KVO and keyboard observers, and nils the event blocks. The
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

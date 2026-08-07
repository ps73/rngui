# Changelog

## Unreleased

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

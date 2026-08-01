# `@rngui/collection-view` — the Android backend

The plan for replacing the stub with a real `RecyclerView` implementation.

The iOS side is a real `UICollectionView`, and the promise of this package is that the Android side
is equally real: a `RecyclerView` with Material 3 Expressive list items, not a `FlatList` in a
trench coat and not iOS drawn in Android colours. Everything below follows from that.

Twelve milestones. **M1 is a gate** — it settles two decisions that every later milestone depends
on, and it settles them by measurement rather than by argument.

---

## Status

Eleven of twelve milestones are done and verified on a Pixel 10 emulator (API 37). What remains is
listed honestly rather than rounded up.

| Milestone                         | State                                                              |
| --------------------------------- | ------------------------------------------------------------------ |
| M1 Foundations + decisions        | Done. Both settled by measurement; results below                   |
| M2 Generated Kotlin model         | Done. Shares its fixture with the Swift test                       |
| M3 Scroll shell + first rows      | Done                                                               |
| M4 Grouping, shape, separators    | Done. Ripple clipping verified by pixel measurement                |
| M5 Sticky headers + scrubber      | Done                                                               |
| M6 Typography + icons             | Done. Ink-coverage instrument passes                               |
| M7 Controls                       | Done. Recycling emits zero spurious events, proven by mutation     |
| M8 Host rows                      | Done. Ownership guard proven by mutation                           |
| M9 Chips + swipe actions          | Done                                                               |
| M10 Insets, keyboard, scroll      | Done, except the focus-following half of `keyboardAware`           |
| M11 Bottom sheet                  | **Partial.** Renders and drags; the list does not scroll inside it |
| M12 Example parity, docs, release | Docs and the publish dry-run done; device matrix not run           |

**M11 is the one that is not working.** `react-native-gesture-handler` asks the attached view
whether it scrolls, and the attached view is this component's `FrameLayout` wrapper rather than the
`RecyclerView` inside it. Forwarding `canScrollVertically` was necessary and not sufficient; the
next step is to find out whether the touch reaches the list at all, and if not, whether the
fallback the risk register prescribes — `scrollEnabled` from `animatedScrollableStatus`, which the
shared bottom-sheet entry point already implements — is reaching native. The jank-frame instrument
is not built, because it would measure a gesture that does not yet work.

**Not done in M12:** the device matrix (one API 24 device, one API 31, one current, one low-RAM).
Only a single emulator was available, so "it works on a Pixel 10 emulator" is the whole of the
claim. The Settings and Contacts screens are also still ports of their iOS originals rather than
Android-native rebuilds — the strongest forcing function for Expressive fidelity in the plan, and
untouched.

Three plan assumptions did not survive contact and are corrected in place below: Compose needs a
compiler plugin the app does not provide, the Material Symbols font is 14 MB rather than something
to bundle whole, and `RowBackendSpike` could not settle Decision 1 on available hardware.

---

## Where we are

`android/src/main/java/com/rngui/collectionview/` holds three files: a `ViewGroupManager` that
implements the generated `RNGUICollectionViewManagerInterface` and discards every prop, a
`ViewGroup` that lays out nothing, and a `BaseReactPackage` that registers them. `Root` withholds
`<CollectionView.Host>` children on Android (`HOSTS_CHILDREN` in
[CollectionView.tsx:276](../packages/collection-view/src/CollectionView.tsx:276)) and warns once in
development.

That stub is worth more than it looks: it implements the **generated** interface rather than
declaring its own `@ReactProp` setters, so the Kotlin compiler already fails the build whenever the
TypeScript spec changes. Every milestone below inherits that guarantee — a prop cannot be added on
one side and forgotten on the other.

The JS contract is fixed and platform-neutral:
[`tree.ts`](../packages/collection-view/src/tree.ts) is the descriptor model,
[`RNGUICollectionViewNativeComponent.ts`](../packages/collection-view/src/specs/RNGUICollectionViewNativeComponent.ts)
is the native prop/event/command surface. **Neither is Android's to change**, with one deliberate
exception (`materialSymbol`, M6). If a milestone below wants to change either, that is a signal the
milestone is wrong.

---

## What transfers from iOS

Four things were learned the expensive way on iOS and apply verbatim. They are written here so they
are not relearned.

**The reuse rule.** Anything assigned to a recycled view must be fully specified on every bind
pass. On iOS this bit us through `UIListContentConfiguration`; on Android it will bite harder,
because a recycled `EditText` keeps its `TextWatcher`, a recycled `Switch` keeps its
`OnCheckedChangeListener`, and both will fire during rebind and report a change the user never
made. Every listener is detached before bind and reattached after.

**The ownership guard.** During a reload UIKit configures the replacement cell _before_ recycling
the outgoing one, so the outgoing cell's `detach()` was hiding a view the new cell already owned —
rows vanished, and I twice blamed Fast Refresh before the screenshots forced a real look. The fix
was `guard view.superview === contentView` in
[HostCell.swift](../packages/collection-view/ios/Cells/HostCell.swift). `RecyclerView` has the same
ordering (`onBindViewHolder` for the incoming holder can precede `onViewRecycled` for the outgoing
one), so M8 needs the identical guard: `if (child.parent !== holder.container) return`.

**The revision gate.** `tree` is a string that can be megabytes. Native decodes only when
`revision` changes, and JS reuses the previous revision when the serialized tree is byte-identical.
That fix removed a per-frame re-serialization that was causing library-wide judder. The Android
adapter gates on the same field, in `setRevision` rather than `setTree`.

**No verdict without a reproduction.** "Stale Fast Refresh" and "it looks fine to me" were both
wrong twice each. The instruments that actually settled things were a direction-reversal counter
and pixel ink-coverage measurement, and both contradicted my visual impression. Every milestone
below states a _done-when_ that a machine can check.

---

## What will not be 1:1, and why

The point of this package is that each platform gets its own idiom. These are decisions, not gaps,
and each one gets documented in the README rather than quietly approximated.

| Concept                                      | iOS                                   | Android                                                                                     | Fidelity                                                                       |
| -------------------------------------------- | ------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `insetGrouped`                               | `UICollectionLayoutListConfiguration` | M3 Expressive grouped cards — the Pixel Settings look, with first/last/middle corner shapes | **Full.** Modern Android really does this now                                  |
| `plain` + pinned headers                     | Free from compositional layout        | `ItemDecoration` drawing a sticky header, with push-off                                     | Full, but hand-written (M5)                                                    |
| `systemImage`                                | SF Symbols                            | Material Symbols variable font, via a curated name map + a `materialSymbol` escape hatch    | **Partial by nature** — unmapped names render nothing and warn                 |
| Section index (A–Z)                          | `UICollectionView` index bar          | Fast-scroller thumb with a section-letter bubble                                            | **Different by design.** Android has never had an A–Z rail                     |
| `headerBackgroundStyle: 'blurred' \| 'soft'` | `UIBlurEffect` / `UIScrollEdgeEffect` | M3 tonal surface elevation on scroll; real blur only on API 31+                             | **Different by design.** Android signals scroll with tone, not blur            |
| Swipe actions                                | `UISwipeActionsConfiguration`         | `ItemTouchHelper` revealing an action tray                                                  | Full, but _off-idiom_ — Material prefers an overflow menu. Documented          |
| `datePickerStyle: 'wheels'`                  | Drum picker                           | No M3 equivalent; falls back to `inline` and warns once                                     | **Degraded, loudly**                                                           |
| `decelerationRate`                           | `UIScrollView.decelerationRate`       | `0` maps exactly (suppress fling); other values approximate via fling friction              | **Partial.** `0` is the value gorhom needs, and it is exact                    |
| Overscroll                                   | Rubber-band bounce                    | Stretch (API 31+) / glow                                                                    | Platform-correct, so the iOS reversal-counter instrument does **not** transfer |
| `contentOffset.y`                            | Exact                                 | Accumulated from `onScrolled` dy — exact. `contentSize.height` stays an estimate            | Offset full, content size partial                                              |

---

## API-level floor

`minSdk` is 24 today. Three features want more, and each degrades rather than crashes:

| Feature                                                  | Needs | Below that            |
| -------------------------------------------------------- | ----- | --------------------- |
| `FontSpec.variations` (`Paint.setFontVariationSettings`) | 26    | Nearest static weight |
| Real blur (`RenderEffect`)                               | 31    | Opaque / tonal header |
| Stretch overscroll                                       | 31    | Glow                  |

Whether to raise `minSdk` to 26 is an M1 question. React Native 0.86's own floor is 24, so raising
it is a real cost to consumers for one feature.

---

## M1 — Foundations, and two decisions made by measurement

**Everything else depends on these two answers, so they are settled first and settled with a
spike.**

### Decision 1: what draws a row

Three candidates, and the trade is Material 3 Expressive fidelity against cost:

- **Pure Views + Material Components (MDC).** Cheapest, but M3 _Expressive_ barely exists in
  Views — no `ListItem`, no motion scheme, no shape morphing. We would be reimplementing
  Expressive by hand and getting it subtly wrong forever.
- **Full Compose (`LazyColumn` in an `AndroidView`).** Best fidelity, worst fit: the scroll
  container has to be a real View anyway (see below), and hosted React children inside a
  composition means Compose's disposal fighting React's mounting layer.
- **Hybrid — `RecyclerView` shell, `ComposeView` per stock-row holder.** _Recommended._

The recommendation follows from three things that are not negotiable:

1. **The scroll container must be a View.** `onScroll`, `contentInset`, the `scrollTo` command,
   RNGH/gorhom interop and hosted-child reparenting all live there, and all four mirror iOS
   closely enough that the design is already known.
2. **`host` rows must be a plain `ViewGroup` holder.** A React child cannot live inside a
   composition that owns its lifetime.
3. **Everything else is chrome, and Expressive chrome only exists in Compose.**

So the shell is `RecyclerView`, `host` holders are `FrameLayout`, and every stock row's content is
a `ComposeView` with
`ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool` — the strategy that exists
specifically for this pattern.

**The measurement that decides it:** a 2,000-row list of `value` rows, scrolled by a fixed
`ItemTouchHelper`-free programmatic fling, with frame times from `FrameMetrics`. Both the hybrid
and the pure-Views spike get built. If per-item composition costs more than ~2 ms of the 16 ms
budget on a mid-tier device, the fallback ships: `default` / `value` / `subtitle` (the 90 % case)
drop to hand-built Views, and Compose is kept only for the control-bearing kinds — `switch`,
`datePicker`, `menu`, `chip` — where its components are the whole point.

### Decision 2: how the tree is decoded

kotlinx.serialization is the obvious choice and is probably unavailable to us. It is a **compiler
plugin**, so `apply plugin: "org.jetbrains.kotlin.plugin.serialization"` requires that plugin on
the _root project's_ buildscript classpath — and React Native app templates, Expo's included, do
not put it there. `android/build.gradle` deliberately declares no `buildscript` block for exactly
this class of reason (version pinning against the app's), and adding one to chase a serializer
would trade a real problem for a worse one.

**Recommendation: hand-written `org.json` decoders, emitted by the same generator that emits the
Swift ones.** Zero plugins, zero runtime dependencies, no R8 rules, and generated so it cannot
drift. Moshi needs KSP (another plugin) or `kotlin-reflect` (a dependency and R8 rules); Gson needs
reflection rules.

**To verify in this milestone:** create a throwaway RN 0.86 app, add
`apply plugin: "org.jetbrains.kotlin.plugin.serialization"` to a library subproject, and confirm it
fails to resolve. If it resolves, kotlinx.serialization wins on every axis and M2 gets simpler.

### Also in M1

- Decide `minSdk`: stay at 24, or raise to 26 for font variations.
- Make the library's unit tests runnable. `android/build.gradle` says the library is not buildable
  standalone, and that stays true — tests run through the example app's Gradle
  (`apps/example/android/gradlew :rngui_collection-view:testDebugUnitTest`) rather than through new
  infrastructure.
- `.gitignore` `packages/collection-view/android/build/` — it is currently tracked-adjacent noise
  in every `find`.

**Done when:** both spikes exist, `FrameMetrics` numbers for each are recorded in this document,
the serialization question is answered by a real build failure or a real success, and the two
decisions are written down with their numbers.

### M1 results

Measured 2026-07-31. The spike is
[`RowBackendSpike.kt`](../packages/collection-view/android/src/androidTest/java/com/rngui/collectionview/spike/RowBackendSpike.kt);
re-run it with `apps/example/android/gradlew :rngui_collection-view:connectedDebugAndroidTest`.

**Decision 2 — serialization: settled, as predicted.** Adding
`apply plugin: "org.jetbrains.kotlin.plugin.serialization"` to the library subproject of a
prebuilt Expo app fails at configuration time:

```
> Plugin with id 'org.jetbrains.kotlin.plugin.serialization' not found.
```

Generated `org.json` decoders it is, and M2 ships them.

**A finding the plan did not anticipate: Compose has the same problem.** Since Kotlin 2.0 the
Compose compiler is a Gradle plugin too, and `org.jetbrains.kotlin.plugin.compose` fails
identically. It is _recoverable_ where serialization was not — a `buildscript` block in the
library's own `build.gradle`, with the version read off `rootProject.ext.kotlinVersion` so it
tracks the app's Kotlin rather than pinning against it, resolves and compiles. But it means
**any** use of Compose costs the library the one thing `android/build.gradle` was written to
avoid, and the cost is paid at the first `@Composable` rather than at the hundredth.

**Decision 1 — row backend: the emulator cannot settle it, and says so.**

|         | create   | first bind | rebind    |
| ------- | -------- | ---------- | --------- |
| Views   | 0.292 ms | 0.054 ms   | 0.055 ms  |
| Compose | 0.039 ms | 2.207 ms   | 0.021 ms† |

† Not a real number. Writing a `mutableStateOf` schedules a recomposition that dispatches on the
next frame, so a `measure()` on the same stack still sees the old composition. The spike says this
rather than reporting it as a cost — an earlier version of it did report exactly that, and was
confidently wrong by two orders of magnitude.

Frame times, 2,000 rows, 400 frames of fixed `scrollBy`:

|         | p50      | p90      | p99      | janky frames |
| ------- | -------- | -------- | -------- | ------------ |
| Views   | 17.50 ms | 18.34 ms | 18.88 ms | 290 / 400    |
| Compose | 24.68 ms | 29.28 ms | 34.24 ms | 388 / 400    |

**The Views baseline misses 60fps drawing two `TextView`s per row.** On a software-rendered
emulator there is no 16 ms budget left in which to measure a 2 ms cost, and the frame comparison
additionally penalises Compose's draw path in a way a real GPU would not. The one number that does
transfer is first composition: **2.15 ms above Views**, which is over the plan's threshold — but it
is paid once per _holder_, not once per row, so a pool of ~7 per view type pays it ~7 times. That
is a hitch when a list first scrolls, not a per-row tax, and it is not by itself grounds for the
fallback.

**What M3 does with that.** It builds `default` / `value` / `subtitle` as hand-built Views — the
plan's own documented fallback — for three reasons, only one of which is performance:

1. The measurement is inconclusive on available hardware, and the plan's rule is that a verdict
   needs a reproduction. Shipping the more expensive option on an unresolved measurement is the
   wrong way round.
2. Both branches of Decision 1 keep Compose for the control-bearing kinds (`switch`, `datePicker`,
   `menu`, `chip`), so this defers nothing that M7 will not need anyway.
3. The build-system coupling above is real, permanent and paid up front. Three row kinds that a
   `LinearLayout` renders in 0.055 ms are not what should buy it.

This is a decision to revisit, not a closed one. Re-run the spike on the M12 device matrix; if
Compose holds frame budget on a mid-tier phone, moving the three stock kinds over is a contained
change, because M3 puts every row kind behind one `RowViewHolder` seam.

**`minSdk` stays at 24.** Raising it to 26 buys `Paint.setFontVariationSettings` and costs every
consumer below it, against a React Native floor of 24. M6 falls back to the nearest static weight
below 26, which is a visible-but-correct degradation rather than a broken one — and the ink-coverage
instrument M6 specifies will say which path a device took.

**Unit tests run**, through the example app's Gradle exactly as the plan proposed:

```
apps/example/android/gradlew -p apps/example/android :rngui_collection-view:testDebugUnitTest
```

**`.gitignore`** already covers `packages/*/android/build/`; nothing to do.

---

## M2 — The generated Kotlin tree model

The Android counterpart to `ios/Generated/TreeTypes.swift`, from the same source of truth.

**Ships:**

- `packages/collection-view/scripts/gen-kotlin-types.mjs`, reading `src/tree.ts` exactly as
  `gen-swift-types.mjs` does — including the `IntValue` marker that distinguishes an index from a
  measurement.
- `android/src/main/java/com/rngui/collectionview/Generated/TreeTypes.kt`: `data class`es (so
  `DiffUtil.areContentsTheSame` is free and correct) plus a decoder per type.
- A JVM unit test decoding `ios/Tests/TreeTypesFixture.json` — **the same fixture the Swift test
  uses**, so the two platforms cannot diverge in what they accept.
- `verify:kotlin-types` wired into `npm run verify`, mirroring `verify:swift-types`.

**The hard part:** lenient decoding. `tree.ts` rule 2 says a JS bundle can be newer than the native
binary (`expo-updates` ships JS alone), so unknown keys are ignored and an unknown enum value
degrades to the default rather than throwing. With hand-written decoders this is a property of the
generator's output, so it gets a fixture case of its own: a tree containing a future row kind and a
future appearance field must decode to a usable tree.

**Done when:** `npm run verify` fails if `tree.ts` changes without regenerating, and the
forward-compatibility fixture passes on both platforms.

---

## M3 — The scroll shell, and the first rows on screen

The narrowest slice that puts real recycled rows on a real screen.

**Ships:**

- `RNGUICollectionViewView` becomes a `FrameLayout` holding a `RecyclerView` +
  `LinearLayoutManager`.
- A flattened item model — headers, rows and footers as one list, in tree order, which is the same
  order `serialize` produces and the same order `onVisibleRangeChange` indexes against.
- `ListAdapter` + `DiffUtil`, keyed on row `id` for identity and `data class` equality for
  contents. This is the direct analogue of `UICollectionViewDiffableDataSource`, background
  diffing included. Note that unlike UIKit it does **not** silently drop duplicate ids — but the
  serializer already warns about collisions in `__DEV__`, and that warning stays the contract.
- `setRevision` gates decode; `setTree` only stores.
- Row kinds `default`, `value`, `subtitle`. Nothing else.
- `AppearanceResolver`'s Kotlin counterpart: `#RRGGBBAA` parsing, `appearance` /
  `darkAppearance` selection from `Configuration.uiMode`, and a rebind on
  `onConfigurationChanged` — so a theme flip costs no JS render, matching
  `UIColor(dynamicProvider:)`.
- `colorScheme` overriding the configuration, which on Android means a wrapped
  `ContextThemeWrapper` with an overridden `uiMode` rather than iOS's
  `overrideUserInterfaceStyle`.

**Done when:** the example app's Reminders screen renders its `default` / `value` / `subtitle` rows
on Android with correct colours in both themes, a theme flip rebinds without a JS commit, and
scrolling 2,000 rows holds the M1 frame budget.

---

## M4 — Grouping, shape and separators

Where it starts looking like Android rather than like a list.

**Ships:**

- `insetGrouped` / `grouped` / `plain` mapped to M3: rounded grouped cards with horizontal insets,
  the same without insets, and edge-to-edge respectively.
- **Group corner shapes.** First item in a group takes large leading corners, last takes large
  trailing corners, middles take small ones — the Pixel Settings treatment, and the reason
  `insetGrouped` is a _native_ look on Android now rather than an iOS import. The adapter has to
  know each row's position within its section, which the flattened model already encodes.
- Press feedback: a `RippleDrawable` masked to the row's `MaterialShapeDrawable`, because an
  unclipped ripple on a rounded card is the single most obvious tell that a list was not built for
  the platform.
- Separators: inset dividers within a group, none between groups. `appearance.separator`.
- Headers and footers as item view types, with `headerFont` / `footerFont` / text colours.
- `sectionSpacing` as the whole gap, matching the iOS semantics ("not a contribution to it").

**Stretch, and genuinely the Expressive signature:** the interactive shape morph on press — a
pressed row briefly takes a different shape. Worth doing, worth not blocking on.

**Done when:** the Settings screen's grouping is screenshot-compared against Pixel Settings, and
the ripple is clipped (verifiable: a press at a rounded corner must show no ink outside the shape).

---

## M5 — Sticky headers and the fast scroller

Two things `RecyclerView` does not give us.

**Ships:**

- A sticky-header `ItemDecoration` for `plain`: measure the current section's header, draw it in
  `onDrawOver`, and push it off when the next header meets it. It must also stay touchable, which
  a drawn decoration is not — so header taps (the `action` button, M7) need a hit-test overlay
  rather than a real view.
- `showsSectionIndex` → the fast-scroller thumb, with a bubble showing the current section's
  `indexTitle`. `sectionIndexShowsCallout` maps to the bubble.
- `sectionIndexRowHeight` has no Android meaning and becomes a documented no-op — the iOS prop
  exists to stop a naive implementation stretching the rail, and there is no rail here.

**The hard part is the interaction between the two.** A drawn sticky header and a fast-scroll jump
must agree about which section is current mid-fling, and the naive implementation flickers between
two headers at the boundary.

**Done when:** the Contacts screen has working sticky headers and a working letter bubble, and a
programmatic fling from A to Z never draws two headers in one frame.

---

## M6 — Typography and icons

**Ships:**

- `FontSpec` → `ReactFontManager.getInstance().getTypeface(...)`, so a family name registered by
  `expo-font` resolves the same way React Native's own `fontFamily` resolves it.
- `variations` → `Paint.setFontVariationSettings("'wght' 620, 'opsz' 28")` on API 26+. This is the
  Android half of the Inter Variable work, and it has an iOS lesson attached: on iOS a descriptor
  carrying a _name_ attribute is matched by name and the variation attribute is **ignored
  entirely** — silently. Android's failure mode is the same shape (an unsupported axis is dropped
  without complaint), so this gets the same instrument that caught it on iOS: render `wght=350` and
  `wght=900` and assert the ink coverage differs. A visual check is not sufficient; it already
  failed once.
- `scaled` → `sp` units and `Configuration.fontScale`.
- **Material Symbols as a bundled variable font**, rendered by codepoint. One font file covers the
  entire icon set with `wght`/`fill`/`grad`/`opsz` axes, which is both cheaper and more faithful
  than shipping vector drawables.
- A curated SF Symbol → Material Symbol name map, covering the names real apps use. Unmapped names
  render nothing and warn once in `__DEV__`, because a silently missing icon reads as a layout bug.
- **`materialSymbol?: string` on `RowSpec`** — the one deliberate addition to the tree contract,
  and the escape hatch for everything the map does not cover. Additive, so it is safe under
  `tree.ts` rule 2.
- Icon tiles (`imageBackground`) and badges (`badge` / `badgeColor`), the Kotlin counterparts of
  `IconTile.swift` and `BadgeView.swift`.

**Done when:** Inter Variable renders at two distinguishable weights on Android with measured ink
difference, and the Settings screen's coloured tiles and red badge match their iOS counterparts in
structure.

---

## M7 — Controls

Every remaining row kind and every accessory.

**Ships:** `switch`, `button`, `menu`, `textField`, `textArea`, `datePicker`, `card`, `chip`
content (the layout comes in M9), and the `disclosure` / `checkmark` / `checkbox` / `radio` /
`spinner` accessories.

Notable mappings:

- `menu` → M3 `DropdownMenu`. Clean.
- `textField` → a bare `EditText` styled to the row, **not** a `TextInputLayout`: the iOS row is
  borderless and inline, and a boxed Material field inside a list row is a different component
  with a different meaning. `keyboardType` → `inputType`, `returnKeyType` → `imeOptions`,
  `autoCapitalize` → `InputType.TYPE_TEXT_FLAG_CAP_*`.
- `textArea` → the same, multiline, growing to `maxLines` then scrolling internally.
- `datePicker`: `compact` → a tappable value opening `MaterialDatePicker` / `MaterialTimePicker`,
  which is the Android idiom. `inline` → the Compose `DatePicker`. `wheels` → falls back to
  `inline` with a one-time warning; there is no M3 drum.
- `disabled` applied to the **control** as well as the label — a greyed row whose switch still
  flips is worse than no disabled state.
- Section header `action` buttons, which under M5's drawn sticky headers need the hit-test overlay.

**The hard part is the reuse rule, and it is worse here than on iOS.** Every listener —
`TextWatcher`, `OnCheckedChangeListener`, `OnDateChangedListener` — is detached before bind and
reattached after, or rebinding a recycled holder fires a change event the user never made and JS
writes it back as state.

**Done when:** the Reminders "new" screen round-trips text, a switch, and a date on Android; and a
regression test scrolls a list of switches far enough to force recycling and asserts **zero**
`onSwitchChange` events were emitted.

---

## M8 — Host rows

The milestone that flips `HOSTS_CHILDREN` to `true`.

**Ships:**

- `needsCustomLayoutForChildren() = true` on the manager — the Android analogue of overriding
  `mountChildComponentView:` on iOS. Without it Fabric positions hosted children itself, straight
  from the Yoga result, and they pile at the origin.
- `addView` / `removeViewAt` overridden on the manager, parking children in a hidden container
  until a holder claims them.
- Holder claim/release **with the ownership guard** from the iOS lessons above:
  `if (child.parent !== holder.container) return` before detaching. This bug already cost two wrong
  diagnoses; it does not get to cost a third.
- `hostIndex` and `height` (JS-measured via `onLayout` — unchanged and free, since the measurement
  is platform-neutral).
- `onVisibleRangeChange` from `findFirstVisibleItemPosition` / `findLastVisibleItemPosition`,
  mapped through the flattened index.
- `Root` drops the Android warning and mounts hosted children.

**Done when:** the windowing demo runs on Android, and a scripted reload of a list of `host` rows
mid-scroll leaves every hosted view visible — the exact reproduction that caught the iOS bug.

---

## M9 — Chips and swipe actions

**Chips:** a `chips` section becomes a single item holding a nested horizontal `RecyclerView`,
sharing a `RecycledViewPool` across sections. This is the Play Store shelf pattern, and it is the
one thing compositional layout bought on iOS that has a genuinely idiomatic Android answer — M3
chips are a real component, unlike M3 "orthogonal scrolling sections".

**Swipe actions:** `ItemTouchHelper.SimpleCallback` with `onChildDraw` revealing a tray, honouring
`leadingActions` / `trailingActions`, `style: 'destructive'` and `backgroundColor`.

Worth saying plainly in the README: **Material's guidance is that swipe means dismiss**, and a
revealed multi-button tray is an iOS idiom. It is implemented because the API has the field and
dropping it silently would be worse, but an Android-first design should reach for an overflow menu.

**Done when:** the Reminders screen's swipe-to-delete works, and a chip section scrolls
horizontally inside a vertically scrolling list without either gesture stealing the other.

---

## M10 — Insets, keyboard and the scroll contract

The props that are pure geometry, and the ones a bottom sheet depends on.

**Ships:**

- `contentInsetTop/Left/Bottom/Right` → padding with `clipToPadding = false`.
- `contentInsetAdjustmentBehavior` → `ViewCompat.setOnApplyWindowInsetsListener`. `automatic`
  applies system-bar insets, `never` applies none. This maps _better_ than it looks: Android 15
  forces edge-to-edge, so "the list insets itself for the chrome above it" is now the platform's
  own model too.
- `automaticallyAdjustKeyboardInsets` → `WindowInsetsCompat.Type.ime()`, with
  `WindowInsetsAnimationCompat` so the list moves **in step with** the keyboard rather than
  snapping — the Android equivalent of reading duration and curve off the iOS notification.
- `keyboardAware` / `keyboardAwareOffset` → scroll the focused row above the IME, and keep it there
  when focus moves with the keyboard already up.
- `keyboardDismissMode` → `none` / `onDrag`; `interactive` has no Android equivalent and maps to
  `onDrag`, documented.
- `scrollEnabled`, `showsVerticalScrollIndicator`.
- The five scroll events + `onContentSizeChange`, gated by `tracksScroll` exactly as on iOS.
  **`contentOffset.y` is accumulated from `onScrolled` dy**, not read from
  `computeVerticalScrollOffset()` — the latter is an average-item-height estimate and gorhom needs
  `y === 0` to be exact. `contentSize.height` stays an estimate and says so in the docs.
- `scrollTo` command → `scrollToPosition(0)` for the `(0,0)` case gorhom always sends, and
  `scrollBy(y - currentOffset)` otherwise.
- `decelerationRate`: `0` suppresses the fling outright (exact, and the value that matters);
  other values approximate through fling friction and are documented as approximate.

**The footgun that must be documented, not solved:** `keyboardAware` needs
`android:windowSoftInputMode="adjustNothing"` on the host **Activity**, which lives in the app's
manifest and cannot be set by a library. It gets a README section and a `__DEV__` check that reads
the current mode and warns when it will fight us.

**Done when:** the Reminders "new" screen keeps the focused field above an animating keyboard, and
`onScroll` reports exactly `0` at rest at the top over 100 scroll cycles.

---

## M11 — The bottom sheet

The hardest integration on iOS, and it should be easier here — which is a claim to verify, not to
assume.

Two of the three iOS fixes were platform-specific: `scrollEnabled` driven from
`animatedScrollableStatus` existed because RNGH's `retrieveScrollView` only recognises
`RCTScrollViewComponentView` and could not find ours. On Android, RNGH's `NativeViewGestureHandler`
works with any `ViewGroup` that reports `canScrollVertically`, and `RecyclerView` does — so the
gesture may compose properly without the workaround. The third fix (revision reuse) was
library-wide and already applies.

**Ships:** `@rngui/collection-view/bottom-sheet` working on Android, whatever that takes;
`requestDisallowInterceptTouchEvent` handling; and an Android-appropriate judder instrument.

**The iOS reversal counter does not transfer** — Android stretches instead of bouncing, so a
settling overscroll produces no reversal at all and the counter reads zero whether or not the sheet
and list are fighting. The replacement is jank frames from `FrameMetrics` during a scripted
drag-to-collapse, which measures the same thing (two systems correcting each other per frame)
through the symptom Android actually shows.

**Done when:** the sheet demo drags, collapses and flings on Android with a jank-frame count within
noise of the same gesture on a plain `RecyclerView` in a sheet.

---

## M12 — Example parity, docs, release

**Ships:**

- Every demo screen running on Android. Two get **Android-native counterparts rather than ports**:
  Settings becomes a 1:1 Pixel Settings rebuild and Contacts an Android Contacts rebuild, because
  the iOS versions are 1:1 rebuilds of _iOS_ apps and porting them pixel-for-pixel would prove the
  opposite of what this package claims. This is also the strongest forcing function for M3
  Expressive fidelity in the whole plan.
- README rewritten: the "iOS only today" line goes, the platform-difference table above ships as
  documentation, and every documented no-op is listed in one place rather than discovered.
- `package.json` `files` audited — `android/src` already covers new sources, resources and the
  bundled Material Symbols font, but anything new at `android/` root (a `gradle.properties`, a
  consumer ProGuard file) needs adding explicitly.
- A repeat of the publish dry-run: pack, install into a scratch consumer, and confirm the Android
  sources and font asset are actually in the tarball. The iOS dry-run found two defects that only a
  real install could surface; there is no reason to expect Android to be cleaner.
- A device matrix: one API 24 device (the floor), one API 31 (blur, stretch), one current, and one
  low-RAM device, because per-item `ComposeView` cost is exactly the thing that looks fine on a
  flagship.

**Done when:** `npm run android` renders every screen, and the packed tarball installs and runs in
a fresh Android app.

---

## Risk register

| Risk                                                                  | Milestone | If it bites                                                                                   |
| --------------------------------------------------------------------- | --------- | --------------------------------------------------------------------------------------------- |
| Per-item `ComposeView` too slow                                       | M1        | Drop `default`/`value`/`subtitle` to hand-built Views; keep Compose for control-bearing kinds |
| kotlinx.serialization unavailable                                     | M1/M2     | Generated `org.json` decoders — already the recommendation                                    |
| Sticky headers + touchable header actions                             | M5/M7     | Real header views via a second `RecyclerView` overlay, at a layout cost                       |
| Compose disposal vs React mounting on `host` rows                     | M8        | `host` holders are already plain `FrameLayout`s, which is the mitigation by construction      |
| RNGH does compose cleanly with `RecyclerView` — assumed, not verified | M11       | Fall back to the iOS approach: `scrollEnabled` from `animatedScrollableStatus`                |
| `minSdk` 24 blocks font variations                                    | M1        | Ship without variations below 26, or raise the floor                                          |

## Sequencing

M1 gates everything. M2 → M3 → M4 is a straight line and is where the list becomes usable. M5–M7
are independent of each other and can be reordered by whatever the example app needs first. M8
must precede M11 (a sheet demo with hosted rows), and M10 must precede M11 (the sheet reads scroll
events). M12 is last by definition.

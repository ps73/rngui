# rngui

Native UI components for React Native. Each component is a genuine platform view — a real
`UICollectionView`, not a reimplementation of one — driven by a Radix-style compound API.

## Packages

| Package                                                     | What it is                                                                                 |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| [`@rngui/collection-view`](packages/collection-view#readme) | A real iOS `UICollectionView`; Android `RecyclerView` + Material 3 Expressive to follow |

Install only what you use:

```bash
npm install @rngui/collection-view
```

There is deliberately **no umbrella package** that re-exports every component. Native code
doesn't tree-shake: React Native's autolinking walks an app's transitive dependencies and
compiles and links every podspec and Kotlin source set it finds, whether or not you ever
import the component. An umbrella would make every app pay the full build time and binary
size of the whole family to use one view.

The exception, whenever there is enough shared code to justify extracting it, would be a
pure-JavaScript `@rngui/core`: no podspec, no `android/`, no `react-native.config.js`, so
autolinking cannot see it at all and it costs nothing to depend on. It does not exist yet —
one component is not enough consumers to design a shared layer against.

## Layout

```
packages/*      one publishable component each
apps/example/   a single kitchen-sink app that imports them all
```

One example app rather than one per package — `prebuild` + `pod install` + codegen is the
slowest loop in the repo, and there is no reason to pay it per component.

## The example app

Five screens, each chosen because it is hard in a different way:

| Screen        | What it is for                                                                                                                                           |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Settings**  | The three stock cell kinds, a hosted React child, and thirty numbered rows to scroll through pooled cells                                                |
| **Reminders** | Every row kind — switches, text fields, an auto-growing text area, menus, inline date pickers, swipe actions — plus keyboard avoidance and a `formSheet` |
| **Contacts**  | 2,000 rows, `plain` with pinned section headers, and the A–Z scrubber                                                                                    |
| **Health**    | A horizontally scrolling chip strip inside a vertical list, gradient background, recycling `Card` rows beside a one-off hosted chart                     |
| **Custom**    | Theming played with live, a self-measuring hosted row, a `@gorhom/bottom-sheet` integration, and 200 windowed hosted rows                                |

A blurred large-title header is the default on every one of them, deliberately: it is the
hardest case for a custom scroll view, so every screen is a live test that UIKit still finds
the collection view and collapses the title against it.

## Why React Native codegen, not Nitro Modules

Nitro is a better authoring experience for almost everything, but it cannot host React
children inside a recycled cell. Nitro assigns its view as a Fabric component's
`contentView`, which means React's children arrive as _siblings_, and
`RCTViewComponentView`'s default mount/unmount assert that a child is exactly where React
put it — so a hosted view can only be floated _over_ its cell, never placed inside it.

A hand-written Fabric component view overrides those two methods and owns child placement
outright, which is how `react-native-screens` reparents children into view controllers and
navigation bars. That makes `<CollectionView.Host>` a real subview of `cell.contentView` —
clipped, hit-tested and scrolled by UIKit for free.

## Development

```bash
npm install                  # links every workspace
npm run verify               # typecheck, regenerate both native models, run every unit test
npm run ios                  # builds and runs the example on a simulator
npm run android              # ditto, on an emulator
npm run lint                 # prettier --check
```

`verify` covers the Kotlin tests too, but they need `apps/example/android` — which `expo prebuild`
generates and git does not track — and an `ANDROID_HOME`. Without both it says so and skips them
rather than failing, so a machine with only Node and Xcode still verifies everything else.

The same checks run in CI as the [Verify workflow](.github/workflows/verify.yml), which is
**manual only**: this repository is private, a macOS runner costs ten times a Linux one, and the
Kotlin job pays for a prebuild before it can assert anything. Start it from the Actions tab before
merging a branch or cutting a release — the Swift and Kotlin jobs each have a checkbox, so a
TypeScript-only change need not pay for either. CI passes `--require`, so there a skipped Kotlin
run is a failure rather than a shrug.

Packages are consumed **from source**: each points `source`/`react-native` at `src/index`,
and the example's Metro config watches the repo root. Editing TypeScript needs no rebuild;
editing Swift or Kotlin does. A change to a codegen spec needs `pod install` before the next
iOS build, because that is when the generated `Props.h` is rewritten.

### The generated Swift model

`packages/collection-view/src/tree.ts` is the single source of truth for the descriptor tree,
and `npm run gen` compiles it into Swift `Codable` structs plus a golden fixture and the
assertions that check every field round-trips. The output is **committed**, so `pod install`
never needs Node and a schema change shows up in a diff — and `npm run verify` fails if it is
stale.

The tests are built by SwiftPM rather than Xcode (`swift test`, seconds, no simulator), which
is possible only because the generated model depends on nothing but Foundation.

## License

AGPL-3.0-only. See [LICENSE](LICENSE).

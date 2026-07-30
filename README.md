# rngui

Native UI components for React Native. Each component is a genuine platform view — a real
`UICollectionView`, not a reimplementation of one — driven by a Radix-style compound API.

## Packages

| Package                                              | What it is                                                                              |
| ---------------------------------------------------- | --------------------------------------------------------------------------------------- |
| [`@rngui/collection-view`](packages/collection-view) | A real iOS `UICollectionView`; Android `RecyclerView` + Material 3 Expressive to follow |

Install only what you use:

```bash
npm install @rngui/collection-view
```

There is deliberately **no umbrella package** that re-exports every component. Native code
doesn't tree-shake: React Native's autolinking walks an app's transitive dependencies and
compiles and links every podspec and Kotlin source set it finds, whether or not you ever
import the component. An umbrella would make every app pay the full build time and binary
size of the whole family to use one view.

`@rngui/core` is the exception, and only because it is **pure JavaScript** — no podspec, no
`android/`, no `react-native.config.js`, so autolinking cannot see it at all.

## Layout

```
packages/*      one publishable component each, plus the pure-JS core
apps/example/   a single kitchen-sink app that imports them all
```

One example app rather than one per package — `prebuild` + `pod install` + codegen is the
slowest loop in the repo, and there is no reason to pay it per component.

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
npm run typecheck            # fans out across all packages + the example
npm run ios                  # builds and runs the example on a simulator
```

Packages are consumed **from source**: each points `source`/`react-native` at `src/index`,
and the example's Metro config watches the repo root. Editing TypeScript needs no rebuild;
editing Swift or Kotlin does.

## License

AGPL-3.0-only. See [LICENSE](LICENSE).

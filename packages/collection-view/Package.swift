// swift-tools-version:5.9
import PackageDescription

/**
 * A SwiftPM manifest covering *only* the generated descriptor model and its contract test.
 *
 * This is not how the library ships — that is `RNGUICollectionView.podspec`, and the real
 * implementation needs UIKit, Fabric and a running app. This exists so the one part that can be
 * tested in isolation actually is: `ios/Generated/TreeTypes.swift` depends on nothing but
 * Foundation, so `swift test` verifies the TypeScript↔Swift contract in seconds on the host,
 * with no simulator, no Xcode project and no `pod install`.
 *
 * Keeping the test cheap is the point. A contract test that requires a full native build is a
 * contract test that stops being run.
 */
let package = Package(
  name: "RNGUICollectionViewModel",
  platforms: [.macOS(.v13), .iOS(.v15)],
  targets: [
    .target(
      name: "RNGUICollectionViewModel",
      path: "ios/Generated"
    ),
    .testTarget(
      name: "RNGUICollectionViewModelTests",
      dependencies: ["RNGUICollectionViewModel"],
      path: "ios/Tests",
      resources: [.process("TreeTypesFixture.json")]
    ),
  ]
)

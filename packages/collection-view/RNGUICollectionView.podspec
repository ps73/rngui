require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

# The pod name is the *native* identity and is deliberately not the npm name. It is shared
# with `codegenConfig.name` and with the generated header path
# (`react/renderer/components/RNGUICollectionView/`), so renaming the package on npm is a
# metadata change that leaves generated code untouched. Renaming this is not.
Pod::Spec.new do |s|
  s.name         = "RNGUICollectionView"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = package["description"]
  s.license      = package["license"]
  s.author       = package["author"]
  s.homepage     = package["homepage"]

  # npm requires the object form of `repository` in a monorepo — it is what carries
  # `directory` — so the URL has to be reached through the hash rather than read as a
  # string.
  s.source       = { :git => package["repository"]["url"], :tag => "v#{s.version}" }

  # Tracks React Native's own floor rather than pinning a number, so consumers are free to
  # be on whatever RN supports. Anything newer (iOS 26 scroll-edge effects, for instance)
  # is reached with `if #available` in Swift rather than by raising this.
  s.platforms    = { :ios => min_ios_version_supported }

  s.source_files = "ios/**/*.{h,m,mm,swift}"

  # The contract test is built by SwiftPM (see Package.swift), never by the app. Without this
  # the glob above would compile an XCTest case into every consumer's binary.
  s.exclude_files = "ios/Tests/**/*"

  s.requires_arc = true

  # There is deliberately **no public header, and no header at all**, for the component
  # view. CocoaPods puts every public header into a generated umbrella header:
  #
  #     #ifdef __OBJC__
  #     #import "RNGUICollectionViewComponentView.h"
  #
  # and that umbrella is parsed by plain Objective-C translation units. Declaring the class
  # requires `RCTViewComponentView`, which transitively includes Fabric's C++ headers, so a
  # public header makes any `.m` file that causes clang to build this module fail with
  # `'atomic' file not found` from `react/renderer/core/EventBeat.h` — reported against an
  # unrelated pod, with nothing pointing back here.
  #
  # Note CocoaPods treats *all* headers as public when `public_header_files` is unset, so
  # the fix is having no header, not omitting this attribute. The `@interface` lives inside
  # the `.mm`; codegen's `ios.componentProvider` finds the class by name through
  # `NSClassFromString`, so nothing ever needs to import it.

  s.pod_target_xcconfig = {
    # Mandatory for a mixed ObjC++/Swift pod. Without a module, Xcode never generates
    # `RNGUICollectionView-Swift.h` anywhere the .mm can import it.
    "DEFINES_MODULE" => "YES",

    # Fabric's generated headers are C++20.
    "CLANG_CXX_LANGUAGE_STANDARD" => "c++20",

    # Deliberately NOT set, and both omissions are load-bearing:
    #
    #   SWIFT_OBJC_INTEROP_MODE => objcxx
    #     Would let Swift see C++, and then the Swift compile starts trying to import folly
    #     and jsi through our headers. The entire point of the .mm shim is that the Swift
    #     module never sees a C++ type.
    #
    #   SWIFT_INSTALL_OBJC_HEADER => NO
    #     Nitro sets this because it bridges through C++ interop instead of the ObjC header.
    #     We need the opposite — that generated header is how the .mm reaches Swift.
  }

  install_modules_dependencies(s)
end

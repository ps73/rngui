package com.rngui.collectionview

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

/**
 * Registers the view manager with the host app.
 *
 * Found by autolinking rather than by anything importing it: `expo-modules-autolinking` scans this
 * package's `android/` directory for a `*Package.kt` whose class extends `BaseReactPackage` (or
 * `TurboReactPackage`, or implements `ReactPackage`) and writes the import into the generated
 * `PackageList`. Renaming this file so it no longer ends in `Package.kt`, or changing what it
 * extends, silently unregisters the component — the class name is the contract.
 *
 * `BaseReactPackage` rather than the bare `ReactPackage` interface because it is the lazy form:
 * `getModule` is consulted by name instead of every module being constructed at startup. There are
 * no TurboModules here, so it always answers null.
 */
class RNGUICollectionViewPackage : BaseReactPackage() {
  override fun getModule(
    name: String,
    reactContext: ReactApplicationContext,
  ): NativeModule? = null

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider { emptyMap() }

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
    listOf(RNGUICollectionViewManager())
}

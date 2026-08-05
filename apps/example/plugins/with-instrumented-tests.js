const { withDangerousMod, withAppBuildGradle } = require('expo/config-plugins')
const fs = require('node:fs')
const path = require('node:path')

/**
 * Puts `androidTest/` into the generated Android project, and configures Gradle to run it.
 *
 * **These tests exist because two things in the library cannot be tested anywhere else.** The
 * component's attach-time restyle and the two `keyboardAware` hooks both live on
 * `RNGUICollectionViewView`, which takes a `ThemedReactContext` — so constructing one means having
 * a live React instance, which the library's own test suite has no way to produce. The example app
 * *is* a live React instance. Running the tests against it is the only honest way to cover those
 * paths, and both of them shipped as "verified by hand" or "verified by simulation" for want of it.
 *
 * `android/` is prebuild output and gitignored, so the sources live in `apps/example/androidTest/`
 * and this copies them in — the same arrangement, and for the same reason, as
 * `with-tab-drawables`.
 *
 * **The copy happens at prebuild, not at build**, which is a trap worth stating because it fails
 * quietly. Editing anything under `apps/example/androidTest/` and going straight to Gradle compiles
 * the *previous* copy: an edited test runs its old body, and a newly added one does not run at all
 * and is not reported missing — the run simply passes with one fewer test than expected. Run
 * `npm run prebuild` after touching these sources, or copy the file across by hand for a quick
 * loop. Counting the tests in the report is the cheap way to notice.
 *
 * **`-PrnguiInstrumentedTests` points the tests at the release variant, and both halves of that
 * matter.** A debug APK loads JavaScript from Metro, so the test would fail for want of a dev
 * server; and `expo-dev-client` puts its launcher in front of the app, so a test that did reach the
 * screen would find a "Start a local development server" panel rather than a list — which is
 * exactly what the first run of these tests found. Release bundles the JavaScript into the APK and
 * leaves the dev launcher out, which is also the build a user actually ships. It is unminified
 * (`android.enableMinifyInReleaseBuilds` defaults off) and signed with the debug keystore, so it
 * installs like any other.
 *
 * Gated on the property rather than set outright, because `testBuildType` is global: switching it
 * unconditionally would send the library's own `connectedDebugAndroidTest` somewhere unexpected.
 *
 * ```bash
 * ./gradlew :app:connectedReleaseAndroidTest -PrnguiInstrumentedTests
 * ```
 */
module.exports = function withInstrumentedTests(config) {
  config = withDangerousMod(config, [
    'android',
    async (config) => {
      const source = path.join(config.modRequest.projectRoot, 'androidTest')
      if (!fs.existsSync(source)) return config

      const destination = path.join(
        config.modRequest.platformProjectRoot,
        'app',
        'src',
        'androidTest',
        'java'
      )
      fs.rmSync(destination, { recursive: true, force: true })
      fs.cpSync(source, destination, { recursive: true })
      return config
    },
  ])

  return withAppBuildGradle(config, (config) => {
    config.modResults.contents = patch(config.modResults.contents)
    return config
  })
}

/** Idempotent: prebuild regenerates the file, but a re-run over an already-patched one must not double up. */
function patch(contents) {
  if (contents.includes(MARKER)) return contents

  const withRunner = contents.replace(
    /(defaultConfig\s*\{)/,
    `$1
        // ${MARKER}
        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'`
  )

  const withTestBuildType = withRunner.replace(
    /(defaultConfig\s*\{)/,
    `// ${MARKER} — see plugins/with-instrumented-tests.js
    testBuildType = project.hasProperty('rnguiInstrumentedTests') ? 'release' : 'debug'

    $1`
  )

  return withTestBuildType.replace(
    /(dependencies\s*\{)/,
    `$1
    // ${MARKER}
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
    androidTestImplementation 'androidx.test:runner:1.6.2'
    androidTestImplementation 'androidx.test:rules:1.6.1'
    androidTestImplementation 'androidx.test.uiautomator:uiautomator:2.3.0'
    // The library declares this \`implementation\`, so it is on the runtime classpath but not the
    // compile one — and \`RNGUICollectionViewView.list\` is a RecyclerView. Kept at the library's
    // version so the two cannot resolve to different classes.
    androidTestImplementation 'androidx.recyclerview:recyclerview:1.4.0'`
  )
}

const MARKER = 'rngui:instrumented-tests'

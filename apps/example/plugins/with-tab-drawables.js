const { withDangerousMod } = require('expo/config-plugins')
const fs = require('node:fs')
const path = require('node:path')

/**
 * Copies `assets/android/drawable/*.xml` into the generated Android project.
 *
 * A native tab bar draws the *platform's* icons, so `<NativeTabs.Trigger.Icon>` takes an SF Symbol
 * name on iOS and an Android drawable *resource* name on the other side — and a resource has to
 * exist in the app's res tree before anything can name it. `android/` is prebuild output and is
 * gitignored, so the drawables live in `assets/` and this puts them where AAPT will see them.
 *
 * A dangerous mod rather than a resource mod because there is no typed mod for "add arbitrary
 * drawable files"; the operation is a file copy and pretending otherwise would be more code, not
 * less.
 */
module.exports = function withTabDrawables(config) {
  return withDangerousMod(config, [
    'android',
    async (config) => {
      const source = path.join(
        config.modRequest.projectRoot,
        'assets',
        'android',
        'drawable'
      )
      const destination = path.join(
        config.modRequest.platformProjectRoot,
        'app',
        'src',
        'main',
        'res',
        'drawable'
      )

      if (!fs.existsSync(source)) return config
      fs.mkdirSync(destination, { recursive: true })
      for (const file of fs.readdirSync(source)) {
        fs.copyFileSync(path.join(source, file), path.join(destination, file))
      }
      return config
    },
  ])
}

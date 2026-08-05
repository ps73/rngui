const path = require('node:path')
const { getDefaultConfig } = require('expo/metro-config')

const root = path.resolve(__dirname, '../..')
const config = getDefaultConfig(__dirname)

// The packages are consumed from source — npm workspaces symlinks them into the root
// node_modules, and each package points `source`/`react-native` at src/index. Metro has
// to watch the repo root to pick those edits up.
config.watchFolders = [root]

// Hierarchical lookup stays ON. The usual monorepo advice is to set
// `disableHierarchicalLookup: true`, but that breaks npm workspaces specifically: npm
// nests some of Expo's own dependencies under node_modules/expo/node_modules, and
// without the upward walk they become unresolvable.
config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, 'node_modules'),
  path.resolve(root, 'node_modules'),
]

/**
 * The built output is invisible to Metro, and that is load-bearing.
 *
 * The comment above says the packages are consumed from source — and they were, right up until
 * `bob build` started running from a `prepare` script. Once `packages/collection-view/lib` exists,
 * Metro resolves the package through `main` and serves that instead, so the app silently runs
 * whatever the library looked like the last time anyone packed it. Every source edit after that
 * point is invisible: no error, no warning, just an app that does not change when you change it.
 *
 * Blocking the directory is what makes "consumed from source" true rather than merely intended.
 * It cost a long debugging session to find, because the symptom is a feature that used to work and
 * quietly stopped.
 */
config.resolver.blockList = [
  ...(Array.isArray(config.resolver.blockList)
    ? config.resolver.blockList
    : config.resolver.blockList
      ? [config.resolver.blockList]
      : []),
  new RegExp(`${path.resolve(root, 'packages')}/[^/]+/lib/.*`),
]

module.exports = config

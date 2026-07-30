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

module.exports = config

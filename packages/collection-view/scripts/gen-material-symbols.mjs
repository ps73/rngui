/**
 * Emits the Kotlin symbol table, and subsets the Material Symbols font down to what it names.
 *
 * Two artifacts, both committed:
 *
 * - `android/src/main/java/.../generated/MaterialSymbols.kt` — SF Symbol name → codepoint, and
 *   Material Symbol name → codepoint, as one lookup each.
 * - `android/src/main/assets/rngui_material_symbols.ttf` — the subset face.
 *
 * **The font is subset because the full one is 15 MB.** The plan called for bundling the variable
 * font whole, on the grounds that one file covering the entire icon set is cheaper than shipping
 * vector drawables — which is true against drawables and untrue against a consuming app's download
 * size. Four thousand icons is not a rounding error in an npm tarball. Subsetting keeps the
 * variable axes (`wght`, `FILL`, `GRAD`, `opsz`) that made the variable font the right choice in
 * the first place, and drops only glyphs nothing can name.
 *
 * Subsetting needs `pyftsubset` from fonttools, which is why the output is committed: a Gradle sync
 * must not require Python any more than it requires Node.
 *
 *   npm run gen:material-symbols
 */
import { execFileSync } from 'node:child_process'
import {
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SF_TO_MATERIAL, EXTRA_MATERIAL_SYMBOLS } from './symbol-map.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.join(here, '..')

const CODEPOINTS = path.join(root, 'assets', 'material-symbols.codepoints')
const SOURCE_FONT = path.join(root, 'assets', 'material-symbols-source.ttf')
const OUT_FONT = path.join(
  root,
  'android',
  'src',
  'main',
  'assets',
  'rngui_material_symbols.ttf'
)
const OUT_KT = path.join(
  root,
  'android',
  'src',
  'main',
  'java',
  'com',
  'rngui',
  'collectionview',
  'generated',
  'MaterialSymbols.kt'
)

// --- read the codepoint table -------------------------------------------------

const codepoints = new Map()
for (const line of readFileSync(CODEPOINTS, 'utf8').split('\n')) {
  const [name, hex] = line.trim().split(/\s+/)
  if (name && hex) codepoints.set(name, parseInt(hex, 16))
}

/** Every Material name the build needs a glyph for. */
const wanted = new Set([
  ...Object.values(SF_TO_MATERIAL),
  ...EXTRA_MATERIAL_SYMBOLS,
])

const unknown = [...wanted].filter((name) => !codepoints.has(name))
if (unknown.length > 0) {
  throw new Error(
    `gen-material-symbols: no such Material Symbol: ${unknown.join(', ')}. ` +
      'Check the name against assets/material-symbols.codepoints — a typo here would ship a ' +
      'blank row rather than fail, which is why this throws.'
  )
}

// --- emit the Kotlin table ----------------------------------------------------

const sfEntries = Object.entries(SF_TO_MATERIAL)
  .sort(([a], [b]) => (a < b ? -1 : 1))
  .map(
    ([sf, material]) =>
      `    "${sf}" to 0x${codepoints.get(material).toString(16)},`
  )

const materialEntries = [...wanted]
  .sort()
  .map((name) => `    "${name}" to 0x${codepoints.get(name).toString(16)},`)

const kotlin = `// Generated from scripts/symbol-map.mjs by scripts/gen-material-symbols.mjs. Do not edit.
//
// Run \`npm run gen:material-symbols\` after changing the map. Committed on purpose: a Gradle sync
// must not require Node, and a change to which icons ship should be visible in a diff.

package com.rngui.collectionview.generated

/**
 * Icon names to codepoints in the bundled Material Symbols face.
 *
 * Two tables rather than one, because they answer different questions and fail differently. A
 * \`systemImage\` this map has never heard of is a *mapping* gap — SF Symbols has thousands of names
 * and this covers the ones real apps use. A \`materialSymbol\` it has never heard of is a *subset*
 * gap: the name may well be a real Material Symbol, but the bundled face is subset and does not
 * carry its glyph. Both render nothing and warn once; the warnings say different things.
 */
object MaterialSymbols {
  /** The bundled face, subset to exactly the glyphs these tables name. */
  const val FONT_ASSET = "rngui_material_symbols.ttf"

  /** SF Symbol name, as \`RowSpec.systemImage\` spells it. */
  val bySfName: Map<String, Int> =
    mapOf(
${sfEntries.join('\n')}
    )

  /** Material Symbol name, as \`RowSpec.materialSymbol\` spells it. */
  val byMaterialName: Map<String, Int> =
    mapOf(
${materialEntries.join('\n')}
    )
}
`

mkdirSync(path.dirname(OUT_KT), { recursive: true })
writeFileSync(OUT_KT, kotlin)
console.log(
  `wrote ${path.relative(process.cwd(), OUT_KT)} ` +
    `(${sfEntries.length} SF names, ${materialEntries.length} glyphs)`
)

// --- subset the font ----------------------------------------------------------

if (!existsSync(SOURCE_FONT)) {
  console.log(
    `skipping the font: ${path.relative(process.cwd(), SOURCE_FONT)} is not present.\n` +
      'Fetch it once with:\n' +
      '  curl -sSL -o packages/collection-view/assets/material-symbols-source.ttf \\\n' +
      "    'https://github.com/google/material-design-icons/raw/master/variablefont/" +
      "MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf'\n" +
      'It is deliberately not committed — 15 MB of source for a 200 KB artifact.'
  )
  process.exit(0)
}

const unicodes = [...wanted]
  .map((name) => codepoints.get(name).toString(16))
  .join(',')

mkdirSync(path.dirname(OUT_FONT), { recursive: true })
execFileSync(
  'pyftsubset',
  [
    SOURCE_FONT,
    `--unicodes=${unicodes}`,
    `--output-file=${OUT_FONT}`,
    // Keep the axes. They are the reason a variable font was the right choice: `wght` is what lets
    // an icon match the row's text weight, and `FILL` is the difference between an outlined and a
    // filled glyph without a second file. `gvar` is most of the remaining bulk and is exactly what
    // must not be dropped.
    //
    // Everything else goes. Icons are drawn one codepoint at a time, so there is no shaping to do:
    // GSUB and GPOS exist for ligatures and kerning that this never invokes, and keeping them cost
    // 300 KB of a 1 MB file for nothing.
    '--drop-tables+=DSIG,GSUB,GPOS',
    '--layout-features=',
    '--name-IDs=0,1,2,3,4,5,6',
    '--recalc-bounds',
  ],
  { stdio: 'inherit' }
)

const sourceSize = statSync(SOURCE_FONT).size
const outSize = statSync(OUT_FONT).size
console.log(
  `wrote ${path.relative(process.cwd(), OUT_FONT)} — ` +
    `${(outSize / 1024).toFixed(0)} KB, from ${(sourceSize / 1024 / 1024).toFixed(1)} MB`
)

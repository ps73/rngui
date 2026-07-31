/**
 * Generates the Kotlin descriptor model from `src/tree.ts`, the counterpart to
 * `gen-swift-types.mjs`.
 *
 * Everything the Swift generator's header says applies here — TypeScript is the single source of
 * truth, the output is committed so a Gradle sync never needs Node, and CI fails on a stale file.
 * Two things are specific to this side:
 *
 * 1. **The decoders are hand-written `org.json`, and that is a decision rather than an
 *    oversight.** kotlinx.serialization is a *compiler plugin*, so using it would mean putting
 *    `org.jetbrains.kotlin.plugin.serialization` on the consuming app's root buildscript
 *    classpath — which React Native app templates, Expo's included, do not do, and which
 *    `android/build.gradle` deliberately refuses to reach into. Moshi wants KSP (another plugin)
 *    or `kotlin-reflect` (a dependency and R8 rules); Gson wants reflection rules. Emitting the
 *    decoder costs one generator and buys zero plugins, zero runtime dependencies and zero
 *    ProGuard rules. See `docs/android-plan.md` § M1 for the measurement that settled it.
 *
 * 2. **`data class`, not `class`.** `DiffUtil.areContentsTheSame` is then correct for free, which
 *    is what makes the adapter's diffing trustworthy — and structural equality is exactly what
 *    the Swift side gets from `Equatable`.
 *
 * `org.json` is more forgiving than `JSONDecoder`: it coerces a type mismatch rather than
 * throwing, so Android accepts a superset of what iOS does. That asymmetry runs in the safe
 * direction — leniency is the documented goal of both — and is not worth code to remove.
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { readTreeSchema, resolvePath } from './tree-schema.mjs'
import { synthesizeFixture } from './tree-fixture.mjs'
import { FORWARD_COMPAT_ASSERTIONS } from './forward-compat-fixture.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const SOURCE = path.join(here, '..', 'src', 'tree.ts')
const TOOL = 'gen-kotlin-types'
const ROOT = 'Tree'

const PACKAGE = 'com.rngui.collectionview.generated'
const MAIN_DIR = path.join(
  here,
  '..',
  'android',
  'src',
  'main',
  'java',
  ...PACKAGE.split('.')
)
const TEST_DIR = path.join(
  here,
  '..',
  'android',
  'src',
  'test',
  'java',
  ...PACKAGE.split('.')
)
const OUTPUT = path.join(MAIN_DIR, 'TreeTypes.kt')

/**
 * Kotlin's *hard* keywords — the ones that cannot appear as an identifier anywhere.
 *
 * Enough for a property name, because the generator always writes `val` in front of one, which
 * puts the name in plain identifier position. Not enough for an enum entry: see below.
 */
const KOTLIN_HARD_KEYWORDS = new Set([
  'as',
  'break',
  'class',
  'continue',
  'do',
  'else',
  'false',
  'for',
  'fun',
  'if',
  'in',
  'interface',
  'is',
  'null',
  'object',
  'package',
  'return',
  'super',
  'this',
  'throw',
  'true',
  'try',
  'typealias',
  'typeof',
  'val',
  'var',
  'when',
  'while',
])

/**
 * Also the modifier and soft keywords, which an *enum entry* cannot be either.
 *
 * An enum entry starts a declaration, so the parser reads `inline("inline")` as the `inline`
 * modifier followed by something that is not a declaration — and `DatePickerStyle.inline` is a
 * real case in `tree.ts`. The failure is a wall of "Expecting member declaration" pointing at
 * every line *after* the offending one, which is why this list is worth carrying explicitly
 * rather than discovering per value.
 */
const KOTLIN_DECLARATION_KEYWORDS = new Set([
  ...KOTLIN_HARD_KEYWORDS,
  // Modifiers.
  'abstract',
  'actual',
  'annotation',
  'companion',
  'const',
  'crossinline',
  'data',
  'enum',
  'expect',
  'external',
  'final',
  'infix',
  'inline',
  'inner',
  'internal',
  'lateinit',
  'noinline',
  'open',
  'operator',
  'out',
  'override',
  'private',
  'protected',
  'public',
  'reified',
  'sealed',
  'suspend',
  'tailrec',
  'vararg',
  // Soft keywords.
  'by',
  'catch',
  'constructor',
  'delegate',
  'dynamic',
  'field',
  'file',
  'finally',
  'get',
  'import',
  'init',
  'param',
  'property',
  'receiver',
  'set',
  'setparam',
  'value',
  'where',
])

/** For property names and named arguments, which sit in plain identifier position. */
const escape = (name) => (KOTLIN_HARD_KEYWORDS.has(name) ? `\`${name}\`` : name)

/**
 * For enum entries, and for references to them.
 *
 * Backticks are transparent — `` `inline` `` and `inline` name the same entry — so escaping a
 * reference that would have been fine anyway costs nothing but keeps declaration and use spelled
 * identically.
 */
const escapeEntry = (name) =>
  KOTLIN_DECLARATION_KEYWORDS.has(name) ? `\`${name}\`` : name

/** Renders a doc comment as KDoc at the given indent, preserving the author's line breaks. */
function renderDoc(doc, indent) {
  if (doc == null) return ''
  // A `*/` inside the text would close the block early. Nothing in tree.ts has one today; this
  // is here so that adding one is a cosmetic problem rather than a broken build.
  const safe = doc.replaceAll('*/', '*\\/')
  const lines = safe.split('\n').map((line) => `${indent} * ${line}`.trimEnd())
  return `${indent}/**\n${lines.join('\n')}\n${indent} */\n`
}

/** Spells a shape in Kotlin, plus the default used when the key is absent. */
function mapType(shape, context) {
  switch (shape.shape) {
    case 'string':
      return { type: 'String', fallback: '""' }
    case 'number':
      return { type: 'Double', fallback: '0.0' }
    case 'int':
      return { type: 'Int', fallback: '0' }
    case 'boolean':
      return { type: 'Boolean', fallback: 'false' }
    case 'array':
      return {
        type: `List<${mapType(shape.element, context).type}>`,
        fallback: 'emptyList()',
      }
    case 'enum': {
      const [first] = context.enums.get(shape.name)
      return {
        type: shape.name,
        fallback: `${shape.name}.${escapeEntry(first)}`,
      }
    }
    default:
      // A struct. No zero value, so it must be optional or inside an array.
      return { type: shape.name, fallback: null }
  }
}

const { enums, structs } = readTreeSchema({
  sourcePath: SOURCE,
  tool: TOOL,
  mapType,
})

// --- decoder expressions ------------------------------------------------------

/**
 * The expression that reads one shape out of a `JSONObject` under `key`, or null when absent.
 *
 * Always nullable, so the caller decides between `?: fallback` for a required field and nothing
 * at all for an optional one. That mirrors the Swift `decodeIfPresent(...) ?? default` shape
 * exactly, which is the point: an absent key takes the default, an unrecognised *enum value*
 * degrades to `unknown`, and the two are distinguishable on both platforms.
 */
function readExpression(shape, keyLiteral) {
  switch (shape.shape) {
    case 'string':
      return `json.string(${keyLiteral})`
    case 'number':
      return `json.double(${keyLiteral})`
    case 'int':
      return `json.int(${keyLiteral})`
    case 'boolean':
      return `json.boolean(${keyLiteral})`
    case 'enum':
      return `json.string(${keyLiteral})?.let(${shape.name}::from)`
    case 'struct':
      return `json.obj(${keyLiteral})?.let(${shape.name}::from)`
    case 'array':
      return `json.array(${keyLiteral})?.map { ${elementExpression(shape.element)} }`
    default:
      throw new Error(`${TOOL}: no decoder for shape '${shape.shape}'.`)
  }
}

/** The per-element expression inside a `JSONArray.map`, where `it` is the raw element. */
function elementExpression(shape) {
  switch (shape.shape) {
    case 'string':
      return 'it as? String ?: ""'
    case 'number':
      return '(it as? Number)?.toDouble() ?: 0.0'
    case 'int':
      return '(it as? Number)?.toInt() ?: 0'
    case 'boolean':
      return 'it as? Boolean ?: false'
    case 'enum':
      return `(it as? String)?.let(${shape.name}::from) ?: ${shape.name}.unknown`
    case 'struct':
      // A non-object element becomes an all-defaults instance rather than shortening the list.
      // Losing a row's *contents* is recoverable; losing the row shifts every index after it,
      // and `hostIndex` is an index into exactly this list.
      return `(it as? JSONObject)?.let(${shape.name}::from) ?: ${shape.name}()`
    default:
      throw new Error(`${TOOL}: no element decoder for shape '${shape.shape}'.`)
  }
}

// --- emit the model -----------------------------------------------------------

const out = []

out.push(`// Generated from src/tree.ts by scripts/gen-kotlin-types.mjs. Do not edit.
//
// Run \`npm run gen:kotlin-types\` after changing the descriptor types. The output is committed on
// purpose: a Gradle sync must not require Node, and a schema change should be visible in a diff.
// CI fails if this file is stale.
//
// Every field decodes leniently, and an unrecognised enum value degrades to \`unknown\` rather
// than failing the payload. \`expo-updates\` can ship a JS bundle newer than the native binary it
// runs against, so "this build doesn't know that row kind yet" has to mean one dull row rather
// than an empty list.

package ${PACKAGE}

import org.json.JSONObject
import org.json.JSONTokener
`)

for (const [name, values] of enums) {
  out.push(
    `${renderDoc(`Generated from \`${name}\` in tree.ts.`, '')}enum class ${name}(val raw: String) {`
  )
  for (const value of values) {
    out.push(`  ${escapeEntry(value)}("${value}"),`)
  }
  out.push(`
  /** A value this binary does not recognise. */
  unknown("");

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun from(raw: String): ${name} = byRaw[raw] ?: unknown
  }
}
`)
}

for (const { name, doc, properties } of structs) {
  // `data class` for structural equality: `DiffUtil.areContentsTheSame` is then correct without
  // anyone writing it, which is the same guarantee `Equatable` gives the Swift side.
  out.push(`${renderDoc(doc, '')}data class ${name}(`)

  for (const property of properties) {
    const propertyDoc = renderDoc(property.doc, '  ')
    if (propertyDoc) out.push(propertyDoc.trimEnd())
    out.push(
      property.optional
        ? `  val ${escape(property.name)}: ${property.type}? = null,`
        : `  val ${escape(property.name)}: ${property.type} = ${property.fallback},`
    )
  }

  out.push(`) {
  companion object {`)

  if (name === ROOT) {
    out.push(`    /**
     * Decodes a whole tree, or an empty one if the string is not an object.
     *
     * The only entry point the rest of the library needs, and the reason nothing outside this
     * file imports \`org.json\`. Malformed input renders an empty list rather than throwing:
     * a crash here would be a crash on a prop update, from a string JavaScript built.
     */
    @JvmStatic
    fun decode(json: String?): ${ROOT} {
      if (json.isNullOrEmpty()) return ${ROOT}()
      return runCatching { JSONTokener(json).nextValue() as? JSONObject }
        .getOrNull()
        ?.let(::from)
        ?: ${ROOT}()
    }
`)
  }

  out.push(`    fun from(json: JSONObject): ${name} =
      ${name}(`)
  for (const property of properties) {
    const read = readExpression(property.shape, `"${property.name}"`)
    out.push(
      property.optional
        ? `        ${escape(property.name)} = ${read},`
        : `        ${escape(property.name)} = ${read} ?: ${property.fallback},`
    )
  }
  out.push(`      )
  }
}
`)
}

out.push(`// -----------------------------------------------------------------------------
// Reading helpers
//
// \`private\` on purpose: these are the generated decoders' business and nothing else's. A caller
// outside this file wanting JSON is a caller who should have been handed a decoded type.
//
// Each returns null for "absent or JSON null" — \`JSONObject.isNull\` is true for both — so the
// generated code can spell "missing takes the default" as a single \`?:\`.
// -----------------------------------------------------------------------------

private fun JSONObject.string(key: String): String? = if (isNull(key)) null else optString(key)

private fun JSONObject.double(key: String): Double? =
  if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun JSONObject.boolean(key: String): Boolean? = if (isNull(key)) null else optBoolean(key)

private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

/** The raw elements of an array, so callers can spell their own per-element coercion. */
private fun JSONObject.array(key: String): List<Any?>? =
  optJSONArray(key)?.let { array -> List(array.length()) { array.opt(it) } }
`)

// --- emit the tests -----------------------------------------------------------

const { checks } = synthesizeFixture({ structs, enums, root: ROOT })

/**
 * Renders one shared accessor path as Kotlin.
 *
 * Every link of a Kotlin safe call has to be spelled: once the chain is nullable it stays
 * nullable, so `menuItems?.get(0)?.id` — the exact opposite of Swift, where the single `?` in
 * `menuItems?[0].id` covers the rest. That is why this is not shared with the Swift generator.
 */
function kotlinPath(path) {
  let expression = 'decoded'
  let nullable = false

  for (const step of resolvePath(structs, ROOT, path)) {
    const dot = nullable ? '?.' : '.'
    if (step.kind === 'index') {
      expression += `${dot}get(${step.index})`
      continue
    }
    expression += `${dot}${step.kind === 'count' ? 'size' : escape(step.name)}`
    if (step.kind === 'property') nullable ||= step.optional
  }

  return expression
}

/**
 * Spells one shared expectation as a Kotlin literal.
 *
 * `assertEquals` compares boxed values, so `99` and `99.0` are *not* equal — which is exactly the
 * distinction `IntValue` exists to carry, and the reason the shared checks keep `int` and
 * `double` apart instead of collapsing both to "number".
 */
function kotlinLiteral(expect) {
  switch (expect.kind) {
    case 'string':
      return JSON.stringify(expect.value)
    case 'boolean':
      return 'true'
    case 'enum':
      return `${expect.name}.${escapeEntry(expect.value)}`
    case 'int':
      return String(expect.value)
    case 'double':
      return `${expect.value}.0`
    default:
      // A `.count`, which is an `Int` on both sides.
      return String(expect.value)
  }
}

const assertions = checks.map(
  ({ path, expect }) =>
    `assertEquals(${kotlinLiteral(expect)}, ${kotlinPath(path)})`
)

const forwardCompatAssertions = FORWARD_COMPAT_ASSERTIONS.map(
  ([description, path, expected]) => {
    const literal =
      typeof expected === 'object' && expected !== null && 'enum' in expected
        ? `${expected.enum}.${escapeEntry(expected.value)}`
        : JSON.stringify(expected)
    return `assertEquals(${JSON.stringify(description)}, ${literal}, ${kotlinPath(path)})`
  }
)

const testSource = `// Generated from src/tree.ts by scripts/gen-kotlin-types.mjs. Do not edit.
//
// Decodes the generated fixture and asserts that *every* field arrived. Each fixture value is
// deliberately different from the field's default, so a field the decoder skipped shows up as a
// failed assertion rather than as a plausible-looking zero.
//
// This is the contract test between src/tree.ts and the generated Kotlin model, and it reads the
// *same fixture files the Swift test reads* — \`ios/Tests/\`, passed in by Gradle as
// \`rngui.fixtureDir\` rather than copied, because two copies of a contract fixture is two
// contracts. What the two platforms accept cannot drift while this passes.
//
// Run it through the example app's Gradle:
//   apps/example/android/gradlew -p apps/example/android \\
//     :rngui_collection-view:testDebugUnitTest

package ${PACKAGE}

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeTypesTest {
  private fun load(name: String): String {
    val dir =
      requireNotNull(System.getProperty("rngui.fixtureDir")) {
        "rngui.fixtureDir is unset — see the systemProperty in android/build.gradle"
      }
    val file = File(dir, "\$name.json")
    assertTrue("\$file missing; run 'npm run gen'", file.isFile)
    return file.readText()
  }

  /** Every field in the schema decodes to its fixture value. */
  @Test
  fun everyFieldRoundTrips() {
    val decoded = ${ROOT}.decode(load("TreeTypesFixture"))

${assertions.map((line) => `    ${line}`).join('\n')}
  }

  /**
   * A tree from a *newer* JS bundle than this binary still decodes to a usable list.
   *
   * The same fixture and the same assertions run on iOS — see
   * \`ios/Tests/TreeTypesTests.swift\`. A decoder that is lenient on one platform and strict on
   * the other is worse than one that is strict on both, because only one of the two phones goes
   * blank.
   */
  @Test
  fun forwardCompatibleTreeDecodes() {
    val decoded = ${ROOT}.decode(load("ForwardCompatFixture"))

${forwardCompatAssertions.map((line) => `    ${line}`).join('\n')}
  }

  /** A missing key takes the field's default rather than throwing. */
  @Test
  fun missingKeysTakeDefaults() {
    assertEquals(0, ${ROOT}.decode("{}").sections.size)
  }

  /** An unrecognised enum value degrades to \`unknown\` instead of failing the whole payload. */
  @Test
  fun unknownEnumDegradesInsteadOfThrowing() {
    val decoded = ${ROOT}.decode("""{"sections":[{"id":"s","rows":[{"id":"r","kind":"nope"}]}]}""")
    assertEquals(RowKind.unknown, decoded.sections[0].rows[0].kind)
  }

  /**
   * Malformed JSON renders an empty list rather than throwing.
   *
   * There is no Swift counterpart because there is no equivalent risk: \`tree\` arrives as a prop
   * on the UI thread, and a decoder that throws there takes the app with it.
   */
  @Test
  fun malformedJsonDecodesToAnEmptyTree() {
    assertEquals(0, ${ROOT}.decode("not json at all").sections.size)
    assertEquals(0, ${ROOT}.decode("[]").sections.size)
    assertEquals(0, ${ROOT}.decode(null).sections.size)
  }
}
`

const artifacts = [
  [OUTPUT, `${out.join('\n')}\n`],
  [path.join(TEST_DIR, 'TreeTypesTest.kt'), testSource],
]

for (const [file, contents] of artifacts) {
  mkdirSync(path.dirname(file), { recursive: true })
  const previous = existsSync(file) ? readFileSync(file, 'utf8') : null
  writeFileSync(file, contents)
  console.log(
    `${previous === contents ? 'unchanged' : 'wrote'} ${path.relative(process.cwd(), file)}`
  )
}

console.log(
  `${enums.size} enum(s), ${structs.length} data class(es), ${assertions.length} assertion(s)`
)

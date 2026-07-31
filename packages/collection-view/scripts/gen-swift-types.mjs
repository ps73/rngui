/**
 * Generates `ios/Generated/TreeTypes.swift` from `src/tree.ts`.
 *
 * The descriptor tree crosses into native as JSON, which means TypeScript and Swift have to
 * agree on a schema that neither compiler can check against the other. Hand-writing both
 * sides works right up until someone adds a field to one of them; then the list silently
 * loses a column and nothing anywhere reports it. So TypeScript is the single source of truth
 * and this emits the Swift.
 *
 * Two deliberate properties of the output:
 *
 * 1. **Everything decodes leniently.** `JSONDecoder` fails an *entire* payload on one missing
 *    key or one unrecognised enum case, and a JS bundle can be newer than the native binary
 *    running it — `expo-updates` ships JavaScript alone. Strict decoding would turn "this
 *    build doesn't know about that row kind yet" into "the list is empty, with no error".
 *    Every field falls back to a default and every enum has an `unknown` case.
 *
 * 2. **The output is committed.** `pod install` must not need Node, and a generated file in
 *    the tree is reviewable in a diff. CI runs this and fails on `git diff --exit-code`.
 *
 * Reads the syntax tree only — never the type checker — so the mapping is decided by what is
 * *written* in the annotation. That is what makes the `IntValue` marker work: as a resolved
 * type it is just `number`, but as syntax it is a name this script can recognise.
 */
import ts from 'typescript'
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { readTreeSchema, resolvePath } from './tree-schema.mjs'
import { synthesizeFixture } from './tree-fixture.mjs'
import {
  FORWARD_COMPAT_FIXTURE,
  FORWARD_COMPAT_ASSERTIONS,
} from './forward-compat-fixture.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const SOURCE = path.join(here, '..', 'src', 'tree.ts')
const OUTPUT = path.join(here, '..', 'ios', 'Generated', 'TreeTypes.swift')
const TOOL = 'gen-swift-types'

/** Swift keywords that have to be escaped when used as an enum case or property name. */
const SWIFT_KEYWORDS = new Set([
  'associatedtype',
  'class',
  'default',
  'deinit',
  'enum',
  'extension',
  'fileprivate',
  'func',
  'import',
  'init',
  'inout',
  'internal',
  'let',
  'operator',
  'private',
  'protocol',
  'public',
  'rethrows',
  'static',
  'struct',
  'subscript',
  'typealias',
  'var',
  'break',
  'case',
  'continue',
  'defer',
  'do',
  'else',
  'fallthrough',
  'for',
  'guard',
  'if',
  'in',
  'repeat',
  'return',
  'switch',
  'where',
  'while',
  'as',
  'catch',
  'false',
  'is',
  'nil',
  'super',
  'self',
  'Self',
  'throw',
  'throws',
  'true',
  'try',
  'any',
  'some',
])

const escape = (name) => (SWIFT_KEYWORDS.has(name) ? `\`${name}\`` : name)

/** Renders a doc comment at the given indent, preserving the author's line breaks. */
function renderDoc(doc, indent) {
  if (doc == null) return ''
  const lines = doc.split('\n').map((line) => `${indent}/// ${line}`.trimEnd())
  return `${lines.join('\n')}\n`
}

/**
 * Spells a shape in Swift, plus the default used when the key is absent.
 *
 * `fallback` is null for types that have no sensible zero value — a struct, for instance —
 * which is what forces such fields to be declared optional in `tree.ts`.
 */
function mapType(shape, context) {
  switch (shape.shape) {
    case 'string':
      return { type: 'String', fallback: '""' }
    case 'number':
      return { type: 'Double', fallback: '0' }
    case 'int':
      return { type: 'Int', fallback: '0' }
    case 'boolean':
      return { type: 'Bool', fallback: 'false' }
    case 'array':
      return {
        type: `[${mapType(shape.element, context).type}]`,
        fallback: '[]',
      }
    case 'enum': {
      const [first] = context.enums.get(shape.name)
      return { type: shape.name, fallback: `.${escape(first)}` }
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

// --- emit --------------------------------------------------------------------

const out = []

out.push(`// Generated from src/tree.ts by scripts/gen-swift-types.mjs. Do not edit.
//
// Run \`npm run gen:swift-types\` after changing the descriptor types. The output is committed
// on purpose: \`pod install\` must not require Node, and a schema change should be visible in a
// diff. CI fails if this file is stale.
//
// Every field decodes leniently. \`JSONDecoder\` fails an entire payload on one unknown enum
// case or missing key, and \`expo-updates\` can ship a JS bundle newer than the native binary
// it runs against — so an unrecognised value has to degrade rather than blank the list.

import Foundation
`)

for (const [name, values] of enums) {
  out.push(
    `${renderDoc(`Generated from \`${name}\` in tree.ts.`, '')}enum ${name}: String, Decodable {`
  )
  for (const value of values) {
    // Backticks keep the case name identical to the JSON value, so no explicit rawValue is
    // needed even for Swift keywords like `default`.
    out.push(`  case ${escape(value)}`)
  }
  out.push(`  /// A value this binary does not recognise.`)
  out.push(`  case unknown

  init(from decoder: any Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = ${name}(rawValue: raw) ?? .unknown
  }
}
`)
}

for (const { name, doc, properties } of structs) {
  // `Equatable` so the host can tell whether the appearance actually changed. Supplementary views
  // are not covered by `reconfigureItems`, so section headers keep the previous theme unless the
  // sections are reloaded — and reloading on every tree update would be wasteful.
  out.push(`${renderDoc(doc, '')}struct ${name}: Decodable, Equatable {`)

  for (const property of properties) {
    const propertyDoc = renderDoc(property.doc, '  ').trimEnd()
    if (propertyDoc) out.push(propertyDoc)
    // Non-optional properties carry an inline default so the no-argument `init()` below
    // compiles; an optional `var` is implicitly nil already and needs nothing.
    out.push(
      property.optional
        ? `  var ${escape(property.name)}: ${property.type}?`
        : `  var ${escape(property.name)}: ${property.type} = ${property.fallback}`
    )
  }

  out.push(``)
  out.push(`  private enum CodingKeys: String, CodingKey {`)
  out.push(`    case ${properties.map((p) => escape(p.name)).join(', ')}`)
  out.push(`  }`)
  out.push(``)
  out.push(
    `  /// All defaults. Lets native render an empty list before any tree has arrived.`
  )
  out.push(`  init() {}`)
  out.push(``)
  out.push(`  init(from decoder: any Decoder) throws {`)
  out.push(
    `    let container = try decoder.container(keyedBy: CodingKeys.self)`
  )
  for (const property of properties) {
    const decode = `try container.decodeIfPresent(${property.type}.self, forKey: .${escape(property.name)})`
    out.push(
      property.optional
        ? `    ${escape(property.name)} = ${decode}`
        : `    ${escape(property.name)} = ${decode} ?? ${property.fallback}`
    )
  }
  out.push(`  }`)
  out.push(`}
`)
}

const rendered = out.join('\n')

// --- fixture + round-trip test ------------------------------------------------
//
// Both the fixture and the checks come from `tree-fixture.mjs`, shared with the Kotlin
// generator. All this file does is spell the checks as XCTest.

const ROOT = 'Tree'
const { fixture, checks } = synthesizeFixture({ structs, enums, root: ROOT })

/**
 * Renders one shared accessor path as Swift.
 *
 * Swift's optional chain unwraps once and the rest of the expression rides on it: after
 * `menuItems?[0]` the element is *not* optional, so `menuItems?[0]?.id` is rejected outright.
 * So `optional` here means "the value just produced still needs unwrapping", and indexing clears
 * it. Kotlin's safe call works the other way round, which is why this is not shared.
 */
function swiftPath(path) {
  let expression = 'decoded'
  let optional = false

  for (const step of resolvePath(structs, ROOT, path)) {
    if (step.kind === 'index') {
      expression += `${optional ? '?' : ''}[${step.index}]`
      optional = false
      continue
    }
    expression += `${optional ? '?.' : '.'}${step.kind === 'count' ? 'count' : escape(step.name)}`
    optional = step.kind === 'property' && step.optional
  }

  return expression
}

/** Spells one shared expectation as a Swift literal. */
function swiftLiteral(expect) {
  switch (expect.kind) {
    case 'string':
      return JSON.stringify(expect.value)
    case 'boolean':
      return 'true'
    case 'enum':
      return `.${escape(expect.value)}`
    default:
      return String(expect.value)
  }
}

const assertions = checks.map(
  ({ path, expect }) =>
    `XCTAssertEqual(${swiftPath(path)}, ${swiftLiteral(expect)})`
)

// --- forward-compatibility assertions -----------------------------------------
//
// Rendered from the shared path list in forward-compat-fixture.mjs. The Kotlin generator renders
// the same list against the same JSON file, which is the only way "both platforms decode a newer
// bundle identically" is a checked claim rather than an intention.

const forwardCompatAssertions = FORWARD_COMPAT_ASSERTIONS.map(
  ([description, path, expected]) => {
    const literal =
      typeof expected === 'object' && expected !== null && 'enum' in expected
        ? `.${escape(expected.value)}`
        : JSON.stringify(expected)
    return `XCTAssertEqual(${swiftPath(path)}, ${literal}, ${JSON.stringify(description)})`
  }
)

const testSource = `// Generated from src/tree.ts by scripts/gen-swift-types.mjs. Do not edit.
//
// Decodes the generated fixture and asserts that *every* field arrived. Each fixture value is
// deliberately different from the field's default, so a field the decoder skipped shows up as a
// failed assertion rather than as a plausible-looking zero.
//
// This is the contract test between src/tree.ts and ios/Generated/TreeTypes.swift. Run it with
// \`npm run test:swift\` — it needs no simulator, because the model depends only on Foundation.

import XCTest
@testable import RNGUICollectionViewModel

final class TreeTypesTests: XCTestCase {
  private func load(_ name: String) throws -> Data {
    let url = try XCTUnwrap(
      Bundle.module.url(forResource: name, withExtension: "json"),
      "\\(name).json missing from the test bundle"
    )
    return try Data(contentsOf: url)
  }

  /// Every field in the schema decodes to its fixture value.
  func testEveryFieldRoundTrips() throws {
    let decoded = try JSONDecoder().decode(Tree.self, from: try load("TreeTypesFixture"))

${assertions.map((line) => `    ${line}`).join('\n')}
  }

  /// A tree from a *newer* JS bundle than this binary still decodes to a usable list.
  ///
  /// The same fixture and the same assertions run on Android — see
  /// \`android/src/test/java/com/rngui/collectionview/generated/TreeTypesTest.kt\`. A decoder that
  /// is lenient on one platform and strict on the other is worse than one that is strict on both,
  /// because only one of the two phones goes blank.
  func testForwardCompatibleTreeDecodes() throws {
    let decoded = try JSONDecoder().decode(Tree.self, from: try load("ForwardCompatFixture"))

${forwardCompatAssertions.map((line) => `    ${line}`).join('\n')}
  }

  /// An unrecognised enum value degrades to \`.unknown\` instead of failing the whole payload.
  ///
  /// This is what keeps a JS bundle that is newer than the native binary — the normal state of
  /// affairs under \`expo-updates\` — from rendering an empty list.
  func testUnknownEnumDegradesInsteadOfThrowing() throws {
    let json = Data(#"{"sections":[{"id":"s","rows":[{"id":"r","kind":"not-a-real-kind"}]}]}"#.utf8)
    let decoded = try JSONDecoder().decode(Tree.self, from: json)
    XCTAssertEqual(decoded.sections.first?.rows.first?.kind, .unknown)
  }

  /// A missing key takes the field's default rather than throwing.
  func testMissingKeysTakeDefaults() throws {
    let decoded = try JSONDecoder().decode(Tree.self, from: Data(#"{}"#.utf8))
    XCTAssertEqual(decoded.sections.count, 0)
  }
}
`

const TESTS_DIR = path.join(here, '..', 'ios', 'Tests')
const artifacts = [
  [OUTPUT, `${rendered}\n`],
  [
    path.join(TESTS_DIR, 'TreeTypesFixture.json'),
    `${JSON.stringify(fixture, null, 2)}\n`,
  ],
  // Written here rather than by the Kotlin generator so everything under ios/Tests has one
  // author, even though both platforms read it.
  [
    path.join(TESTS_DIR, 'ForwardCompatFixture.json'),
    `${JSON.stringify(FORWARD_COMPAT_FIXTURE, null, 2)}\n`,
  ],
  [path.join(TESTS_DIR, 'TreeTypesTests.swift'), testSource],
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
  `${enums.size} enum(s), ${structs.length} struct(s), ${assertions.length} assertion(s)`
)

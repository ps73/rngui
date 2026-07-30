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

const here = path.dirname(fileURLToPath(import.meta.url))
const SOURCE = path.join(here, '..', 'src', 'tree.ts')
const OUTPUT = path.join(here, '..', 'ios', 'Generated', 'TreeTypes.swift')

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

const source = ts.createSourceFile(
  SOURCE,
  readFileSync(SOURCE, 'utf8'),
  ts.ScriptTarget.Latest,
  /* setParentNodes */ true
)

/** String-literal unions become Swift enums; interfaces become structs. */
const enums = new Map() // name -> string[] of raw values
const structs = [] // { name, doc, properties }

/** Pulls a JSDoc block out as plain text, when it is a simple comment. */
function docOf(node) {
  const jsDoc = node.jsDoc?.[0]
  if (jsDoc == null || typeof jsDoc.comment !== 'string') return undefined
  return jsDoc.comment.trim() || undefined
}

/** Renders a doc comment at the given indent, preserving the author's line breaks. */
function renderDoc(doc, indent) {
  if (doc == null) return ''
  const lines = doc.split('\n').map((line) => `${indent}/// ${line}`.trimEnd())
  return `${lines.join('\n')}\n`
}

/**
 * Maps a *written* type annotation to Swift, plus the default used when the key is absent.
 *
 * `fallback` is null for types that have no sensible zero value — a struct, for instance —
 * which is what forces such fields to be declared optional in `tree.ts`.
 */
function mapType(node) {
  if (node.kind === ts.SyntaxKind.StringKeyword)
    return { swift: 'String', fallback: '""' }
  if (node.kind === ts.SyntaxKind.NumberKeyword)
    return { swift: 'Double', fallback: '0' }
  if (node.kind === ts.SyntaxKind.BooleanKeyword)
    return { swift: 'Bool', fallback: 'false' }

  if (ts.isArrayTypeNode(node)) {
    const element = mapType(node.elementType)
    return { swift: `[${element.swift}]`, fallback: '[]' }
  }

  if (ts.isTypeReferenceNode(node)) {
    const name = node.typeName.getText(source)
    // The integer marker. As a resolved type this is `number`; as syntax it is a name.
    if (name === 'IntValue') return { swift: 'Int', fallback: '0' }
    if (enums.has(name)) {
      const [first] = enums.get(name)
      return { swift: name, fallback: `.${escape(first)}` }
    }
    // A struct. No zero value, so it must be optional or inside an array.
    return { swift: name, fallback: null }
  }

  throw new Error(
    `gen-swift-types: unsupported type '${node.getText(source)}' at ` +
      `${SOURCE}:${source.getLineAndCharacterOfPosition(node.getStart()).line + 1}. ` +
      'Add a mapping here rather than working around it in tree.ts.'
  )
}

// --- collect -----------------------------------------------------------------
// Enums first: a struct property can reference one, and mapType needs it registered.

for (const statement of source.statements) {
  if (!ts.isTypeAliasDeclaration(statement)) continue
  const { type } = statement
  if (!ts.isUnionTypeNode(type)) continue

  const values = type.types.map((member) => {
    if (ts.isLiteralTypeNode(member) && ts.isStringLiteral(member.literal)) {
      return member.literal.text
    }
    throw new Error(
      `gen-swift-types: '${statement.name.text}' is a union of something other than string ` +
        'literals, which has no Swift enum equivalent.'
    )
  })

  enums.set(statement.name.text, values)
}

for (const statement of source.statements) {
  if (!ts.isInterfaceDeclaration(statement)) continue

  const properties = statement.members
    .filter(ts.isPropertySignature)
    .map((member) => {
      const name = member.name.getText(source)
      const optional = member.questionToken != null
      const mapped = mapType(member.type)

      if (!optional && mapped.fallback == null) {
        throw new Error(
          `gen-swift-types: '${statement.name.text}.${name}' is a required ${mapped.swift}, ` +
            'which has no default to fall back to when the key is missing. Make it optional in ' +
            'tree.ts, or give it a fallback in this script.'
        )
      }

      return { name, optional, doc: docOf(member), ...mapped }
    })

  structs.push({ name: statement.name.text, doc: docOf(statement), properties })
}

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
        ? `  var ${escape(property.name)}: ${property.swift}?`
        : `  var ${escape(property.name)}: ${property.swift} = ${property.fallback}`
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
    const decode = `try container.decodeIfPresent(${property.swift}.self, forKey: .${escape(property.name)})`
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
// Generated from the same field list as the model, which is the entire point. A hand-written
// fixture protects against nothing: add a field to tree.ts and the fixture simply does not
// mention it, so the test keeps passing while the field silently never arrives. Deriving both
// from the schema means new fields are covered the moment they exist.
//
// Every synthesized value is chosen to *differ from the field's default*, so "decoded
// correctly" and "fell back because decoding missed it" are distinguishable. That is the whole
// assertion.

const structsByName = new Map(structs.map((s) => [s.name, s]))
const ROOT = 'Tree'
if (!structsByName.has(ROOT)) {
  throw new Error(
    `gen-swift-types: expected a '${ROOT}' interface in tree.ts as the root type.`
  )
}

let seed = 0
const assertions = []

/** Builds a JSON value for one property, and records the assertion that checks it. */
function synthProperty(property, jsonPath, swiftPath, visiting) {
  const swiftAccess = `${swiftPath}.${escape(property.name)}`

  if (property.swift === 'String') {
    const value = `${jsonPath}.${property.name}`
    assertions.push(`XCTAssertEqual(${swiftAccess}, ${JSON.stringify(value)})`)
    return value
  }
  if (property.swift === 'Bool') {
    // The default is `false`, so `true` is the only value that proves decoding happened.
    assertions.push(`XCTAssertEqual(${swiftAccess}, true)`)
    return true
  }
  if (property.swift === 'Int' || property.swift === 'Double') {
    const value = ++seed * 11
    assertions.push(`XCTAssertEqual(${swiftAccess}, ${value})`)
    return value
  }
  if (enums.has(property.swift)) {
    // The *last* case, never the first: the first is what a failed decode falls back to.
    const cases = enums.get(property.swift)
    const value = cases[cases.length - 1]
    assertions.push(`XCTAssertEqual(${swiftAccess}, .${escape(value)})`)
    return value
  }
  // Optional properties need `?` before any further member access, so nested assertions read
  // `decoded.appearance?.font?.design`.
  const chain = property.optional ? `${swiftAccess}?` : swiftAccess

  const arrayMatch = /^\[(.+)\]$/.exec(property.swift)
  if (arrayMatch) {
    const elementName = arrayMatch[1]
    const element = structsByName.get(elementName)
    if (element == null) {
      throw new Error(
        `gen-swift-types: no struct '${elementName}' for the fixture.`
      )
    }
    assertions.push(`XCTAssertEqual(${chain}.count, 1)`)
    return [
      synthStruct(
        element,
        `${jsonPath}.${property.name}[0]`,
        `${chain}[0]`,
        visiting
      ),
    ]
  }

  // A directly referenced struct — `appearance?: Appearance`. No assertion of its own; the
  // recursion asserts each of its fields, which is stricter than checking the struct exists.
  const struct = structsByName.get(property.swift)
  if (struct != null) {
    return synthStruct(struct, `${jsonPath}.${property.name}`, chain, visiting)
  }

  throw new Error(
    `gen-swift-types: cannot synthesize a fixture value for ${property.swift}.`
  )
}

function synthStruct(struct, jsonPath, swiftPath, visiting) {
  if (visiting.has(struct.name)) {
    throw new Error(
      `gen-swift-types: '${struct.name}' is recursive. The fixture generator cannot terminate, ` +
        'and neither can codegen — flatten it with parent indices instead.'
    )
  }
  const nested = new Set(visiting).add(struct.name)

  const object = {}
  for (const property of struct.properties) {
    object[property.name] = synthProperty(property, jsonPath, swiftPath, nested)
  }
  return object
}

const fixture = synthStruct(structsByName.get(ROOT), ROOT, 'decoded', new Set())

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
  private func loadFixture() throws -> Data {
    let url = try XCTUnwrap(
      Bundle.module.url(forResource: "TreeTypesFixture", withExtension: "json"),
      "TreeTypesFixture.json missing from the test bundle"
    )
    return try Data(contentsOf: url)
  }

  /// Every field in the schema decodes to its fixture value.
  func testEveryFieldRoundTrips() throws {
    let decoded = try JSONDecoder().decode(Tree.self, from: try loadFixture())

${assertions.map((line) => `    ${line}`).join('\n')}
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

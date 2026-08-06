/**
 * Reads `src/tree.ts` into a language-neutral schema, for the Swift and Kotlin generators.
 *
 * Both generators need exactly the same facts out of the descriptor tree — which string unions
 * are enums, which interfaces are structs, and what each property is written as — and differ only
 * in how they *spell* the result. Keeping the reading here means a type `tree.ts` starts using is
 * a change in one place rather than two, and a type neither generator understands fails both
 * builds instead of silently reaching only one platform.
 *
 * Reads the syntax tree only — never the type checker — so the mapping is decided by what is
 * *written* in the annotation. That is what makes the `IntValue` marker work: as a resolved type
 * it is just `number`, but as syntax it is a name a script can recognise.
 */
import ts from 'typescript'
import { readFileSync } from 'node:fs'

/**
 * The neutral description of a written type annotation.
 *
 * `int` and `number` are distinct here even though TypeScript has one numeric type, because the
 * distinction is the whole reason `IntValue` exists — and because a test asserting `99` against a
 * Kotlin `Double?` fails on a difference that is purely about spelling.
 *
 * @typedef {{ shape: 'string' | 'number' | 'int' | 'boolean' }
 *   | { shape: 'enum' | 'struct', name: string }
 *   | { shape: 'array', element: Shape }} Shape
 */

/**
 * @param {object} options
 * @param {string} options.sourcePath  Absolute path to `tree.ts`.
 * @param {string} options.tool        Generator name, for error messages.
 * @param {(shape: Shape, context: { enums: Map<string, string[]> }) => { type: string, fallback: string | null }} options.mapType
 *   Spells a shape in the target language, plus the value used when the key is absent.
 *   `fallback` is null for types that have no zero value — a struct, for instance — which is what
 *   forces such fields to be declared optional in `tree.ts`.
 */
export function readTreeSchema({ sourcePath, tool, mapType }) {
  const source = ts.createSourceFile(
    sourcePath,
    readFileSync(sourcePath, 'utf8'),
    ts.ScriptTarget.Latest,
    /* setParentNodes */ true
  )

  /** Pulls a JSDoc block out as plain text, when it is a simple comment. */
  const docOf = (node) => {
    const jsDoc = node.jsDoc?.[0]
    if (jsDoc == null || typeof jsDoc.comment !== 'string') return undefined
    return jsDoc.comment.trim() || undefined
  }

  const at = (node) =>
    `${sourcePath}:${source.getLineAndCharacterOfPosition(node.getStart()).line + 1}`

  // Enums first: a struct property can reference one, and the shape reader needs it registered.
  const enums = new Map() // name -> string[] of raw values

  /**
   * Unions of string literals **widened with `(string & {})`** — a plain string on the wire, and
   * a list of suggestions in the editor.
   *
   * `FontFamily` is the case this exists for. Its five reserved names (`ui-rounded` and friends)
   * are worth autocompleting, but the sixth value is "whatever face the app registered", so it
   * cannot be an enum in Swift or Kotlin without making every app font unrepresentable. The
   * widener is what tells TypeScript to keep offering the five without closing the type — and
   * what tells this reader that the answer over the boundary is `String`.
   *
   * Deliberately not a general escape hatch: a union with any *other* non-literal member still
   * throws below, because that is a type neither generator can spell.
   */
  const widenedStrings = new Set()

  /**
   * `string`, or the `(string & {})` idiom that widens a literal union without collapsing it.
   *
   * The parentheses are load-bearing in the source — `&` binds tighter than `|`, but writing it
   * unparenthesised is unreadable — so they are here too, as a `ParenthesizedTypeNode` wrapping
   * the intersection.
   */
  const isStringWidener = (node) => {
    const inner = ts.isParenthesizedTypeNode(node) ? node.type : node
    return (
      inner.kind === ts.SyntaxKind.StringKeyword ||
      (ts.isIntersectionTypeNode(inner) &&
        inner.types.some((part) => part.kind === ts.SyntaxKind.StringKeyword))
    )
  }

  for (const statement of source.statements) {
    if (!ts.isTypeAliasDeclaration(statement)) continue
    const { type } = statement
    if (!ts.isUnionTypeNode(type)) continue

    if (type.types.some(isStringWidener)) {
      widenedStrings.add(statement.name.text)
      continue
    }

    const values = type.types.map((member) => {
      if (ts.isLiteralTypeNode(member) && ts.isStringLiteral(member.literal)) {
        return member.literal.text
      }
      throw new Error(
        `${tool}: '${statement.name.text}' is a union of something other than string ` +
          'literals, which has no enum equivalent in the target language.'
      )
    })

    enums.set(statement.name.text, values)
  }

  const context = { enums, source, sourcePath, tool, at }

  /** @returns {Shape} */
  function shapeOf(node) {
    if (node.kind === ts.SyntaxKind.StringKeyword) return { shape: 'string' }
    if (node.kind === ts.SyntaxKind.NumberKeyword) return { shape: 'number' }
    if (node.kind === ts.SyntaxKind.BooleanKeyword) return { shape: 'boolean' }
    if (ts.isArrayTypeNode(node)) {
      return { shape: 'array', element: shapeOf(node.elementType) }
    }
    if (ts.isTypeReferenceNode(node)) {
      const name = node.typeName.getText(source)
      // The integer marker. As a resolved type this is `number`; as syntax it is a name.
      if (name === 'IntValue') return { shape: 'int' }
      if (enums.has(name)) return { shape: 'enum', name }
      if (widenedStrings.has(name)) return { shape: 'string' }
      return { shape: 'struct', name }
    }
    throw unsupportedType(node, context)
  }

  const structs = []

  for (const statement of source.statements) {
    if (!ts.isInterfaceDeclaration(statement)) continue

    const properties = statement.members
      .filter(ts.isPropertySignature)
      .map((member) => {
        const name = member.name.getText(source)
        const optional = member.questionToken != null
        const shape = shapeOf(member.type)
        const mapped = mapType(shape, context)

        if (!optional && mapped.fallback == null) {
          throw new Error(
            `${tool}: '${statement.name.text}.${name}' is a required ${mapped.type}, which has ` +
              'no default to fall back to when the key is missing. Make it optional in tree.ts, ' +
              'or give it a fallback in the generator.'
          )
        }

        return { name, optional, shape, doc: docOf(member), ...mapped }
      })

    structs.push({
      name: statement.name.text,
      doc: docOf(statement),
      properties,
    })
  }

  return { source, enums, structs, docOf, at }
}

/**
 * Walks a dotted accessor like `sections[0].rows[1].kind` against the schema.
 *
 * Returns one step per segment, each carrying whether the value it produces is optional — which
 * is the part a generator cannot guess and the part both target languages need in order to spell
 * the access at all. Resolving it here rather than in each generator is what lets the shared
 * fixture checks be written once, as data.
 *
 * How the `?` is then *placed* is emphatically not shared, because the two languages disagree:
 * Swift's optional chain unwraps once and the rest of the chain rides on it (`menuItems?[0].id`),
 * while every link of a Kotlin safe call has to be spelled (`menuItems?.get(0)?.id`).
 *
 * @param {{ name: string, properties: Array<object> }[]} structs
 * @param {string} rootName
 * @param {string} path
 * @returns {Array<{ kind: 'property', name: string, optional: boolean, shape: Shape }
 *   | { kind: 'index', index: number } | { kind: 'count' }>}
 */
export function resolvePath(structs, rootName, path) {
  const byName = new Map(structs.map((s) => [s.name, s]))

  const steps = []
  // Either `{ struct }` — the cursor sits on a value we can read a property off — or
  // `{ element }`, where it sits on an array and only `[n]` or `.count` are legal next.
  let cursor = { struct: rootName }

  for (const segment of path.split('.')) {
    const match = /^([A-Za-z0-9_]+)((?:\[\d+\])*)$/.exec(segment)
    if (match == null)
      throw new Error(`tree-schema: cannot parse path segment '${segment}'.`)
    const [, name, indices] = match

    if (name === 'count') {
      if (cursor.element == null) {
        throw new Error(
          `tree-schema: '.count' on a non-array in path '${path}'.`
        )
      }
      steps.push({ kind: 'count' })
      cursor = {}
      continue
    }

    if (cursor.struct == null) {
      throw new Error(
        `tree-schema: path '${path}' reads '${name}' off an array.`
      )
    }
    const struct = byName.get(cursor.struct)
    if (struct == null) {
      throw new Error(
        `tree-schema: no struct '${cursor.struct}' for path '${path}'.`
      )
    }
    const property = struct.properties.find((p) => p.name === name)
    if (property == null) {
      throw new Error(
        `tree-schema: '${cursor.struct}' has no property '${name}' (path '${path}').`
      )
    }

    steps.push({
      kind: 'property',
      name,
      optional: property.optional,
      shape: property.shape,
    })

    cursor =
      property.shape.shape === 'array'
        ? { element: property.shape.element }
        : { struct: property.shape.name }

    for (const index of indices.match(/\d+/g) ?? []) {
      if (cursor.element == null) {
        throw new Error(
          `tree-schema: '${struct.name}.${name}' is not an array (path '${path}').`
        )
      }
      steps.push({ kind: 'index', index: Number(index) })
      cursor =
        cursor.element.shape === 'array'
          ? { element: cursor.element.element }
          : { struct: cursor.element.name }
    }
  }

  return steps
}

/** Throws the "no mapping for this type" error both generators raise. */
export function unsupportedType(node, { source, tool, at }) {
  return new Error(
    `${tool}: unsupported type '${node.getText(source)}' at ${at(node)}. ` +
      'Add a mapping there rather than working around it in tree.ts.'
  )
}

/**
 * Synthesizes the round-trip fixture, and the checks every platform must make against it.
 *
 * A hand-written fixture protects against nothing: add a field to `tree.ts` and the fixture
 * simply does not mention it, so the test keeps passing while the field silently never arrives.
 * Deriving both the JSON and the assertions from the schema means new fields are covered the
 * moment they exist.
 *
 * Every synthesized value is chosen to *differ from the field's default*, so "decoded correctly"
 * and "fell back because decoding missed it" are distinguishable. That is the whole assertion.
 *
 * Shared between the Swift and Kotlin generators rather than reimplemented in each. Two
 * generators walking the same schema in "the same" order is exactly the kind of agreement that
 * holds until someone reorders a branch, and then one platform quietly stops checking a field.
 */

/**
 * @param {object} options
 * @param {Array<object>} options.structs  From `readTreeSchema`.
 * @param {Map<string, string[]>} options.enums
 * @param {string} options.root            The root interface name.
 * @returns {{ fixture: object, checks: Array<{ path: string, expect: object }> }}
 *   `path` is a dotted accessor into the decoded root, for `resolvePath`. `expect` is one of
 *   `{kind:'string'|'int'|'double', value}`, `{kind:'boolean'}`, `{kind:'enum', name, value}`
 *   or `{kind:'count', value}`.
 */
export function synthesizeFixture({ structs, enums, root }) {
  const byName = new Map(structs.map((s) => [s.name, s]))
  const rootStruct = byName.get(root)
  if (rootStruct == null) {
    throw new Error(
      `tree-fixture: expected a '${root}' interface in tree.ts as the root type.`
    )
  }

  let seed = 0
  const checks = []

  /** `sections[0].rows` → the path a *value* embeds, which reads better with the root on it. */
  const label = (path) => `${root}.${path}`

  /**
   * Builds a JSON value for one shape, recording the checks that prove it arrived.
   *
   * @param {object} shape
   * @param {string} path   Accessor path to this value, relative to the root.
   * @param {Set<string>} visiting  Structs on the current stack, for the recursion guard.
   */
  function synthShape(shape, path, visiting) {
    switch (shape.shape) {
      case 'string': {
        // The path itself, so a mismatched assertion says which field went astray.
        const value = label(path)
        checks.push({ path, expect: { kind: 'string', value } })
        return value
      }
      case 'boolean':
        // The default is `false`, so `true` is the only value that proves decoding happened.
        checks.push({ path, expect: { kind: 'boolean' } })
        return true
      case 'int':
      case 'double': {
        const value = ++seed * 11
        checks.push({ path, expect: { kind: shape.shape, value } })
        return value
      }
      case 'number': {
        const value = ++seed * 11
        checks.push({ path, expect: { kind: 'double', value } })
        return value
      }
      case 'enum': {
        // The *last* case, never the first: the first is what a failed decode falls back to.
        const cases = enums.get(shape.name)
        const value = cases[cases.length - 1]
        checks.push({ path, expect: { kind: 'enum', name: shape.name, value } })
        return value
      }
      case 'array': {
        // One element is enough. The assertion proves the array decoded and that its element
        // type is right, which is the only thing that can drift between the platforms.
        if (shape.element.shape === 'struct') {
          checks.push({
            path: `${path}.count`,
            expect: { kind: 'count', value: 1 },
          })
        }
        return [synthShape(shape.element, `${path}[0]`, visiting)]
      }
      case 'struct': {
        // No check of its own; the recursion asserts each of its fields, which is stricter than
        // checking that the struct exists.
        return synthStruct(byName.get(shape.name), path, visiting)
      }
      default:
        throw new Error(
          `tree-fixture: cannot synthesize a value for shape '${shape.shape}'.`
        )
    }
  }

  function synthStruct(struct, path, visiting) {
    if (struct == null)
      throw new Error(`tree-fixture: unknown struct at '${path}'.`)
    if (visiting.has(struct.name)) {
      throw new Error(
        `tree-fixture: '${struct.name}' is recursive. The fixture generator cannot terminate, ` +
          'and neither can codegen — flatten it with parent indices instead.'
      )
    }
    const nested = new Set(visiting).add(struct.name)

    const object = {}
    for (const property of struct.properties) {
      const child = path === '' ? property.name : `${path}.${property.name}`
      object[property.name] = synthShape(property.shape, child, nested)
    }
    return object
  }

  const fixture = synthStruct(rootStruct, '', new Set())
  return { fixture, checks }
}

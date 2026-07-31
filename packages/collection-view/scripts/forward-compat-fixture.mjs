/**
 * The forward-compatibility fixture: a tree written by a *newer* JS bundle than the native
 * binary decoding it.
 *
 * `tree.ts` rule 2 says this is a normal state of affairs rather than an error — `expo-updates`
 * ships JavaScript alone, so a phone can be running last month's binary against today's bundle.
 * Strict decoding turns that into "the list is empty, with no error", which is the worst possible
 * failure for a UI library.
 *
 * Hand-written on purpose, and the one fixture in this package that is. Everything else is
 * synthesized from the schema, which is right for "every field arrives" — but this fixture is
 * about fields and enum cases the schema deliberately does **not** contain, and a generator can
 * only emit what it knows.
 *
 * Both generators consume this: `gen-swift-types.mjs` writes the JSON and emits the XCTest
 * assertions, `gen-kotlin-types.mjs` emits the JUnit ones against the same file. That is the
 * point — a lenient decoder on one platform and a strict one on the other is exactly the drift
 * this package cannot afford.
 */

/** The unknown-key/unknown-enum payload, as it would arrive on the `tree` prop. */
export const FORWARD_COMPAT_FIXTURE = {
  sections: [
    {
      id: 'known-section',
      header: 'Still here',
      // A key no version of the schema has. Must be ignored, not fatal.
      futureSectionField: { nested: true },
      rows: [
        // An ordinary row sharing a section with a row this binary cannot render. It has to
        // survive: degrading one row into nothing is acceptable, losing its neighbours is not.
        { id: 'row-known', kind: 'value', label: 'Label', value: 'Value' },
        {
          id: 'row-future',
          kind: 'quantumSlider',
          label: 'From a newer bundle',
          quantumRange: [0, 1],
        },
      ],
    },
  ],
  // An unrecognised value for a *known* enum field.
  listAppearance: 'holographic',
  appearance: {
    labelColor: '#112233FF',
    futureAppearanceField: 'ignored',
    font: { weight: '620', futureAxis: 'grad' },
  },
  futureTopLevelField: 42,
}

/**
 * What every platform must observe after decoding it, as `[description, path, expected]`.
 *
 * Written as data rather than as per-language source so the two generators cannot assert
 * different things. `path` is a dotted accessor into the decoded tree; each generator renders it
 * in its own syntax, including its own optional-chaining rules.
 */
export const FORWARD_COMPAT_ASSERTIONS = [
  ['the known section survived', 'sections.count', 1],
  [
    'its header decoded past the unknown sibling key',
    'sections[0].header',
    'Still here',
  ],
  [
    'both rows survived, including the unrenderable one',
    'sections[0].rows.count',
    2,
  ],
  [
    'the known row is intact',
    'sections[0].rows[0].kind',
    { enum: 'RowKind', value: 'value' },
  ],
  ['and kept its fields', 'sections[0].rows[0].value', 'Value'],
  [
    'the future row kind degraded rather than throwing',
    'sections[0].rows[1].kind',
    { enum: 'RowKind', value: 'unknown' },
  ],
  [
    'and the rest of that row still decoded',
    'sections[0].rows[1].label',
    'From a newer bundle',
  ],
  [
    'an unrecognised value for a known enum degrades too',
    'listAppearance',
    { enum: 'ListAppearance', value: 'unknown' },
  ],
  [
    'appearance decoded past its unknown key',
    'appearance.labelColor',
    '#112233FF',
  ],
  ['and so did the font nested inside it', 'appearance.font.weight', '620'],
]

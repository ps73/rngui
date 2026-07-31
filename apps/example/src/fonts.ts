/**
 * The one bundled typeface, and the names the native side has to be given.
 *
 * **Inter Variable, not one of the nine static Inter weights.** A variable font is the only thing
 * that exercises `FontSpec.variations`: the named weights a static family ships are reachable
 * through `weight`, but `wght=550` is not — there is no such file. Inter carries two axes:
 *
 * | axis   | range     | default |
 * | ------ | --------- | ------- |
 * | `wght` | 100 – 900 | 400     |
 * | `opsz` | 14 – 32   | 14      |
 *
 * `opsz` is the optical-size axis: at display sizes it tightens the spacing and thins the strokes,
 * which is a thing no static family can do at all.
 */
export const INTER = {
  // The key is what React Native's own `fontFamily` uses. Registration is what matters for this
  // library — `expo-font` hands the face to Core Text, which registers it under the names baked
  // into the file, and those are what `UIFont(name:)` will answer to.
  Inter: require('../assets/fonts/InterVariable.ttf'),
}

/**
 * The name to pass as `FontSpec.family`.
 *
 * The file's family name, which is also what its PostScript name (`Inter-Regular`) resolves to.
 * `FontResolver` tries `UIFont(name:)` first and a family descriptor second, so either spelling
 * works here — but the family name is the one that keeps meaning the whole family once more
 * instances exist.
 */
export const INTER_FAMILY = 'Inter'

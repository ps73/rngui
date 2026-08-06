/**
 * `npm run verify`, against a fresh clone of HEAD in a temporary directory.
 *
 * Everything `verify` checks, it checks in *this* checkout — with the `node_modules` that has been
 * grown incrementally for months, and with `apps/example/android` as `expo prebuild` last left it.
 * Both of those hide a specific class of failure: a dependency that is installed here and missing
 * from the lockfile, generated Kotlin that only compiles against a stale prebuild, a file that is
 * committed with the wrong case. The Verify workflow catches those because a runner starts from
 * nothing, and this is the same idea without the minutes.
 *
 * What it does not reproduce is Linux. macOS is case-insensitive by default and a GitHub runner is
 * not, so an import that differs only in case still needs the hosted job to catch it. That is the
 * one thing left for CI before a release, and it is worth saying rather than implying this is a
 * replacement.
 *
 * **Only committed work is tested.** The clone is of `HEAD`, so uncommitted changes are not in it;
 * the run warns rather than refusing, because "does the commit I am about to push stand up on its
 * own" is exactly the question being asked.
 *
 *   npm run verify:clean            # clone, run everything, delete the clone
 *   npm run verify:clean -- --keep  # leave the clone in place to poke at
 */
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const keep = process.argv.includes('--keep')

const git = (...args) =>
  execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8' }).trim()

const head = git('rev-parse', 'HEAD')
const branch = git('rev-parse', '--abbrev-ref', 'HEAD')
const dirty = git('status', '--porcelain')

if (dirty) {
  console.warn(
    `\n⚠️  ${dirty.split('\n').length} uncommitted change(s) in this checkout.\n` +
      '   The clone is of HEAD, so none of them are being tested.\n'
  )
}

/**
 * Present but unusable counts as absent.
 *
 * `swift` needs macOS, and the Kotlin tests need an Android SDK. Rather than fail on a machine
 * that was never going to have one, each step that cannot run is skipped and named in the summary
 * — the same bargain `run-kotlin-tests.mjs` makes, for the same reason.
 */
const hasSwift =
  spawnSync('swift', ['--version'], { stdio: 'ignore' }).status === 0
const hasAndroidSdk = Boolean(
  process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT
)

const clone = mkdtempSync(path.join(tmpdir(), 'rngui-verify-'))

console.log(`\nCloning ${branch} @ ${head.slice(0, 7)} → ${clone}\n`)
// `--no-hardlinks` because the clone is disposable and the object store here is not: a local
// clone hardlinks objects by default, and nothing in this script should be able to reach them.
execFileSync('git', ['clone', '--no-hardlinks', '--quiet', repoRoot, clone], {
  stdio: 'inherit',
})
execFileSync('git', ['checkout', '--quiet', head], {
  cwd: clone,
  stdio: 'inherit',
})

/** @type {Array<{ name: string, argv: string[], skip?: string }>} */
const steps = [
  { name: 'npm ci', argv: ['ci'] },
  { name: 'lint', argv: ['run', 'lint'] },
  { name: 'typecheck', argv: ['run', 'typecheck'] },
  {
    name: 'swift model up to date',
    argv: ['run', 'verify:swift-types', '--workspaces', '--if-present'],
  },
  {
    name: 'kotlin model up to date',
    argv: ['run', 'verify:kotlin-types', '--workspaces', '--if-present'],
  },
  {
    name: 'symbol table up to date',
    argv: ['run', 'verify:material-symbols', '--workspaces', '--if-present'],
  },
  {
    name: 'swift test',
    argv: ['run', 'test', '-w', '@rngui/collection-view'],
    skip: hasSwift ? undefined : 'no swift toolchain on this machine',
  },
  {
    name: 'expo prebuild (android)',
    argv: [
      'run',
      'prebuild',
      '-w',
      '@rngui/example',
      '--',
      '--platform',
      'android',
    ],
    skip: hasAndroidSdk ? undefined : 'no ANDROID_HOME',
  },
  {
    name: 'kotlin tests',
    argv: [
      'run',
      'test:kotlin',
      '-w',
      '@rngui/collection-view',
      '--',
      '--require',
    ],
    skip: hasAndroidSdk ? undefined : 'no ANDROID_HOME',
  },
]

const results = []
let failed = null

for (const step of steps) {
  if (step.skip) {
    console.log(`\n── ${step.name}: skipped (${step.skip})`)
    results.push({ name: step.name, state: `skipped — ${step.skip}` })
    continue
  }

  console.log(`\n── ${step.name}`)
  const started = process.hrtime.bigint()
  const { status } = spawnSync('npm', step.argv, {
    cwd: clone,
    stdio: 'inherit',
  })
  const seconds = Number(process.hrtime.bigint() - started) / 1e9

  if (status !== 0) {
    results.push({
      name: step.name,
      state: `FAILED after ${seconds.toFixed(0)}s`,
    })
    failed = step.name
    break
  }
  results.push({ name: step.name, state: `ok in ${seconds.toFixed(0)}s` })
}

console.log(`\n${'─'.repeat(60)}`)
for (const { name, state } of results) {
  console.log(`  ${name.padEnd(28)} ${state}`)
}

// Kept on failure whatever the flag says: the clone is the only copy of the state that failed,
// and deleting it means running the whole thing again to look at it.
if (keep || failed) {
  console.log(`\n  clone kept at ${clone}`)
} else {
  rmSync(clone, { recursive: true, force: true })
}

if (failed) {
  console.error(
    `\n${failed} failed against a clean checkout of ${head.slice(0, 7)}.\n`
  )
  process.exit(1)
}

const skipped = results.filter((r) => r.state.startsWith('skipped'))
console.log(
  `\n  ${head.slice(0, 7)} verifies from a clean checkout` +
    (skipped.length ? `, with ${skipped.length} step(s) skipped above.` : '.') +
    '\n  Linux is still only covered by the hosted workflow.\n'
)

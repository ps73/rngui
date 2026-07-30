import { BaselineList } from '../../../src/components/BaselineList'

/**
 * Target: the Apple Health "Mental Wellbeing" screen.
 *
 * The custom-content showcase: large cards built from the native content DSL (so they
 * still recycle) alongside a `CollectionView.Host` row holding a real chart, over a
 * tinted gradient background.
 */
export default function HealthScreen() {
  return <BaselineList rows={20} />
}

import { Redirect } from 'expo-router'

/**
 * Entry route.
 *
 * Every tab in `(tabs)` is a named directory holding its own native stack, so the group
 * has no `index` route of its own and `/` would otherwise match nothing — which renders as
 * a blank screen with no error, since an unmatched route is not a failure to expo-router.
 * Redirecting is more legible than naming one of the tab directories `index` purely to
 * satisfy the router.
 */
export default function Index() {
  return <Redirect href="/settings" />
}

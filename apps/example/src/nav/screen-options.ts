import { Platform } from 'react-native'

/**
 * The header style every tab's stack uses, per platform.
 *
 * **The two are deliberately different, and that is the same argument the library makes about
 * everything else.** A transparent large-title header over a soft scroll edge is an iOS
 * treatment — there is no large title on Android, and no scroll edge effect — so shipping it on
 * both would not be parity, it would be one platform wearing the other's clothes.
 *
 * Deliberately untyped rather than annotated with `NativeStackNavigationOptions`: that type lives
 * in `@react-navigation/native-stack`, which is expo-router's transitive dependency and not ours
 * to pin. `as const` gets the string literals narrowed (a widened `string` would not satisfy
 * `scrollEdgeEffects`) and the object is structurally checked at each `<Stack screenOptions={...}>`
 * use site, which is where an error would matter.
 */

/**
 * iOS: a transparent large-title header, which is the hardest case for a custom scroll view.
 *
 * That is exactly why it is the default here. UIKit has to find the screen's scroll view both to
 * collapse the title and to apply the edge effect to the content, so if `<CollectionView.Root>` is
 * ever not the first descendant of the screen, both silently stop working — and every screen in
 * this app is a live test of that.
 */
const iosOptions = {
  headerLargeTitle: true,
  headerTransparent: true,
  headerShadowVisible: false,
  headerLargeTitleShadowVisible: false,

  /**
   * iOS 26's scroll edge effect, rather than a blur behind the bar.
   *
   * The system apps stopped using `headerBlurEffect` here: a soft edge fades the *content* out as
   * it passes under the navigation bar instead of putting a frosted pane over it, and the two are
   * visibly different — the blur has an edge, the soft effect does not. react-native-screens warns
   * that setting both overlaps them, so `headerBlurEffect` is gone rather than merely overridden.
   *
   * This applies to "the ScrollView in the first descendants chain of the Screen", which is the
   * same `subviews[0]` walk the large title uses — so it is one more thing that silently stops
   * working if `<CollectionView.Root>` ever loses that position.
   */
  scrollEdgeEffects: {
    top: 'soft',
    bottom: 'automatic',
    left: 'automatic',
    right: 'automatic',
  },

  /**
   * Required, not cosmetic.
   *
   * react-native-screens builds the navigation bar's `scrollEdgeAppearance` by *copying* the
   * standard appearance and only calls `configureWithTransparentBackground` when the
   * large-title background colour is fully transparent. Leave this unset and whatever the
   * standard appearance paints ends up over the expanded large title, so scrolling down and
   * back up leaves the title sitting behind it rather than in front.
   */
  headerLargeStyle: { backgroundColor: 'transparent' },
} as const

/**
 * Android: an ordinary opaque toolbar, which is what the platform actually has.
 *
 * **`headerTransparent` is the whole point of this split.** It tells react-native-screens not to
 * reserve space for the header, on the promise that the content will inset itself for it — which
 * on iOS `contentInsetAdjustmentBehavior: automatic` does, because UIKit knows the navigation bar's
 * height. Android has no such thing: the toolbar's height is not a window inset, so `automatic`
 * folds in the status bar and nothing else, and the first rows end up sliding under the title.
 *
 * The library could have guessed at it by walking up the view hierarchy looking for a Toolbar, and
 * that would work until the first app that does not use react-native-screens. An opaque header is
 * what an Android app has anyway.
 *
 * `headerShadowVisible` stays off because the list supplies its own separation — a grouped list
 * already reads as sitting on a tinted background, and a toolbar hairline over it is a second
 * border doing the same job.
 */
const androidOptions = {
  headerShadowVisible: false,
} as const

export const stackScreenOptions = Platform.select({
  ios: iosOptions,
  default: androidOptions,
})

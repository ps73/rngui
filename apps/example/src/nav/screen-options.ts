/**
 * The header style every tab's stack uses.
 *
 * A transparent large-title header over a soft scroll edge is the hardest case for a
 * custom scroll view, which is exactly why it is the default here: UIKit has to find the
 * screen's scroll view both to collapse the title and to apply the edge effect to the
 * content. If `<CollectionView.Root>` is ever not the first descendant of the screen, both
 * silently stop working — so every screen in this app is a live test of that.
 *
 * `headerShadowVisible: false` because the edge effect already separates the header from
 * the content; a hairline on top of it reads as a double border.
 *
 * Deliberately untyped rather than annotated with `NativeStackNavigationOptions`: that
 * type lives in `@react-navigation/native-stack`, which is expo-router's transitive
 * dependency and not ours to pin. `as const` gets the string literals narrowed (a widened
 * `string` would not satisfy `scrollEdgeEffects`) and the object is structurally checked at
 * each `<Stack screenOptions={...}>` use site, which is where an error would matter.
 */
export const stackScreenOptions = {
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

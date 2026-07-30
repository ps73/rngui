/**
 * The header style every tab's stack uses.
 *
 * A transparent, blurred, large-title header is the hardest case for a custom scroll
 * view, which is exactly why it is the default here: UIKit has to find the screen's
 * scroll view to collapse the title and to fade the blur in at the scroll edge. If
 * `<CollectionView.Root>` is ever not the first descendant of the screen, this header
 * silently stops animating — so every screen in this app is a live test of that.
 *
 * `headerShadowVisible: false` because the blur already separates the header from the
 * content; the hairline on top of it reads as a double border.
 *
 * Deliberately untyped rather than annotated with `NativeStackNavigationOptions`: that
 * type lives in `@react-navigation/native-stack`, which is expo-router's transitive
 * dependency and not ours to pin. `as const` gets the string literals narrowed (a widened
 * `string` would not satisfy `headerBlurEffect`) and the object is structurally checked at
 * each `<Stack screenOptions={...}>` use site, which is where an error would matter.
 */
export const stackScreenOptions = {
  headerLargeTitle: true,
  headerTransparent: true,
  headerBlurEffect: 'systemChromeMaterial',
  headerShadowVisible: false,
  headerLargeTitleShadowVisible: false,

  /**
   * Required, not cosmetic.
   *
   * react-native-screens builds the navigation bar's `scrollEdgeAppearance` by *copying* the
   * standard appearance — blur included — and only calls `configureWithTransparentBackground`
   * when the large-title background colour is fully transparent. Leave this unset and the
   * blur is painted over the expanded large title, so scrolling down and back up leaves the
   * title sitting behind the blur instead of in front of it.
   */
  headerLargeStyle: { backgroundColor: 'transparent' },
} as const

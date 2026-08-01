package com.rngui.collectionview

import android.content.res.Configuration
import androidx.annotation.ColorInt
import com.rngui.collectionview.generated.Appearance
import com.rngui.collectionview.generated.FontSpec

/** What the `colorScheme` prop pins the list to. */
enum class ColorScheme {
  system,
  light,
  dark;

  companion object {
    fun from(raw: String?): ColorScheme =
      when (raw) {
        "light" -> light
        "dark" -> dark
        else -> system
      }
  }
}

/**
 * Resolves the light/dark appearance pair against the mode in force.
 *
 * **This is where Android and iOS genuinely differ, rather than differing in spelling.** The Swift
 * `AppearanceResolver` hands back dynamic `UIColor`s and lets UIKit re-resolve them when the trait
 * collection changes, so a theme flip restyles every cell with no reconfiguration and no round trip
 * to JavaScript. Android has no dynamic colour: a resolved colour is an `Int`, and an `Int` does not
 * know what mode it was resolved in.
 *
 * So the same guarantee — *a theme flip costs no JS render* — is bought differently here. The
 * resolver is rebuilt and the visible rows rebound from `onConfigurationChanged`: strictly more work
 * than UIKit does, all of it inside this view. The observable behaviour is the same, which is the
 * part the guarantee was ever about.
 *
 * A field left unset falls through to the platform's own colour, so partial theming stays correct in
 * both modes for free.
 */
class AppearanceResolver(
  /**
   * Whether to resolve the dark side. Passed in rather than read off a `Context`, and that is a bug
   * fixed rather than a preference.
   *
   * The obvious implementation reads `android:textColorPrimary` off the view's context, whose theme
   * is `Theme.AppCompat.DayNight` in every React Native app — so it looks like it must follow the
   * mode. It does not: `ThemedReactContext` delegates `getTheme()` to the *application* context, and
   * a `Resources.Theme` obtained there does not re-resolve when the configuration changes. Flipping
   * to dark left every label black on a black list, which reads as "the appearance prop is broken"
   * rather than as a context bug. See [isDark] for what replaced it.
   */
  val isDark: Boolean,
  private val light: Appearance?,
  private val dark: Appearance?,
) {
  /**
   * The appearance in force, field by field.
   *
   * `dark` falls back to `light` per field, never the other way round: setting only `appearance`
   * should give that look in both modes, which is the least surprising behaviour and means adding a
   * dark override is always additive. Exactly the Swift resolver's rule.
   */
  private fun <T> pick(get: (Appearance) -> T?): T? {
    if (isDark) dark?.let(get)?.let { return it }
    return light?.let(get)
  }

  /** A themed colour, or the platform's own when neither side sets it. */
  @ColorInt
  fun color(get: (Appearance) -> String?, @ColorInt fallback: Int): Int =
    optionalColor(get) ?: fallback

  /**
   * `null` when neither side themes this field, which is not the same as "the default colour".
   *
   * The distinction survives the port for the Android version of the iOS reason: a row whose
   * background is left alone keeps its `RippleDrawable` and therefore its press feedback, while
   * assigning a concrete colour — even the identical one — replaces the drawable and the row stops
   * responding to touch. Unthemed has to mean untouched.
   */
  @ColorInt
  fun optionalColor(get: (Appearance) -> String?): Int? = parseRnguiHex(pick(get))

  /** Non-colour values. No dynamic form exists for these on either platform. */
  fun font(get: (Appearance) -> FontSpec?): FontSpec? = pick(get)

  fun dimension(get: (Appearance) -> Double?): Double? = pick(get)

  companion object {
    /**
     * The Android answer to `overrideUserInterfaceStyle`: `colorScheme` wins over the device.
     *
     * Deliberately *not* a `ContextThemeWrapper` carrying an overridden `uiMode`. That is the shape
     * the plan proposed, and it is the right shape once there is a Material drawable to theme — M4's
     * ripples and M7's controls will want one. It buys nothing today, because nothing M3 draws reads
     * a theme attribute, and infrastructure with no consumer is infrastructure nobody notices has
     * stopped working.
     *
     * The `Configuration` passed in is the one the view was handed, which is authoritative in a way
     * a context's theme is not.
     */
    fun isDark(configuration: Configuration, scheme: ColorScheme): Boolean =
      when (scheme) {
        ColorScheme.light -> false
        ColorScheme.dark -> true
        ColorScheme.system ->
          (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
      }
  }
}

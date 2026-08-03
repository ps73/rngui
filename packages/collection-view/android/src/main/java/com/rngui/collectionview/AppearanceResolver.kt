package com.rngui.collectionview

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
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
 * Resolves the light/dark appearance pair against the mode in force, over Material 3 tokens.
 *
 * **The defaults are M3 colour roles, not iOS colours transliterated.** An unthemed list draws with
 * `surface`, `surfaceContainer`, `onSurface`, `onSurfaceVariant`, `outlineVariant` and `primary` —
 * which means it follows the consuming app's Material theme, including dynamic colour on Android 12+,
 * rather than looking like an iOS list that happens to run here. Anything the caller sets in
 * `appearance` still wins; the tokens are what "unset" means.
 *
 * **This is where Android and iOS genuinely differ, rather than differing in spelling.** The Swift
 * resolver hands back dynamic `UIColor`s and lets UIKit re-resolve them on a trait change, with no
 * cell reconfigured. Android has no dynamic colour — a resolved colour is an `Int`, and an `Int`
 * does not know what mode produced it — so the same guarantee is bought by rebinding instead.
 *
 * `dark` falls back to `light` per field, never the other way round: setting only `appearance`
 * gives that look in both modes, so adding a dark override is always additive.
 */
class AppearanceResolver(
  /**
   * Already wrapped for the mode in force — see [themedContext]. Every token below resolves
   * against *this* context's theme, which is what makes `colorScheme` work at all.
   */
  val context: Context,
  val isDark: Boolean,
  private val light: Appearance?,
  private val dark: Appearance?,
) {
  private fun <T> pick(get: (Appearance) -> T?): T? {
    if (isDark) dark?.let(get)?.let { return it }
    return light?.let(get)
  }

  /** A themed colour, or the M3 role behind it when neither side sets one. */
  @ColorInt
  fun color(get: (Appearance) -> String?, role: String, @ColorInt fallback: Int): Int =
    optionalColor(get) ?: token(role, fallback)

  /**
   * `null` when neither side themes this field, which is not the same as "the default colour".
   *
   * A row whose background is left alone keeps its `RippleDrawable` and therefore its press
   * feedback; assigning a concrete colour — even the identical one — replaces the drawable and the
   * row stops responding to touch. Unthemed has to mean untouched.
   */
  @ColorInt
  fun optionalColor(get: (Appearance) -> String?): Int? = parseRnguiHex(pick(get))

  /**
   * An M3 colour role off the themed context, looked up by *name*.
   *
   * **By name rather than through `com.google.android.material.R.attr`, and that is not a
   * stylistic choice.** A library's R fields are non-final and rewritten when the app is
   * assembled; with non-transitive R classes the app's regenerated `material.R$attr` need not
   * carry every field this library compiled against, and the result is a runtime
   * `NoSuchFieldError: No field colorPrimary` on the first row it draws. Declaring our own `<attr>`
   * references instead fails the resource merge outright — the names are already defined.
   *
   * Resolving by name asks the merged resource table the same question, at runtime, where the
   * answer is always right. Cached per name, so it costs one lookup per role per process.
   */
  @ColorInt
  fun token(role: String, @ColorInt fallback: Int): Int {
    val attr = attrId(context, role)
    if (attr == 0) return fallback
    return MaterialColors.getColor(context, attr, fallback)
  }

  /** Non-colour values. No dynamic form exists for these on either platform. */
  fun font(get: (Appearance) -> FontSpec?): FontSpec? = pick(get)

  fun dimension(get: (Appearance) -> Double?): Double? = pick(get)

  companion object {
    private val attrIds = HashMap<String, Int>()

    @AttrRes
    private fun attrId(context: Context, name: String): Int =
      attrIds.getOrPut(name) {
        context.resources.getIdentifier(name, "attr", context.packageName)
      }

    // The M3 colour roles this library draws with. Names rather than ids; see `token`.
    const val COLOR_PRIMARY = "colorPrimary"
    const val COLOR_ON_SURFACE = "colorOnSurface"
    const val COLOR_ON_SURFACE_VARIANT = "colorOnSurfaceVariant"
    const val COLOR_OUTLINE_VARIANT = "colorOutlineVariant"
    const val COLOR_SECONDARY_CONTAINER = "colorSecondaryContainer"
    const val COLOR_ON_SECONDARY_CONTAINER = "colorOnSecondaryContainer"
    const val COLOR_SURFACE = "colorSurface"
    const val COLOR_SURFACE_CONTAINER = "colorSurfaceContainer"

    fun isDark(configuration: Configuration, scheme: ColorScheme): Boolean =
      when (scheme) {
        ColorScheme.light -> false
        ColorScheme.dark -> true
        ColorScheme.system ->
          (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
      }

    /**
     * A context carrying a Material 3 theme and the mode this list is resolving against.
     *
     * **Both halves are load-bearing, and neither is optional any more.**
     *
     * The *theme* is what `MaterialSwitch`, `MaterialCheckBox` and `MaterialRadioButton` require —
     * they read `colorPrimary`, `colorOnSurface` and a dozen shape attributes at inflation time and
     * throw without them — and it is where every M3 token this library draws with comes from.
     * Supplying it here rather than requiring one of the consuming app is the difference between a
     * library that works in any React Native project and one that comes with setup instructions.
     *
     * The *configuration override* is the Android answer to `overrideUserInterfaceStyle`. A theme
     * resolves `colorSurface` against its context's configuration, so overriding `uiMode` on the
     * wrapper makes every token below it resolve as though the device were in that mode — which is
     * exactly what `colorScheme` promises.
     *
     * `applyOverrideConfiguration` has to happen before anything reads a resource from the wrapper,
     * which is why the theme is set *after* it rather than in the constructor.
     */
    fun themedContext(base: Context, isDark: Boolean): Context {
      val configuration =
        Configuration(base.resources.configuration).apply {
          uiMode =
            (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
              if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }

      return ContextThemeWrapper(base, 0).apply {
        applyOverrideConfiguration(configuration)
        setTheme(MaterialR.style.Theme_Material3_DayNight_NoActionBar)
      }
    }
  }
}

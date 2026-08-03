package com.rngui.collectionview

import android.animation.TimeInterpolator
import android.content.Context
import android.util.TypedValue
import android.view.animation.AnimationUtils
import android.view.animation.PathInterpolator

/**
 * The Material 3 motion tokens this library animates with.
 *
 * **Read off the consuming app's theme, for the same reason the colours are.** An app that has
 * retuned its motion system — slower for a TV-sized surface, snappier for a utility app — has said
 * how it wants state changes to feel, and a list that ignored that would animate at its own tempo
 * while every other component on the screen moved at the app's. The literals below are the M3
 * baseline, used when nothing supplies a token.
 *
 * Resolved by *name* rather than through `com.google.android.material.R.attr`, and by hand rather
 * than through `MotionUtils`: the first is the `NoSuchFieldError` documented on
 * [AppearanceResolver.token], and the second keeps this off an API that Material marks as internal
 * and has changed shape across releases. What is left is fifteen lines of `TypedValue`.
 */
object Motion {
  /**
   * `md.sys.motion.duration.medium1` — 250ms.
   *
   * The M3 band for a transition *within* a component, as against one between screens. Long enough
   * that the shape change reads as the container growing rather than as a redraw, short enough that
   * a run of taps never queues up behind itself.
   */
  private const val DURATION_TOKEN = "motionDurationMedium1"

  private const val DEFAULT_DURATION_MS = 250

  /** `md.sys.motion.easing.standard`. */
  private const val EASING_TOKEN = "motionEasingStandardInterpolator"

  /** `cubic-bezier(0.2, 0, 0, 1)` — the curve the token above resolves to in an M3 theme. */
  private val STANDARD: TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

  /** How long a component takes to move between two of its own states. */
  fun stateChangeDuration(context: Context): Long {
    val value = resolve(context, DURATION_TOKEN) ?: return DEFAULT_DURATION_MS.toLong()
    if (value.type != TypedValue.TYPE_INT_DEC) return DEFAULT_DURATION_MS.toLong()
    return value.data.toLong()
  }

  /** The curve it moves along. */
  fun stateChangeEasing(context: Context): TimeInterpolator {
    val value = resolve(context, EASING_TOKEN) ?: return STANDARD
    // An M3 theme points this at an XML `pathInterpolator`; older themes spell the same curve as a
    // `cubic-bezier(...)` string, which resolves to the identical shape as the fallback below and so
    // is not worth a parser.
    if (value.type != TypedValue.TYPE_REFERENCE || value.resourceId == 0) return STANDARD
    return runCatching { AnimationUtils.loadInterpolator(context, value.resourceId) }
      .getOrDefault(STANDARD)
  }

  private fun resolve(context: Context, name: String): TypedValue? {
    val attr = AppearanceResolver.attrId(context, name)
    if (attr == 0) return null
    val value = TypedValue()
    return if (context.theme.resolveAttribute(attr, value, true)) value else null
  }
}

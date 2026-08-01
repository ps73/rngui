package com.rngui.collectionview

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

/** How the surrounding chrome folds into the content inset. Mirrors `UIScrollView`'s enum. */
enum class InsetAdjustment {
  automatic,
  scrollableAxes,
  never,
  always;

  companion object {
    fun from(raw: String?): InsetAdjustment =
      when (raw) {
        "never" -> never
        "always" -> always
        "scrollableAxes" -> scrollableAxes
        else -> automatic
      }
  }
}

/** What `keyboardDismissMode` can be, once `interactive` has been folded into `onDrag`. */
enum class KeyboardDismissMode {
  none,
  onDrag;

  companion object {
    /**
     * `interactive` maps to `onDrag`, and this is a documented degradation rather than a shrug.
     *
     * iOS's interactive mode lets the keyboard follow your finger down and come back if you
     * reverse. Android has no equivalent — `WindowInsetsAnimationControllerCompat` can drive the
     * IME by hand, but wiring a scroll gesture into it means owning the whole dismissal animation
     * and fighting the list for the same drag. Dismissing on drag is the platform's own behaviour
     * and the honest approximation.
     */
    fun from(raw: String?): KeyboardDismissMode =
      when (raw) {
        "none" -> none
        else -> onDrag
      }
  }
}

/**
 * Content insets, system-bar insets and the keyboard.
 *
 * All three end up in the same place — the list's padding, with `clipToPadding = false` so rows
 * scroll *through* the inset rather than being clipped at it, which is what makes a list look
 * like it passes under a translucent bar rather than stopping at one.
 *
 * `contentInset*` is kept separate from the resolved padding on purpose, and the keyboard path is
 * why: it computes `max(keyboardOverlap, contentInsetBottom)` and must never shrink the list below
 * what the caller asked for. A list with a bottom inset for a floating button keeps that room when
 * the keyboard appears.
 *
 * **`contentInsetAdjustmentBehavior` maps better here than it looks.** Android 15 forces
 * edge-to-edge, so "the list insets itself for the chrome above and below it" is now the
 * platform's own model too, not an iOS import — `automatic` applies the system-bar insets and
 * `never` applies none.
 */
class InsetController(
  private val root: View,
  private val list: RecyclerView,
  private val onInsetsChanged: () -> Unit,
) {
  var top: Int = 0
  var left: Int = 0
  var bottom: Int = 0
  var right: Int = 0

  var adjustment: InsetAdjustment = InsetAdjustment.automatic
  var adjustsForKeyboard: Boolean = false

  private var systemBars = Insets.NONE
  private var imeBottom = 0

  /** The resolved padding, in pixels, for the scroll event's `contentInset`. */
  var resolvedTop = 0
    private set

  var resolvedBottom = 0
    private set

  fun attach() {
    root.addOnAttachStateChangeListener(
      object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = apply()

        override fun onViewDetachedFromWindow(view: View) = Unit
      }
    )

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      // Read here as well as in the animation callback, so a keyboard that appears without an
      // animation — a hardware keyboard, or an accessibility setting that disables them — still
      // insets the list.
      imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      apply()
      windowInsets
    }

    /**
     * Moves the list *in step with* the keyboard rather than snapping when it has finished.
     *
     * The Android equivalent of reading duration and curve off the iOS keyboard notification: the
     * callback fires once per frame of the IME's own animation, so the list and the keyboard share
     * a single timeline instead of running two that happen to end together.
     */
    ViewCompat.setWindowInsetsAnimationCallback(
      root,
      object :
        WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
        override fun onProgress(
          insets: WindowInsetsCompat,
          animations: MutableList<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat {
          imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
          apply()
          return insets
        }
      },
    )
  }

  fun apply() {
    // Pulled as well as pushed. `setOnApplyWindowInsetsListener` only fires if every ancestor
    // dispatches, and this view sits deep inside React Native's tree where nothing guarantees
    // that — a listener that never fires leaves the list drawn under the status bar with no error
    // anywhere. Reading the root window insets directly is authoritative and costs one call.
    if (systemBars == Insets.NONE) {
      ViewCompat.getRootWindowInsets(root)?.let {
        systemBars = it.getInsets(WindowInsetsCompat.Type.systemBars())
      }
    }

    val bars = if (adjustment == InsetAdjustment.never) Insets.NONE else systemBars
    val keyboard = if (adjustsForKeyboard) imeBottom else 0

    val paddingTop = root.context.dp(top) + bars.top
    // `max` rather than a sum: the keyboard *replaces* the bottom bar it covers, and adding both
    // would push the list up by the height of a navigation bar nobody can see.
    val paddingBottom =
      maxOf(root.context.dp(bottom) + bars.bottom, keyboard + root.context.dp(bottom))

    resolvedTop = paddingTop
    resolvedBottom = paddingBottom

    list.setPadding(
      root.context.dp(left) + bars.left,
      paddingTop,
      root.context.dp(right) + bars.right,
      paddingBottom,
    )
    // Without this the rows are clipped at the padding edge and the inset reads as a margin — the
    // list would appear to *start* below the bar rather than to pass under it.
    list.clipToPadding = false
    onInsetsChanged()
  }
}

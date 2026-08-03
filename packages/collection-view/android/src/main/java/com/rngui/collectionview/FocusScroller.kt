package com.rngui.collectionview

import android.graphics.Rect
import android.view.View
import android.widget.EditText

/**
 * Brings a focused input clear of whatever the list's bottom padding is reserving.
 *
 * **The keyboard never appears in this file, and that is the design.** `InsetController` already
 * turns the IME height into the list's bottom padding, and `RecyclerView` already scrolls against
 * `getHeight() - getPaddingBottom()` — so "scroll the field above the keyboard" is exactly "ask for
 * a rectangle on screen" once the padding is right. Computing a scroll offset from the IME height
 * here would be a second implementation of a thing the framework does, and one that would have to
 * be kept in step with the first.
 *
 * Split out of `RNGUICollectionViewView` so it can be tested: nothing in the suite can construct
 * that view, which needs a live React instance, and this is the part with logic in it.
 */
object FocusScroller {
  /**
   * @param extraPx grows the target rectangle, so the caret lands with air under it rather than
   *   flush against the keyboard. Also what makes a zero-height caret rect — an empty field — still
   *   scroll to something.
   */
  fun scrollIntoView(focused: View, extraPx: Int, immediate: Boolean): Boolean {
    val target = caretRect(focused) ?: Rect(0, 0, focused.width, focused.height)
    // A negative inset *grows* the rect, which is what is wanted here.
    target.inset(0, -extraPx)
    return focused.requestRectangleOnScreen(target, immediate)
  }

  /**
   * Where the caret is, in the focused view's own coordinates — or null if it has none.
   *
   * **Preferring the caret to the row is not a refinement.** A `textArea` grown to four lines is a
   * tall row, and bringing *the row* into view puts its midpoint on screen, which can leave the
   * line actually being typed underneath the keyboard. The row is the fallback, for a focused view
   * that is not a text field at all.
   *
   * `totalPaddingTop` rather than `paddingTop`: a field with a compound drawable or extra line
   * spacing offsets its layout by more than its padding, and the difference is a caret computed one
   * line off in exactly the case this exists to rescue.
   */
  fun caretRect(view: View): Rect? {
    val field = view as? EditText ?: return null
    val layout = field.layout ?: return null
    val offset = field.selectionEnd.coerceIn(0, field.text.length)
    val line = layout.getLineForOffset(offset)
    val x = layout.getPrimaryHorizontal(offset).toInt() + field.totalPaddingLeft - field.scrollX
    val top = layout.getLineTop(line) + field.totalPaddingTop - field.scrollY
    val bottom = layout.getLineBottom(line) + field.totalPaddingTop - field.scrollY
    return Rect(x, top, x + 1, bottom)
  }
}

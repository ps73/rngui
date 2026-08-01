package com.rngui.collectionview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The count bubble — Settings' unread badge.
 *
 * Drawn rather than composed from a `TextView` on a shaped background, because the shape depends on
 * the text: a single digit is a circle and "12" is a stadium, and getting there with a background
 * drawable means measuring the text, deciding a corner radius from it, and rebuilding the drawable
 * on every bind. One `onDraw` is less code and less garbage.
 *
 * The content is a `String` rather than a number for the reason `RowSpec.badge` gives: these are
 * not always counts. iOS puts version numbers and a bare `!` in the same bubble, and a caller who
 * has "1" already formatted should not have to unformat it.
 */
class BadgeView(context: Context) : View(context) {
  private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val text =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textAlign = Paint.Align.CENTER
      textSize = context.dp(13).toFloat()
      isFakeBoldText = true
    }

  private var label: String = ""

  /** @return false when the row has no badge, so the caller can hide this. */
  fun bind(value: String?, color: Int?): Boolean {
    if (value.isNullOrEmpty()) {
      visibility = GONE
      return false
    }
    label = value
    fill.color = color ?: DEFAULT_RED
    visibility = VISIBLE
    requestLayout()
    return true
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val height = context.dp(20)
    // A stadium, not a circle: the width grows with the text but never below the height, so "1" is
    // round and "128" is a pill. Both are what the platform draws.
    val width = maxOf(height, (text.measureText(label) + context.dp(12)).toInt())
    setMeasuredDimension(width, height)
  }

  override fun onDraw(canvas: Canvas) {
    val radius = height / 2f
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, fill)
    val baseline = height / 2f - (text.descent() + text.ascent()) / 2f
    canvas.drawText(label, width / 2f, baseline, text)
  }

  private companion object {
    /** `systemRed`, which is what `RowSpec.badgeColor` documents as the default. */
    const val DEFAULT_RED = 0xFFFF3B30.toInt()
  }
}

package com.rngui.collectionview

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.rngui.collectionview.generated.MaterialSymbols
import com.rngui.collectionview.generated.SwipeActionSpec
import com.rngui.collectionview.generated.SwipeActionStyle

/**
 * Swipe to reveal a tray of actions.
 *
 * **Worth saying plainly: Material's own guidance is that swipe means *dismiss*.** A revealed
 * multi-button tray is an iOS idiom, and an Android-first design should reach for an overflow menu.
 * It is implemented because the shared API has `leadingActions` and `trailingActions` and dropping
 * them silently would be worse than implementing them off-idiom — the README says so too.
 *
 * **`ItemTouchHelper` drives the gesture but does not own the open state**, and that split is the
 * whole design. `ItemTouchHelper` always recovers `translationX` to zero when the finger lifts;
 * a tray that closes on release is a tray whose actions can never be tapped, so the row would have
 * exactly one reachable action — whichever a full-width swipe happened to trigger. So the gesture
 * ends by *deciding*: past the reveal threshold the row is pinned open by this class, and the tray
 * is drawn by [SwipeTrayDecoration], which keeps drawing it long after `onChildDraw` has stopped
 * being called.
 */
class SwipeActionsCallback(
  private val actionsAt: (position: Int) -> Pair<List<SwipeActionSpec>, List<SwipeActionSpec>>,
  private val rowIdAt: (position: Int) -> String?,
  private val style: () -> RowStyle,
  private val onAction: (rowId: String, actionId: String) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

  /** Which row is held open, and how far. Zero displacement means nothing is open. */
  internal var openPosition = RecyclerView.NO_POSITION
    private set

  internal var openDx = 0f
    private set

  /** The row being dragged right now, if any — drawn from the live displacement instead. */
  internal var activePosition = RecyclerView.NO_POSITION
    private set

  internal var activeDx = 0f
    private set

  /** Filled by the decoration each time it draws, so taps can be routed to a real rect. */
  internal val hitRects = mutableListOf<Pair<RectF, SwipeActionSpec>>()

  override fun getMovementFlags(view: RecyclerView, holder: RecyclerView.ViewHolder): Int {
    val (leading, trailing) = actionsAt(holder.bindingAdapterPosition)
    var flags = 0
    // Leading actions are revealed by swiping *right*, trailing by swiping left — the same
    // handedness as iOS, and the reason a row with only trailing actions must not be draggable to
    // the right at all. A row that moves and then snaps back reads as a broken gesture.
    if (leading.isNotEmpty()) flags = flags or ItemTouchHelper.RIGHT
    if (trailing.isNotEmpty()) flags = flags or ItemTouchHelper.LEFT
    return makeMovementFlags(0, flags)
  }

  override fun onMove(
    view: RecyclerView,
    holder: RecyclerView.ViewHolder,
    target: RecyclerView.ViewHolder,
  ) = false

  /** Never dismisses. See the class doc: dismissal is the semantic this deliberately avoids. */
  override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

  /** Effectively unreachable, so the gesture always ends in [clearView] rather than in a dismiss. */
  override fun getSwipeThreshold(holder: RecyclerView.ViewHolder) = 10f

  override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

  override fun onChildDraw(
    canvas: Canvas,
    parent: RecyclerView,
    holder: RecyclerView.ViewHolder,
    dX: Float,
    dY: Float,
    actionState: Int,
    isCurrentlyActive: Boolean,
  ) {
    val position = holder.bindingAdapterPosition
    val actions = actionsFor(position, dX)
    if (actions.isEmpty()) {
      super.onChildDraw(canvas, parent, holder, 0f, dY, actionState, isCurrentlyActive)
      return
    }

    // Capped at the tray's natural width. Without this the row can be dragged clear of the screen
    // and the actions end up wider than anything anyone would aim at.
    val trayWidth = trayWidth(holder, actions)
    val clamped = dX.coerceIn(-trayWidth, trayWidth)

    if (isCurrentlyActive) {
      activePosition = position
      activeDx = clamped
      // A new drag closes whatever was open. Two open trays at once is a state nobody can reason
      // about, least of all the person looking at it.
      if (openPosition != position) close(parent)
    }

    super.onChildDraw(canvas, parent, holder, clamped, dY, actionState, isCurrentlyActive)
  }

  /**
   * The gesture is over: decide whether the row stays open, and pin it if so.
   *
   * `ItemTouchHelper` has already animated `translationX` back to zero by the time this runs, so
   * "stays open" means setting it again here and taking over from the helper entirely.
   */
  override fun clearView(parent: RecyclerView, holder: RecyclerView.ViewHolder) {
    super.clearView(parent, holder)
    val position = holder.bindingAdapterPosition
    val dx = activeDx
    activePosition = RecyclerView.NO_POSITION
    activeDx = 0f

    val actions = actionsFor(position, dx)
    val trayWidth = if (actions.isEmpty()) 0f else trayWidth(holder, actions)

    if (actions.isEmpty() || kotlin.math.abs(dx) < trayWidth * REVEAL_FRACTION) {
      holder.itemView.translationX = 0f
      if (openPosition == position) close(parent)
      return
    }

    openPosition = position
    openDx = if (dx < 0) -trayWidth else trayWidth
    holder.itemView.translationX = openDx
    // `invalidate`, never `invalidateItemDecorations`: the latter requests a layout, and a layout
    // pass resets the translation that was just set — the tray drew correctly while the row
    // snapped shut behind it. A decoration only needs a redraw.
    parent.invalidate()
  }

  /** Springs the open row shut. */
  fun close(parent: RecyclerView) {
    val position = openPosition
    openPosition = RecyclerView.NO_POSITION
    openDx = 0f
    hitRects.clear()
    if (position == RecyclerView.NO_POSITION) return
    parent.findViewHolderForAdapterPosition(position)?.itemView?.translationX = 0f
    parent.invalidate()
  }

  internal fun actionsFor(position: Int, dx: Float): List<SwipeActionSpec> {
    val (leading, trailing) = actionsAt(position)
    return if (dx > 0) leading else trailing
  }

  internal fun trayWidth(holder: RecyclerView.ViewHolder, actions: List<SwipeActionSpec>): Float =
    (holder.itemView.context.dp(ACTION_WIDTH_DP) * actions.size).toFloat()

  internal fun colorFor(action: SwipeActionSpec): Int =
    parseRnguiHex(action.backgroundColor)
      ?: if (action.style == SwipeActionStyle.destructive) DESTRUCTIVE else style().tintColor

  /**
   * Routes a tap inside an open tray to the action under it, and closes the tray otherwise.
   *
   * The tray is drawn, not laid out, so nothing in it can receive a touch on its own — the same
   * problem the pinned header has, solved the same way.
   */
  fun touchListener(): RecyclerView.OnItemTouchListener =
    object : RecyclerView.OnItemTouchListener {
      override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
        if (openPosition == RecyclerView.NO_POSITION) return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false

        val hit = hitRects.firstOrNull { it.first.contains(event.x, event.y) }
        if (hit == null) {
          // A tap anywhere else closes the tray rather than reaching the row under it. Tapping
          // "away" to dismiss is what every platform does, and letting the touch through would
          // activate a row the user was trying to stop looking at.
          close(view)
          return true
        }

        rowIdAt(openPosition)?.let { onAction(it, hit.second.id) }
        close(view)
        return true
      }

      override fun onTouchEvent(view: RecyclerView, event: MotionEvent) = Unit

      override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit
    }

  internal companion object {
    const val ACTION_WIDTH_DP = 84

    /** How far the row has to travel before it stays open rather than springing back. */
    const val REVEAL_FRACTION = 0.5f

    /** `systemRed`, matching `SwipeActionStyle.destructive` on iOS. */
    const val DESTRUCTIVE = 0xFFFF3B30.toInt()
  }
}

/**
 * Draws the tray behind whichever row is displaced.
 *
 * A decoration rather than `onChildDraw`, because `onChildDraw` is only called while
 * `ItemTouchHelper` is animating — and the tray has to keep being drawn for as long as the row is
 * held open, which is exactly when the helper has stopped caring.
 */
class SwipeTrayDecoration(private val callback: SwipeActionsCallback) :
  RecyclerView.ItemDecoration() {

  private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val label =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      isFakeBoldText = true
      color = Color.WHITE
    }
  private val glyph =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      color = Color.WHITE
    }

  override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    callback.hitRects.clear()

    val position =
      if (callback.activePosition != RecyclerView.NO_POSITION) callback.activePosition
      else callback.openPosition
    if (position == RecyclerView.NO_POSITION) return

    val dx =
      if (callback.activePosition != RecyclerView.NO_POSITION) callback.activeDx
      else callback.openDx
    if (dx == 0f) return

    val holder = parent.findViewHolderForAdapterPosition(position) ?: return
    val actions = callback.actionsFor(position, dx)
    if (actions.isEmpty()) return

    val view = holder.itemView
    // Re-asserted on every draw rather than set once when the gesture ended.
    //
    // Setting `translationX` in `clearView` is not enough: anything that triggers a layout pass —
    // and inside React Native that includes this component's own posted re-layout — scraps and
    // re-attaches the row, and the translation goes with it. The row then sits shut while the tray
    // is drawn behind it, which looks exactly like the tray failing to open. Asserting it here
    // costs one float assignment per draw and cannot drift.
    if (callback.activePosition == RecyclerView.NO_POSITION && view.translationX != dx) {
      view.translationX = dx
    }
    val trayWidth = callback.trayWidth(holder, actions)
    val slotWidth = trayWidth / actions.size

    actions.forEachIndexed { index, action ->
      val left =
        if (dx < 0) view.right - trayWidth + index * slotWidth
        else view.left + index * slotWidth
      val rect = RectF(left, view.top.toFloat(), left + slotWidth, view.bottom.toFloat())
      callback.hitRects += rect to action

      fill.color = callback.colorFor(action)
      canvas.drawRect(rect, fill)

      // The symbol wins over the title when both are set, as UIKit prefers elsewhere.
      val codepoint = action.systemImage?.let { MaterialSymbols.bySfName[it] }
      if (codepoint != null) {
        glyph.textSize = view.context.dp(22).toFloat()
        glyph.typeface = IconView.typeface(view.context)
        val baseline = rect.centerY() - (glyph.descent() + glyph.ascent()) / 2
        canvas.drawText(String(Character.toChars(codepoint)), rect.centerX(), baseline, glyph)
      } else {
        action.title?.takeIf { it.isNotEmpty() }?.let { title ->
          label.textSize = view.context.dp(14).toFloat()
          val baseline = rect.centerY() - (label.descent() + label.ascent()) / 2
          canvas.drawText(title, rect.centerX(), baseline, label)
        }
      }
    }
  }
}

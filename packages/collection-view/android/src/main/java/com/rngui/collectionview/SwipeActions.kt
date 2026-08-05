package com.rngui.collectionview

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.rngui.collectionview.generated.MaterialSymbols
import com.rngui.collectionview.generated.SwipeActionSpec
import com.rngui.collectionview.generated.SwipeActionStyle
import kotlin.math.min

/**
 * Swipe to reveal a row's actions, as Material 3 Expressive reveals them.
 *
 * **Circular buttons beside the card, not coloured slabs behind it.** That is what
 * `ListItemRevealLayout` draws in the
 * [M3 Expressive list docs](https://github.com/material-components/material-components-android/blob/master/docs/components/List.md#m3-expressive):
 * the item keeps its own container and slides, and what appears alongside is a row of round action
 * buttons at their intrinsic size. Full-height rectangles are the iOS treatment — `UISwipeAction`
 * fills the cell's height edge to edge — and drawing those was the most obviously wrong thing on
 * this screen.
 *
 * ```
 *   ╭──────────────────────────╮ ⟵ the item slides
 *   │ List item                │  ( ⌫ ) ( ⚑ )   ⟵ circular actions, revealed
 *   ╰──────────────────────────╯
 * ```
 *
 * Material's own guidance is still that a swipe means *dismiss*, and an Android-first design would
 * reach for an overflow menu. This exists because the shared API has `leadingActions` and
 * `trailingActions` and dropping them silently would be worse; `onSwiped` is deliberately a no-op,
 * so nothing here ever removes a row.
 *
 * **`ItemTouchHelper` drives the gesture; this owns the open state.** The helper always recovers
 * `translationX` to zero when the finger lifts, and a tray that closes on release is a tray whose
 * actions can never be tapped.
 */
class SwipeActionsCallback(
  private val actionsAt: (position: Int) -> Pair<List<SwipeActionSpec>, List<SwipeActionSpec>>,
  private val rowIdAt: (position: Int) -> String?,
  private val positionOfRow: (rowId: String) -> Int,
  private val style: () -> RowStyle,
  private val onAction: (rowId: String, actionId: String) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

  /**
   * *Which row* is held open — the identity, not the slot.
   *
   * **This is the open tray's source of truth, and [openPosition] is only a cache of it.** An
   * adapter position is a statement about the list as it was when the finger lifted, and the list
   * can change under an open tray for reasons that have nothing to do with it: any commit at all —
   * a parent re-render, a navigation focus effect, a `colorScheme` flip — can insert or remove rows
   * above this one. The tray would then be drawn beside a *different* row and, worse, the tap would
   * be routed to whichever id had moved into the remembered slot. On the example's Contacts screen
   * that is a delete against the wrong contact.
   *
   * The deleting path itself was always safe, because `close()` runs before the commit that removes
   * the row — which is exactly why this survived manual testing.
   */
  internal var openRowId: String? = null
    private set

  /**
   * Where [openRowId] currently sits, cached so the hot paths do not have to search for it.
   *
   * [translationFor] is called once per bind and the attach listener once per row coming back on
   * screen; both need the answer immediately, and neither can afford a scan of the list. [rebase]
   * is what keeps this honest whenever the list moves underneath it.
   */
  internal var openPosition = RecyclerView.NO_POSITION
    private set

  internal var openDx = 0f
    private set

  /** The row being dragged right now, if any — drawn from the live displacement instead. */
  internal var activePosition = RecyclerView.NO_POSITION
    private set

  internal var activeDx = 0f
    private set

  /** Filled by the decoration each time it draws, so taps can be routed to a real button. */
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

  /** Never dismisses. Dismissal is the semantic this deliberately avoids. */
  override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

  /** Effectively unreachable, so the gesture always ends in [clearView]. */
  override fun getSwipeThreshold(holder: RecyclerView.ViewHolder) = 10f

  override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

  /**
   * No recovery animation — this owns the settle.
   *
   * The helper's default is to animate `translationX` back to zero over 200ms and *then* call
   * [clearView], so pinning the row open there produced snap-shut-then-jump-open on every release.
   * That is the whole of the "laggy" feel: two animations disagreeing, not a slow one.
   */
  override fun getAnimationDuration(
    view: RecyclerView,
    animationType: Int,
    animateDx: Float,
    animateDy: Float,
  ) = 0L

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

  /** The gesture is over: decide whether the row stays open, and pin it if so. */
  override fun clearView(parent: RecyclerView, holder: RecyclerView.ViewHolder) {
    super.clearView(parent, holder)
    // The position captured when the drag began, falling back to the holder's. Defensive rather
    // than load-bearing: a holder detached or rebound between the last move and the release would
    // report `NO_POSITION`, which looks up no actions and closes a tray the user had just dragged
    // fully open. Not observed, and cheap enough to rule out.
    val position =
      if (activePosition != RecyclerView.NO_POSITION) activePosition
      else holder.bindingAdapterPosition
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

    // The identity first, and no tray without one: a row whose id cannot be read is a row whose
    // action could never be dispatched anyway, so holding it open would only offer buttons that
    // do nothing.
    val rowId = rowIdAt(position)
    if (rowId == null) {
      holder.itemView.translationX = 0f
      if (openPosition == position) close(parent)
      return
    }

    openRowId = rowId
    openPosition = position
    openDx = if (dx < 0) -trayWidth else trayWidth
    // Set rather than animated: the finger just lifted somewhere near this position, so the
    // remaining distance is a few pixels and animating it only adds latency to the reveal.
    holder.itemView.translationX = openDx
    parent.invalidate()
  }

  /**
   * Re-points the open tray at wherever its row went, and drops it if the row is gone.
   *
   * **Called on every adapter change**, because that is the only moment [openPosition] can become a
   * lie — see [openRowId]. Resolving by identity means an unrelated commit no longer moves the tray
   * onto a neighbour: the rows shift, the cached slot is corrected, and the tray stays on the row
   * the user opened.
   *
   * Nothing is animated here. This runs inside the adapter's own change dispatch, where the view
   * for a removed row is mid-removal and the one for a moved row may not have been laid out at its
   * new position yet — so the state is corrected and the next draw is left to place it.
   */
  /**
   * Calls [rebase] on every change that can move a row.
   *
   * A factory beside [attachStateListener] and [touchListener], for the same reason those are: the
   * host registers it and nothing else needs to know the shape of it. `onItemRangeChanged` is
   * deliberately not overridden — a payload change rebinds a row without moving it.
   */
  fun dataObserver(parent: RecyclerView): RecyclerView.AdapterDataObserver =
    object : RecyclerView.AdapterDataObserver() {
      override fun onChanged() = rebase(parent)

      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = rebase(parent)

      override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = rebase(parent)

      override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
        rebase(parent)
    }

  fun rebase(parent: RecyclerView) {
    val rowId = openRowId ?: return
    val next = positionOfRow(rowId)
    if (next == RecyclerView.NO_POSITION) {
      openRowId = null
      openPosition = RecyclerView.NO_POSITION
      openDx = 0f
      hitRects.clear()
    } else {
      openPosition = next
    }
    parent.invalidate()
  }

  /** Springs the open row shut, and animates it because nothing else is moving at that moment. */
  fun close(parent: RecyclerView) {
    val position = openPosition
    openRowId = null
    openPosition = RecyclerView.NO_POSITION
    openDx = 0f
    hitRects.clear()
    if (position == RecyclerView.NO_POSITION) return

    val view = parent.findViewHolderForAdapterPosition(position)?.itemView
    if (view == null || view.translationX == 0f) {
      parent.invalidate()
      return
    }
    view
      .animate()
      .translationX(0f)
      .setDuration(CLOSE_MS)
      .withEndAction { parent.invalidate() }
      .start()
  }

  /**
   * The displacement a row should be drawn at, for the adapter to re-apply on bind.
   *
   * **`RecyclerView` resets `translationX` behind our back.** `ItemAnimator.endAnimation` — which
   * runs on every layout pass that scraps or rebinds a row, and inside React Native that includes
   * this component's own posted re-layout — sets it straight back to zero. So the open state cannot
   * live only in the view; it lives here, and the two places a row can come back from ask for it.
   */
  fun translationFor(position: Int): Float =
    if (position == openPosition) openDx else 0f

  /**
   * Re-applies the open row's displacement whenever its view comes back.
   *
   * **An attach listener rather than a nudge from `onDraw`**, and that swap is the other half of
   * the lag. Anything that triggers a layout — including this component's own posted re-layout,
   * which exists because React Native swallows `requestLayout` from below — scraps and re-attaches
   * the row and takes the translation with it. Restoring it from inside the decoration's draw meant
   * mutating a view during a draw pass, which invalidates and schedules another draw, which mutates
   * again: a redraw loop that costs a frame every frame for as long as a tray is open.
   */
  fun attachStateListener(): RecyclerView.OnChildAttachStateChangeListener =
    object : RecyclerView.OnChildAttachStateChangeListener {
      override fun onChildViewAttachedToWindow(view: View) {
        if (openPosition == RecyclerView.NO_POSITION) return
        val holder = (view.parent as? RecyclerView)?.getChildViewHolder(view) ?: return
        if (holder.bindingAdapterPosition == openPosition) view.translationX = openDx
      }

      override fun onChildViewDetachedFromWindow(view: View) = Unit
    }

  internal fun actionsFor(position: Int, dx: Float): List<SwipeActionSpec> {
    val (leading, trailing) = actionsAt(position)
    return if (dx > 0) leading else trailing
  }

  /**
   * How far the row travels: one circular button per action, plus the gaps around them.
   *
   * Derived from the row's own height rather than a constant, so the buttons stay circles inside a
   * one-line item and inside a two-line one.
   */
  internal fun trayWidth(holder: RecyclerView.ViewHolder, actions: List<SwipeActionSpec>): Float {
    val context = holder.itemView.context
    val diameter = buttonDiameter(holder)
    val gap = context.dp(BUTTON_GAP_DP)
    return actions.size * diameter + (actions.size + 1) * gap
  }

  internal fun buttonDiameter(holder: RecyclerView.ViewHolder): Float {
    val context = holder.itemView.context
    val available = holder.itemView.height - 2 * context.dp(BUTTON_GAP_DP)
    return min(available.toFloat(), context.dp(MAX_BUTTON_DP).toFloat())
      .coerceAtLeast(context.dp(MIN_BUTTON_DP).toFloat())
  }

  @androidx.annotation.ColorInt
  internal fun containerColorFor(action: SwipeActionSpec): Int {
    parseRnguiHex(action.backgroundColor)?.let { return it }
    val context = style()
    return if (action.style == SwipeActionStyle.destructive) context.errorContainer
    else context.selectedContainer
  }

  @androidx.annotation.ColorInt
  internal fun contentColorFor(action: SwipeActionSpec): Int {
    // A caller-supplied container gets white content, because nothing here can know what would
    // read on an arbitrary colour and white is what a filled action button uses by default.
    if (action.backgroundColor != null) return Color.WHITE
    val resolved = style()
    return if (action.style == SwipeActionStyle.destructive) resolved.onErrorContainer
    else resolved.onSelectedContainer
  }

  /**
   * Routes a tap inside an open tray to the button under it, and closes the tray otherwise.
   *
   * The buttons are drawn, not laid out, so nothing in the tray can receive a touch on its own —
   * the same problem the pinned header has, solved the same way.
   */
  fun touchListener(): RecyclerView.OnItemTouchListener =
    object : RecyclerView.OnItemTouchListener {
      override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
        if (openPosition == RecyclerView.NO_POSITION) return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false

        val hit = hitRects.firstOrNull { it.first.contains(event.x, event.y) }
        if (hit == null) {
          // A tap anywhere else closes the tray rather than reaching the row under it. Tapping
          // away to dismiss is what every platform does, and letting the touch through would
          // activate a row the user was trying to stop looking at.
          close(view)
          return true
        }

        // The remembered id, not a fresh lookup by position. Reading the row out of the slot is
        // what made this dispatchable against the wrong row in the first place: by the time a tap
        // arrives, the list may have moved and the slot may belong to somebody else.
        openRowId?.let { onAction(it, hit.second.id) }
        close(view)
        return true
      }

      override fun onTouchEvent(view: RecyclerView, event: MotionEvent) = Unit

      override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit
    }

  internal companion object {
    /** M3's list action buttons are circles at the item's height, capped so they stay tappable. */
    const val MAX_BUTTON_DP = 56
    const val MIN_BUTTON_DP = 40
    const val BUTTON_GAP_DP = 8

    /** How far the row has to travel before it stays open rather than springing back. */
    const val REVEAL_FRACTION = 0.5f

    const val CLOSE_MS = 150L
  }
}

/**
 * Draws the revealed action buttons beside whichever row is displaced.
 *
 * A decoration rather than `onChildDraw`, because the latter is only called while
 * `ItemTouchHelper` is animating — and the buttons have to keep being drawn for as long as the row
 * is held open, which is exactly when the helper has stopped caring.
 *
 * **This only draws.** It sets no view property and requests no layout; see
 * `SwipeActionsCallback.attachStateListener` for where the open row's displacement is restored, and
 * why it cannot happen here.
 */
class SwipeTrayDecoration(private val callback: SwipeActionsCallback) :
  RecyclerView.ItemDecoration() {

  private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
  private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

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
    val context = view.context

    // Re-assert the open displacement.
    //
    // `RecyclerView` clears `translationX` from `ItemAnimator.endAnimation` on any layout pass that
    // touches the row — and inside React Native that includes this component's own posted
    // re-layout, which fires constantly. The row is not *rebound* by that pass and it is not
    // detached either, so neither `onBindViewHolder` nor an attach listener sees it; the only hook
    // that reliably runs afterwards is the next draw.
    //
    // The `!=` guard is what keeps this from looping: setting the property invalidates and
    // schedules one more draw, and on that draw the value already matches and nothing is set.
    if (callback.activePosition == RecyclerView.NO_POSITION && view.translationX != dx) {
      view.translationX = dx
    }
    val diameter = callback.buttonDiameter(holder)
    val gap = context.dp(SwipeActionsCallback.BUTTON_GAP_DP).toFloat()
    val trayWidth = callback.trayWidth(holder, actions)
    val centreY = view.top + view.height / 2f
    val radius = diameter / 2f

    // Revealed *proportionally*: the buttons scale up as the row travels, so a half-open tray is
    // half-sized rather than fully drawn under a row that has barely moved.
    val progress = (kotlin.math.abs(dx) / trayWidth).coerceIn(0f, 1f)
    val drawnRadius = radius * progress

    actions.forEachIndexed { index, action ->
      val centreX =
        if (dx < 0) view.right - trayWidth + gap + diameter / 2f + index * (diameter + gap)
        else view.left + trayWidth - gap - diameter / 2f - index * (diameter + gap)

      // The hit rect stays full size whatever the reveal progress: by the time anyone can tap, the
      // tray is open and the button is drawn at full size anyway.
      callback.hitRects +=
        RectF(centreX - radius, centreY - radius, centreX + radius, centreY + radius) to action

      fill.color = callback.containerColorFor(action)
      canvas.drawCircle(centreX, centreY, drawnRadius, fill)

      if (progress < 0.5f) return@forEachIndexed

      val codepoint = action.systemImage?.let { MaterialSymbols.bySfName[it] }
      if (codepoint != null) {
        glyph.color = callback.contentColorFor(action)
        glyph.textSize = context.dp(22).toFloat()
        glyph.typeface = IconView.typeface(context)
        val baseline = centreY - (glyph.descent() + glyph.ascent()) / 2
        canvas.drawText(String(Character.toChars(codepoint)), centreX, baseline, glyph)
      } else {
        action.title?.takeIf { it.isNotEmpty() }?.let { title ->
          label.color = callback.contentColorFor(action)
          label.textSize = context.dp(12).toFloat()
          val baseline = centreY - (label.descent() + label.ascent()) / 2
          canvas.drawText(title, centreX, baseline, label)
        }
      }
    }
  }
}

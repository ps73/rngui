package com.rngui.collectionview

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider as MaterialSlider
import com.rngui.collectionview.generated.AutoCapitalize
import com.rngui.collectionview.generated.ButtonRole
import com.rngui.collectionview.generated.DatePickerMode
import com.rngui.collectionview.generated.DatePickerStyle
import com.rngui.collectionview.generated.KeyboardType
import com.rngui.collectionview.generated.ReturnKeyType
import com.rngui.collectionview.generated.RowKind
import com.rngui.collectionview.generated.RowSpec
import java.util.Calendar

/** Points, which the tree speaks, to Android pixels. dp and iOS points are the same unit. */
fun Context.dp(value: Number): Int =
  TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      resources.displayMetrics,
    )
    .toInt()

/**
 * A row of any kind.
 *
 * ```
 *  ┌───────────────────────────────────────────────────────────┐
 *  │ [icon] label                        value [badge] [access] │
 *  │        secondaryLabel                    ⟨ or a control ⟩  │
 *  └───────────────────────────────────────────────────────────┘
 * ```
 *
 * **One class, but one instance per kind**, because the adapter gives each `RowKind` its own view
 * type and therefore its own pool. That is the same separation `DatePickerStyle` forces on iOS —
 * a cell configured as a compact pill cannot be reconfigured into a calendar — generalised: a
 * holder built for a `switch` never has to become a `textField`, so its control can be created
 * once in the constructor rather than swapped on every bind.
 *
 * Views rather than Compose for the stock kinds, per M1. The control-bearing kinds could go either
 * way and currently use platform widgets, which cost no Compose dependency in the shipped AAR.
 */
class RowView(context: Context, private val kind: RowKind, private val events: RowEvents) :
  LinearLayout(context) {

  private var boundRowId: String = ""

  /** What [RowSpec.isSelected] said last time, so a change in it can be told from a recycle. */
  private var boundSelected: Boolean = false

  /**
   * The M3 container this row draws in — its shape, its fill, and the transition between states.
   *
   * Installed here rather than assigned by the adapter on every bind, which is what lets a
   * selection change be animated at all; see [RowContainer].
   */
  private val container = RowContainer(this)

  @VisibleForTesting val containerForTest: RowContainer get() = container

  // -- text ---------------------------------------------------------------------------------

  private val labelView =
    TextView(context).apply { layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, WRAP) }

  private val secondaryView =
    TextView(context).apply {
      visibility = View.GONE
      layoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, WRAP).apply { topMargin = context.dp(2) }
    }

  /**
   * Holds the label and its second line. Populated in `init`, not here.
   *
   * A `card` row puts `labelView` in [cardColumn] instead, and a view can only have one parent —
   * adding it eagerly here threw `IllegalStateException: The specified child already has a parent`
   * the first time a card scrolled into view. Every `addView` in this class happens in one place,
   * so which parent a child gets is decided once rather than in two initializers that have to
   * agree.
   */
  private val textColumn =
    LinearLayout(context).apply {
      orientation = VERTICAL
      layoutParams = LayoutParams(0, WRAP, 1f)
    }

  private val valueView =
    TextView(context).apply {
      visibility = View.GONE
      layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) }
      // A long value must not push the label off the row; the label wins the space and the value
      // takes what is left. A value allowed to grow without bound is the classic way a Settings
      // row loses its title.
      maxLines = 1
    }

  private val iconView =
    IconView(context).apply {
      visibility = View.GONE
      layoutParams = LayoutParams(WRAP, WRAP).apply { marginEnd = context.dp(12) }
    }

  private val badgeView =
    BadgeView(context).apply {
      visibility = View.GONE
      layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) }
    }

  private val accessoryView =
    AccessoryView(context).apply {
      visibility = View.GONE
      layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) }
    }

  // -- controls, created only for the kind this holder serves --------------------------------

  /**
   * `MaterialSwitch`, not `android.widget.Switch`.
   *
   * The platform widget is the Holo-era track-and-thumb, and it looks a decade old next to
   * anything else on a modern screen — which is precisely what it was. `MaterialSwitch` is the M3
   * component: a larger thumb that grows on press, an optional icon in the thumb, and the
   * `primary`/`surfaceContainerHighest` colour roles rather than a grey slab.
   */
  private val switchView: MaterialSwitch? =
    if (kind == RowKind.switch) {
      MaterialSwitch(context).apply {
        layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(16) }
        // The row owns the tap; this displays state. Two hit targets that disagree about what a
        // tap means is worse than one.
        isClickable = true
      }
    } else {
      null
    }

  /**
   * The M3 slider, with a glyph either side of it.
   *
   * **`com.google.android.material.slider.Slider`, not `android.widget.SeekBar`.** The platform
   * widget is the Holo track-and-thumb the way `Switch` was, and M3 Expressive moved the slider
   * further from it than almost any other component: a thick track, a *gap* between the filled and
   * empty halves, a vertical handle bar rather than a knob, and a dot marking the far end. Drawing
   * that over a `SeekBar` would mean reimplementing the component; using Material's means getting
   * it, including the value label and the handle's press animation.
   *
   * The flanking icons are ours. `UISlider` has `minimumValueImage`/`maximumValueImage` slots and
   * Material's has no equivalent, so the row lays the two out around the track to the same effect —
   * which is also what Android's own brightness and volume rows do.
   */
  private val sliderView: MaterialSlider? =
    if (kind == RowKind.slider) {
      MaterialSlider(context).apply {
        layoutParams = LayoutParams(0, WRAP, 1f)
        // The row is not selectable, so the slider owns the whole gesture; without this the list
        // steals the drag the moment it crosses the touch slop.
        isClickable = true
      }
    } else {
      null
    }

  private val sliderMinIcon: IconView? = if (kind == RowKind.slider) IconView(context) else null
  private val sliderMaxIcon: IconView? = if (kind == RowKind.slider) IconView(context) else null

  private val editText: EditText? =
    if (kind == RowKind.textField || kind == RowKind.textArea) {
      EditText(context).apply {
        // **Not a `TextInputLayout`.** The iOS row is borderless and inline; a boxed Material
        // field inside a list row is a different component with a different meaning — it says
        // "this is a form field in a form", where the row says "this row's value is editable".
        background = null
        setPadding(0, 0, 0, 0)
        layoutParams = LayoutParams(0, WRAP, 1f)
      }
    } else {
      null
    }

  /**
   * The `cm` after `187`.
   *
   * `textField` only — a `textArea` grows with its content, so a suffix has no line to sit on, and
   * neither the TypeScript API nor the serializer will produce one.
   */
  private val unitView: TextView? =
    if (kind == RowKind.textField) {
      TextView(context).apply {
        visibility = View.GONE
        maxLines = 1
        layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(4) }
      }
    } else {
      null
    }

  private val dateValueView: TextView? =
    if (kind == RowKind.datePicker) {
      TextView(context).apply {
        layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) }
      }
    } else {
      null
    }

  private val cardColumn: LinearLayout? =
    if (kind == RowKind.card) {
      LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, WRAP)
      }
    } else {
      null
    }

  private val cardValueView: TextView? = if (kind == RowKind.card) TextView(context) else null
  private val cardCaptionView: TextView? = if (kind == RowKind.card) TextView(context) else null

  // -- listeners, held so they can be detached ------------------------------------------------
  //
  // **The reuse rule, and on Android it bites harder than it did on iOS.** A recycled `EditText`
  // keeps its `TextWatcher` and a recycled `Switch` keeps its `OnCheckedChangeListener`; setting
  // the new row's state then fires them, and JavaScript writes back a change the user never made.
  // Every listener below is detached before the state is assigned and reattached after — which is
  // why they are fields rather than lambdas passed inline.

  private val textWatcher =
    object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {
        val text = s?.toString().orEmpty()
        pendingEchoes.add(text)
        if (pendingEchoes.size > MAX_ECHOES) {
          pendingEchoes.subList(0, pendingEchoes.size - MAX_ECHOES).clear()
        }
        events.onTextChange(boundRowId, text)
      }

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    }

  /**
   * Values sent to JavaScript and not yet echoed back. See [applyText].
   *
   * Bounded: a burst of typing is short, and an unbounded list would grow for the lifetime of a
   * field whose value JavaScript never round-trips.
   */
  private val pendingEchoes = ArrayList<String>()

  private val focusListener =
    OnFocusChangeListener { _, focused -> events.onFocusChange(boundRowId, focused) }

  private val switchListener =
    android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
      events.onSwitchChange(boundRowId, checked)
    }

  init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    // M3 list item metrics: a one-line item is 56dp tall, a two-line one 72dp, with 16dp of
    // horizontal padding. iOS's 44pt row is shorter than anything Android draws.
    minimumHeight = context.dp(56)
    setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP)

    addView(iconView)

    when (kind) {
      /**
       * **The leading label this row never had.** iOS's `TextFieldCell` has drawn one since the
       * cell existed; this branch added the field alone, so an Android `<TextField>` under a
       * `<Label>` showed the field and nothing else — a silently dropped prop rather than a
       * documented difference.
       *
       * Added directly rather than through [textColumn]: a text row has no second line, and that
       * column carries `LayoutParams(0, WRAP, 1f)`, which would split the row down the middle with
       * the field instead of letting the label take what it needs and the field the rest.
       */
      RowKind.textField -> {
        // Properties of the kind, not of the row, so they belong here rather than in `bindText`:
        // this holder serves `textField` for its whole life. A label allowed to wrap would turn a
        // one-line row into two the first time someone used a long one.
        labelView.maxLines = 1
        labelView.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(labelView, LayoutParams(WRAP, WRAP).apply { marginEnd = context.dp(8) })
        addView(editText)
        addView(unitView)
      }
      // Fills the row, as it does on iOS — `TextAreaCell` has no label either.
      RowKind.textArea -> addView(editText)
      // The track fills the row. A label above it would be a two-line row, and M3 puts a slider's
      // label in its own value bubble rather than beside the track.
      RowKind.slider -> {
        addView(sliderMinIcon)
        addView(sliderView)
        addView(sliderMaxIcon)
      }
      RowKind.card -> {
        cardColumn!!.addView(labelView)
        cardColumn.addView(cardValueView)
        cardColumn.addView(cardCaptionView)
        addView(cardColumn)
      }
      else -> {
        textColumn.addView(labelView)
        textColumn.addView(secondaryView)
        addView(textColumn)
      }
    }

    addView(valueView)
    dateValueView?.let(::addView)
    addView(badgeView)
    switchView?.let(::addView)
    addView(accessoryView)
  }

  /**
   * Fully specifies every field this view can show.
   *
   * Every branch has an `else`. A row that only assigns `value` when the spec has one inherits the
   * previous occupant's when it does not — which reads as a data bug rather than a recycling one,
   * and is the single most common way a recycled list goes subtly wrong.
   */
  fun bind(row: RowSpec, style: RowStyle, listStyle: ListStyle, position: Item.Position) {
    val selected = row.isSelected
    // **The one question [RowContainer] cannot answer for itself**, and it has to be asked before
    // the fields below are overwritten: is this the same row changing state, or a recycled holder
    // arriving at a new one? Only the first is a transition. A restyle answers `false` too — the ids
    // match but nothing about the selection moved — so a theme flip repaints 2,000 rows rather than
    // animating them.
    val sameRow = row.id == boundRowId
    val transitioned = sameRow && selected != boundSelected
    // A pending echo belongs to the row that sent it, and a holder that has moved on will never see
    // it come back. Carrying the list over would let one field's stale value be mistaken for
    // another's; see `applyText`.
    if (!sameRow) pendingEchoes.clear()
    boundRowId = row.id
    boundSelected = selected

    container.apply(position, listStyle, selected, animate = transitioned)

    val disabled = row.disabled == true

    iconView.bind(row, style)
    badgeView.bind(row.badge, parseRnguiHex(row.badgeColor))
    accessoryView.bind(row.accessory, row.on == true, style, disabled)

    val tint = parseRnguiHex(row.tintColor) ?: style.tintColor

    when (kind) {
      RowKind.button -> bindButton(row, style, tint, disabled)
      RowKind.textField,
      RowKind.textArea -> bindText(row, style, disabled)
      RowKind.card -> bindCard(row, style, tint, disabled)
      RowKind.switch -> {
        bindLabels(row, style, tint, disabled)
        bindSwitch(row, disabled)
      }
      RowKind.slider -> bindSlider(row, style, disabled)
      RowKind.datePicker -> {
        bindLabels(row, style, tint, disabled)
        bindDatePicker(row, style, tint, disabled)
      }
      RowKind.menu -> {
        bindLabels(row, style, tint, disabled)
        bindMenu(row, style, disabled)
      }
      // `chip` renders as an ordinary compact row until M9 gives it a horizontal strip to live in.
      else -> bindLabels(row, style, tint, disabled)
    }

    isEnabled = !disabled
    // **A `menu` row is clickable whether or not it is `selectable`**, because the click is what
    // opens the popup rather than what reports a press. Deriving this from `selectable` alone
    // silently disabled every menu row that had no `onPress` — which is all of them, since a menu
    // reports its choice through `onMenuSelect`. The row looked fine and did nothing.
    isClickable = (row.selectable == true || kind == RowKind.menu) && !disabled
    alpha = if (disabled) DISABLED_ALPHA else 1f
  }

  // -- per-kind binding -----------------------------------------------------------------------

  private fun bindLabels(row: RowSpec, style: RowStyle, tint: Int, disabled: Boolean) {
    labelView.text = row.label.orEmpty()
    labelView.gravity = Gravity.START
    labelView.setTextColor(if (disabled) style.disabledColor else style.labelColor)
    FontResolver.apply(labelView, row.font ?: style.font, LABEL_SIZE_SP, context)

    val secondary = if (row.kind == RowKind.subtitle) row.secondaryLabel else null
    secondaryView.visibility = if (secondary != null) View.VISIBLE else View.GONE
    secondaryView.text = secondary.orEmpty()
    FontResolver.apply(secondaryView, style.font, SECONDARY_SIZE_SP, context)
    secondaryView.setTextColor(
      when {
        disabled -> style.disabledColor
        // The "Today" / "15:00" treatment: the tint marks the value as the row's *current setting*
        // rather than as an explanatory second line.
        row.secondaryLabelTinted == true -> tint
        else -> style.secondaryColor
      }
    )

    val value = if (row.kind == RowKind.value) row.value else null
    valueView.visibility = if (value != null) View.VISIBLE else View.GONE
    valueView.text = value.orEmpty()
    FontResolver.apply(valueView, row.font ?: style.font, LABEL_SIZE_SP, context)
    valueView.setTextColor(if (disabled) style.disabledColor else style.secondaryColor)
  }

  /** A tinted, centred, tappable label — not a row that happens to be pressable. */
  private fun bindButton(row: RowSpec, style: RowStyle, tint: Int, disabled: Boolean) {
    labelView.text = row.label.orEmpty()
    labelView.gravity = Gravity.CENTER
    FontResolver.apply(labelView, row.font ?: style.font, LABEL_SIZE_SP, context)
    labelView.setTextColor(
      when {
        disabled -> style.disabledColor
        row.role == ButtonRole.destructive -> DESTRUCTIVE
        row.role == ButtonRole.plain -> style.labelColor
        else -> tint
      }
    )
    secondaryView.visibility = View.GONE
    valueView.visibility = View.GONE
  }

  private fun bindSwitch(row: RowSpec, disabled: Boolean) {
    val control = switchView ?: return
    // Detached, assigned, reattached. Assigning `isChecked` with the listener still attached fires
    // it, and JavaScript writes the value back as state — a change the user never made, on a row
    // they may never have seen.
    control.setOnCheckedChangeListener(null)
    control.isChecked = row.on == true
    control.isEnabled = !disabled
    control.setOnCheckedChangeListener(switchListener)
  }

  /**
   * The track, its bounds, and the two glyphs beside it.
   *
   * **Incoming values are ignored while the thumb is down**, which is the numeric form of the rule
   * `applyText` follows. A drag reports sixty values a second, JavaScript turns each into a commit,
   * and the commit carrying frame N lands while the thumb is at frame N+3 — so writing it back
   * would drag the thumb backwards under the finger, sixty times a second. `isDragging` is set from
   * Material's own touch callbacks, so the window it covers is exactly the gesture.
   */
  private fun bindSlider(row: RowSpec, style: RowStyle, disabled: Boolean) {
    val slider = sliderView ?: return

    sliderMinIcon?.bindStandalone(row.sliderMinImage, style, disabled)
    sliderMaxIcon?.bindStandalone(row.sliderMaxImage, style, disabled)

    slider.isEnabled = !disabled
    if (isDragging) return

    // Detached before the bounds move: Material fires the change listener from `setValueTo` when
    // the current value no longer fits the new range, and that would be reported as a change the
    // user never made — the same trap a recycled `Switch` sets with `isChecked`.
    slider.clearOnChangeListeners()
    slider.clearOnSliderTouchListeners()

    val from = row.sliderMin ?: DEFAULT_SLIDER_MIN
    // Guarded rather than trusted. Material throws `IllegalStateException` on an inverted or empty
    // range, which would take the whole list down for one badly-specified row — and a caller
    // building `sliderMax` from data can produce one by accident.
    val to = (row.sliderMax ?: DEFAULT_SLIDER_MAX).let { if (it > from) it else from + 1f.toDouble() }

    slider.valueFrom = from.toFloat()
    slider.valueTo = to.toFloat()
    // `0` is Material's own sentinel for continuous, and it is what the tree means by "unset".
    slider.stepSize = (row.sliderStep ?: 0.0).toFloat().coerceAtLeast(0f)
    slider.value = (row.sliderValue ?: from).toFloat().coerceIn(from.toFloat(), to.toFloat())

    slider.addOnChangeListener { _, value, fromUser ->
      // Programmatic assignments above are already excluded by the detach, but a value Material
      // itself settles on — snapping to a step — arrives with `fromUser` false and is not news.
      if (fromUser) events.onSliderChange(boundRowId, value.toDouble())
    }
    slider.addOnSliderTouchListener(sliderTouchListener)
  }

  /** Set from Material's touch callbacks, so it spans exactly the gesture. See [bindSlider]. */
  private var isDragging = false

  private val sliderTouchListener =
    object : com.google.android.material.slider.Slider.OnSliderTouchListener {
      override fun onStartTrackingTouch(slider: MaterialSlider) {
        isDragging = true
      }

      override fun onStopTrackingTouch(slider: MaterialSlider) {
        isDragging = false
        events.onSliderCommit(boundRowId, slider.value.toDouble())
      }
    }

  /**
   * The keyboard configuration of an [EditText] — the four properties that describe how it is typed
   * into rather than what it holds.
   *
   * One value rather than four assignments because they are one configuration: `setInputType`
   * installs a fresh key listener and resets the transformation method that `setSingleLine` sets,
   * so applying either without the others leaves a field configured half one way.
   */
  private data class InputConfig(
    val inputType: Int,
    val imeOptions: Int,
    val singleLine: Boolean,
    val maxLines: Int,
  )

  /** What [applyInputConfig] last installed, or null before the first bind. */
  private var appliedInputConfig: InputConfig? = null
  private var appliedHint: String? = null
  private var appliedGravity: Int? = null

  /**
   * Installs the keyboard configuration, and **only when it has actually changed**.
   *
   * The naive version assigns all four on every bind, and that is visible rather than merely
   * wasteful: every keystroke round-trips through JavaScript and comes back as a commit a few
   * hundred milliseconds later, and each of those reapplied the configuration. `setSingleLine`
   * swaps in a fresh `SingleLineTransformationMethod`, which re-sets the text and drops the
   * field's horizontal scroll offset — so a right-aligned field twitched once per keystroke as
   * the text snapped back and re-scrolled. The row that made it obvious is a `unit` row, where
   * the value sits against the suffix and any wobble is next to a fixed reference point.
   *
   * The state is tracked here rather than read back off the view because the getters for it are
   * inconsistent across API levels, and because this holder owns its field for its whole life:
   * the same instance that was configured is the one being compared.
   */
  private fun applyInputConfig(field: EditText, config: InputConfig) {
    if (appliedInputConfig == config) return
    appliedInputConfig = config

    field.inputType = config.inputType
    field.imeOptions = config.imeOptions
    // After `inputType`, which resets the transformation method this sets.
    field.isSingleLine = config.singleLine
    if (!config.singleLine) {
      field.maxLines = config.maxLines
      field.setHorizontallyScrolling(false)
    }
  }

  /**
   * Focuses the field and raises the keyboard.
   *
   * Both halves are needed. `requestFocus` alone gives a field with a caret in it that no key press
   * can reach, because on Android the IME is shown for a *touch* on an editor rather than for focus
   * — and a tap on the unit beside it is not that touch.
   */
  private fun focusField(field: EditText) {
    field.requestFocus()
    // The caret goes to the end, which is where a tap on the unit means. `requestFocus` alone
    // restores the field's stored selection — 0 on a freshly bound row — so tapping `cm` to correct
    // `187` would put the caret *before* the value and type into the front of it.
    field.setSelection(field.text?.length ?: 0)
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
      ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun bindText(row: RowSpec, style: RowStyle, disabled: Boolean) {
    val field = editText ?: return
    val font = row.font ?: style.font
    val hasLabel = kind == RowKind.textField && !row.label.isNullOrEmpty()
    val unit = if (kind == RowKind.textField) row.unit.orEmpty() else ""

    field.removeTextChangedListener(textWatcher)
    field.onFocusChangeListener = null

    if (kind == RowKind.textField) {
      // Fully specified, `GONE` included: a recycled holder that kept the previous row's label
      // would caption this row's field with someone else's, which is the recycling bug that reads
      // as a data bug.
      labelView.visibility = if (hasLabel) View.VISIBLE else View.GONE
      // Compared first, here and on the unit below, for the same reason the keyboard configuration
      // is: `setText` relayouts without checking, and this row is rebound on every keystroke.
      val nextLabel = row.label.orEmpty()
      if (labelView.text?.toString() != nextLabel) labelView.text = nextLabel
      labelView.gravity = Gravity.START
      labelView.setTextColor(if (disabled) style.disabledColor else style.labelColor)
      FontResolver.apply(labelView, font, LABEL_SIZE_SP, context)
    }

    unitView?.apply {
      visibility = if (unit.isNotEmpty()) View.VISIBLE else View.GONE
      if (text?.toString() != unit) text = unit
      setTextColor(if (disabled) style.disabledColor else style.secondaryColor)
      FontResolver.apply(this, font, LABEL_SIZE_SP, context)
      // The unit is part of the value's hit target, as it is on iOS: `187` and `cm` are one value
      // to a reader, so they are one target to a finger.
      setOnClickListener(if (disabled) null else ({ focusField(field) }))
      isClickable = !disabled
    }

    // Right-aligned whenever the row holds something else, for the same reason as iOS: a suffix at
    // the far end of a row the value starts at has stopped being a suffix.
    val nextGravity =
      (if (hasLabel || unit.isNotEmpty()) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
    if (appliedGravity != nextGravity) {
      appliedGravity = nextGravity
      field.gravity = nextGravity
    }

    applyText(field, row.text.orEmpty())

    // `setHint` calls `checkForRelayout` without comparing, so assigning the same hint on every
    // commit is a layout pass per keystroke.
    val nextHint = row.placeholder.orEmpty()
    if (appliedHint != nextHint) {
      appliedHint = nextHint
      field.hint = nextHint
    }

    field.isEnabled = !disabled
    field.setTextColor(if (disabled) style.disabledColor else style.labelColor)
    field.setHintTextColor(style.secondaryColor)
    FontResolver.apply(field, font, LABEL_SIZE_SP, context)

    val multiline = kind == RowKind.textArea
    applyInputConfig(
      field,
      InputConfig(
        inputType = inputTypeFor(row, multiline),
        imeOptions = imeOptionsFor(row.returnKeyType),
        singleLine = !multiline,
        // Grows to `maxLines` and then scrolls internally, which is what the Reminders notes field
        // does. Unset means it grows without limit.
        maxLines = if (multiline) row.maxLines ?: Int.MAX_VALUE else 1,
      ),
    )

    field.addTextChangedListener(textWatcher)
    field.onFocusChangeListener = focusListener
  }

  /**
   * Writes the descriptor's text into the field without ever losing a keystroke.
   *
   * The naive version — assign whenever it differs — scrambles what the user typed, and the
   * mechanism is worth spelling out because it is not obvious and it *looks* correct. Each keystroke
   * goes to JavaScript asynchronously. Type `ab` quickly and the sequence is: the field holds `ab`;
   * the commit carrying `a` arrives; `"ab" != "a"` so the field is assigned `"a"`; the `b` is gone.
   * Typing "Rehearsal-on-Thursday" into the naive version produced **`-on-ThursdayRehear`** — which
   * is the second half of the bug, below.
   *
   * So every value sent to JavaScript is remembered, and an incoming value that is one of those is
   * recognised as an **echo** and ignored: JavaScript agreeing with something the field already
   * knows, possibly something it has since moved past. Anything else is a genuine instruction — a
   * clear, an input mask, a value set from elsewhere — and is applied.
   *
   * This is the same problem React Native's own `TextInput` solves with `eventCount`, and the same
   * answer [`TextFieldCell.applyText`] gives on iOS; the pending list is that idea without needing a
   * counter to ride in the tree.
   *
   * **And when a value genuinely is applied, the caret has to be put back.** `EditText.setText`
   * installs a fresh `Editable`, and `ArrowKeyMovementMethod.initialize` then selects offset *zero*
   * on it — so the caret goes to the *start* of the field, not the end, and every subsequent
   * character is typed in front of what came before. That is why the text above came out reversed in
   * chunks rather than merely truncated.
   */
  private fun applyText(field: EditText, next: String) {
    if (field.hasFocus()) {
      val index = pendingEchoes.indexOf(next)
      if (index >= 0) {
        // Everything *older* than this value is now accounted for and can go — a commit never
        // arrives out of order, so those will not be echoed again.
        //
        // **The matched value itself stays.** A `RecyclerView` rebinds a row for reasons that have
        // nothing to do with its content, so the same tree value reaches this method more than
        // once; consuming it on the first pass makes the second a miss, and a miss writes.
        // Instrumented, that read:
        //
        //     apply next='Rehear' have='Re'                → MISS, applied
        //     …eleven keystrokes later…
        //     apply next='Rehear' have='Rehearon-Thursday' → MISS, applied
        //
        // — the field thrown back eleven characters by a value it had itself sent.
        if (index > 0) pendingEchoes.subList(0, index).clear()
        return
      }
    }
    pendingEchoes.clear()
    if (field.text.toString() == next) return

    val caret = field.selectionEnd
    val wasAtEnd = caret >= field.text.length
    field.setText(next)
    // At the end is where a mask that added characters wants it; anywhere else, the same offset is
    // the closest thing to "where the user was".
    field.setSelection(if (wasAtEnd) next.length else caret.coerceIn(0, next.length))
  }

  private fun bindCard(row: RowSpec, style: RowStyle, tint: Int, disabled: Boolean) {
    labelView.text = row.label.orEmpty()
    labelView.gravity = Gravity.START
    labelView.setTextColor(if (disabled) style.disabledColor else tint)
    FontResolver.apply(labelView, style.font, SECONDARY_SIZE_SP, context)

    cardValueView?.apply {
      text = row.value.orEmpty()
      visibility = if (row.value != null) View.VISIBLE else View.GONE
      setTextColor(if (disabled) style.disabledColor else style.labelColor)
      FontResolver.apply(this, row.font ?: style.font, CARD_VALUE_SIZE_SP, context)
    }
    cardCaptionView?.apply {
      text = row.secondaryLabel.orEmpty()
      visibility = if (row.secondaryLabel != null) View.VISIBLE else View.GONE
      setTextColor(style.secondaryColor)
      FontResolver.apply(this, style.font, SECONDARY_SIZE_SP, context)
    }
    valueView.visibility = View.GONE
  }

  /**
   * `compact` opens a dialog; `inline` and `wheels` degrade to it, loudly.
   *
   * A tappable value that opens `DatePickerDialog` *is* the Android idiom — the platform's own
   * settings screens do exactly this. `inline` would want an always-open calendar, which
   * `android.widget.DatePicker` can draw but which needs a taller cell and its own reuse pool; and
   * **`wheels` has no M3 equivalent at all**, which is a documented degradation rather than an
   * omission. Both warn once so the difference is discoverable from the console rather than from a
   * screenshot comparison.
   */
  private fun bindDatePicker(row: RowSpec, style: RowStyle, tint: Int, disabled: Boolean) {
    val display = dateValueView ?: return
    val millis = row.dateMillis?.toLong() ?: System.currentTimeMillis()
    val mode = row.datePickerMode ?: DatePickerMode.date

    when (row.datePickerStyle) {
      DatePickerStyle.wheels ->
        warnOnce(
          "datePickerStyle:wheels",
          "[@rngui/collection-view] datePickerStyle 'wheels' has no Material 3 equivalent on " +
            "Android — there is no drum picker. That row falls back to a tappable value that " +
            "opens the platform picker.",
        )
      DatePickerStyle.inline ->
        warnOnce(
          "datePickerStyle:inline",
          "[@rngui/collection-view] datePickerStyle 'inline' currently falls back to the " +
            "compact presentation on Android: a tappable value opening the platform picker.",
        )
      else -> Unit
    }

    display.text = formatDate(millis, mode)
    display.setTextColor(if (disabled) style.disabledColor else tint)
    FontResolver.apply(display, row.font ?: style.font, LABEL_SIZE_SP, context)

    display.setOnClickListener(
      if (disabled) null else ({ openPicker(row, millis, mode) })
    )
    display.isClickable = !disabled
  }

  /**
   * Collects whichever components the mode asks for, chaining date into time when both.
   *
   * **`dateAndTime` genuinely needs two dialogs on Android**, and that is a platform difference
   * rather than an omission: iOS's `UIDatePicker` presents a combined date-and-time wheel, and
   * Material has no single component that collects both — the M3 date picker and time picker are
   * separate, and Google's own apps chain them. Reporting after the *first* one, which is what this
   * did, silently threw the time away.
   */
  private fun openPicker(row: RowSpec, millis: Long, mode: DatePickerMode) {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }

    fun pickTime(onDone: () -> Unit) {
      TimePickerDialog(
          context,
          { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            onDone()
          },
          calendar.get(Calendar.HOUR_OF_DAY),
          calendar.get(Calendar.MINUTE),
          android.text.format.DateFormat.is24HourFormat(context),
        )
        .show()
    }

    fun pickDate(onDone: () -> Unit) {
      DatePickerDialog(
          context,
          { _, year, month, day ->
            calendar.set(year, month, day)
            onDone()
          },
          calendar.get(Calendar.YEAR),
          calendar.get(Calendar.MONTH),
          calendar.get(Calendar.DAY_OF_MONTH),
        )
        .apply {
          row.minDateMillis?.let { datePicker.minDate = it.toLong() }
          row.maxDateMillis?.let { datePicker.maxDate = it.toLong() }
        }
        .show()
    }

    val report = { events.onDateChange(boundRowId, calendar.timeInMillis.toDouble()) }

    when (mode) {
      DatePickerMode.time -> pickTime(report)
      DatePickerMode.dateAndTime -> pickDate { pickTime(report) }
      else -> pickDate(report)
    }
  }

  private fun bindMenu(row: RowSpec, style: RowStyle, disabled: Boolean) {
    val items = row.menuItems.orEmpty()
    val selected = items.firstOrNull { it.id == row.selectedItemId }

    valueView.visibility = View.VISIBLE
    valueView.text = selected?.title ?: row.value.orEmpty()
    valueView.setTextColor(if (disabled) style.disabledColor else style.secondaryColor)
    FontResolver.apply(valueView, row.font ?: style.font, LABEL_SIZE_SP, context)

    setOnClickListener(
      if (disabled || items.isEmpty()) {
        null
      } else {
        {
          PopupMenu(context, valueView)
            .apply {
              items.forEachIndexed { index, item ->
                menu.add(0, index, index, item.title).isEnabled = item.disabled != true
              }
              setOnMenuItemClickListener { selection ->
                events.onMenuSelect(boundRowId, items[selection.itemId].id)
                true
              }
            }
            .show()
        }
      }
    )
  }

  // -- mappings -----------------------------------------------------------------------------

  private fun inputTypeFor(row: RowSpec, multiline: Boolean): Int {
    var type =
      when (row.keyboardType) {
        KeyboardType.numeric -> InputType.TYPE_CLASS_NUMBER
        KeyboardType.decimal ->
          InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        KeyboardType.email ->
          InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        KeyboardType.phone -> InputType.TYPE_CLASS_PHONE
        KeyboardType.url -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        else -> InputType.TYPE_CLASS_TEXT
      }

    if (type and InputType.TYPE_CLASS_TEXT != 0) {
      type =
        type or
          when (row.autoCapitalize) {
            AutoCapitalize.words -> InputType.TYPE_TEXT_FLAG_CAP_WORDS
            AutoCapitalize.characters -> InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            AutoCapitalize.none -> 0
            else -> InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
          }
      if (multiline) type = type or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    if (row.secure == true) {
      type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    return type
  }

  private fun imeOptionsFor(returnKey: ReturnKeyType?): Int =
    when (returnKey) {
      ReturnKeyType.done -> EditorInfo.IME_ACTION_DONE
      ReturnKeyType.go -> EditorInfo.IME_ACTION_GO
      ReturnKeyType.next -> EditorInfo.IME_ACTION_NEXT
      ReturnKeyType.search -> EditorInfo.IME_ACTION_SEARCH
      ReturnKeyType.send -> EditorInfo.IME_ACTION_SEND
      else -> EditorInfo.IME_ACTION_UNSPECIFIED
    }

  private fun formatDate(millis: Long, mode: DatePickerMode): String {
    val flags =
      when (mode) {
        DatePickerMode.time -> android.text.format.DateUtils.FORMAT_SHOW_TIME
        DatePickerMode.dateAndTime ->
          android.text.format.DateUtils.FORMAT_SHOW_DATE or
            android.text.format.DateUtils.FORMAT_SHOW_TIME
        else -> android.text.format.DateUtils.FORMAT_SHOW_DATE
      }
    return android.text.format.DateUtils.formatDateTime(context, millis, flags)
  }

  private companion object {
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    const val LABEL_SIZE_SP = 17f
    const val SECONDARY_SIZE_SP = 15f
    const val CARD_VALUE_SIZE_SP = 28f

    /** `systemRed`, matching `ButtonRole.destructive` on iOS. */
    const val DESTRUCTIVE = 0xFFFF3B30.toInt()

    /** How many un-echoed values to remember. Matches the iOS cell's bound. */
    const val MAX_ECHOES = 32

    // An unbounded slider is a fraction, which is what most of them are — and what `UISlider`
    // defaults to, so an unspecified range means the same thing on both platforms.
    const val DEFAULT_SLIDER_MIN = 0.0
    const val DEFAULT_SLIDER_MAX = 1.0

    /**
     * Applied on top of the greyed text colours rather than instead of them.
     *
     * A disabled row has to read as disabled from across the screen, and colour alone does not do
     * that on a themed list where the caller may have set `labelColor` to something already faint.
     */
    const val DISABLED_ALPHA = 0.4f

    private val warned = HashSet<String>()

    private fun warnOnce(key: String, message: String) {
      if (!warned.add(key)) return
      Log.w("rngui", message)
    }
  }
}

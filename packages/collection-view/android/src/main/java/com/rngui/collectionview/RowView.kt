package com.rngui.collectionview

import android.app.DatePickerDialog
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
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

  private val switchView: Switch? =
    if (kind == RowKind.switch) {
      Switch(context).apply {
        layoutParams = LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) }
      }
    } else {
      null
    }

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
        events.onTextChange(boundRowId, s?.toString().orEmpty())
      }

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    }

  private val focusListener =
    OnFocusChangeListener { _, focused -> events.onFocusChange(boundRowId, focused) }

  private val switchListener =
    android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
      events.onSwitchChange(boundRowId, checked)
    }

  init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = context.dp(44)
    setPadding(context.dp(16), context.dp(11), context.dp(16), context.dp(11))
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP)

    addView(iconView)

    when (kind) {
      RowKind.textField,
      RowKind.textArea -> addView(editText)
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
  fun bind(row: RowSpec, style: RowStyle) {
    boundRowId = row.id
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
    isClickable = row.selectable == true && !disabled
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

  private fun bindText(row: RowSpec, style: RowStyle, disabled: Boolean) {
    val field = editText ?: return

    field.removeTextChangedListener(textWatcher)
    field.onFocusChangeListener = null

    val text = row.text.orEmpty()
    // Guarded rather than assigned unconditionally: setting the same text still moves the caret to
    // the end, so a rebind while the user is typing mid-string would jump them to the end of it.
    if (field.text.toString() != text) field.setText(text)
    field.hint = row.placeholder.orEmpty()
    field.isEnabled = !disabled
    field.setTextColor(if (disabled) style.disabledColor else style.labelColor)
    field.setHintTextColor(style.secondaryColor)
    FontResolver.apply(field, row.font ?: style.font, LABEL_SIZE_SP, context)

    val multiline = kind == RowKind.textArea
    field.inputType = inputTypeFor(row, multiline)
    field.imeOptions = imeOptionsFor(row.returnKeyType)
    field.isSingleLine = !multiline
    if (multiline) {
      // Grows to `maxLines` and then scrolls internally, which is what the Reminders notes field
      // does. Unset means it grows without limit.
      field.maxLines = row.maxLines ?: Int.MAX_VALUE
      field.setHorizontallyScrolling(false)
    }

    field.addTextChangedListener(textWatcher)
    field.onFocusChangeListener = focusListener
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
      if (disabled) {
        null
      } else {
        {
          val calendar = Calendar.getInstance().apply { timeInMillis = millis }
          DatePickerDialog(
              context,
              { _, year, month, day ->
                calendar.set(year, month, day)
                events.onDateChange(boundRowId, calendar.timeInMillis.toDouble())
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
      }
    )
    display.isClickable = !disabled
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

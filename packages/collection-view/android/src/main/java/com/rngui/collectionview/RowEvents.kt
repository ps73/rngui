package com.rngui.collectionview

/**
 * What a row can report, addressed by `rowId` rather than by index.
 *
 * Indices shift the moment a row is inserted, and an inline date picker appearing between a switch
 * and its footer shifts every index after it — so an event addressed by index is delivered to the
 * wrong handler exactly when the list is most in flux. `Root` keeps a registry keyed by these ids.
 *
 * One interface handed to every holder rather than six lambdas per row: a 2,000-row list allocates
 * one of these, not twelve thousand.
 */
interface RowEvents {
  fun onRowPress(rowId: String)

  fun onSwitchChange(rowId: String, value: Boolean)

  fun onTextChange(rowId: String, value: String)

  fun onFocusChange(rowId: String, focused: Boolean)

  fun onDateChange(rowId: String, millis: Double)

  fun onMenuSelect(rowId: String, itemId: String)

  fun onSwipeAction(rowId: String, actionId: String)
}

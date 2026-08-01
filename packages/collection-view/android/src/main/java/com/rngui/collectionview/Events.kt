package com.rngui.collectionview

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event

/**
 * Row- and section-scoped events, each carrying an id rather than an index.
 *
 * **The id is the design, not a detail.** Indices shift the moment a row is inserted, and an
 * inline date picker appearing between a switch and its footer shifts every index after it — so an
 * event addressed by index would be delivered to the wrong handler exactly when the list is most
 * in flux. `Root` keeps a registry keyed by the same ids and dispatches, because a function cannot
 * ride inside the serialized tree.
 *
 * One event class per payload shape rather than per event name: the name is what the dispatcher
 * routes on, and `onSwitchChange` and `onTextChange` differ only in the type of `value`.
 */
class RowPressEvent(surfaceId: Int, viewTag: Int, private val rowId: String) :
  Event<RowPressEvent>(surfaceId, viewTag) {
  override fun getEventName() = NAME

  override fun getEventData(): WritableMap =
    Arguments.createMap().apply { putString("rowId", rowId) }

  companion object {
    /**
     * The `topX` form, and it has to be this exactly.
     *
     * React Native's event dispatcher looks the name up in the map returned by
     * `getExportedCustomDirectEventTypeConstants`, whose keys are `topRowPress` and whose
     * `registrationName` is `onRowPress`. Dispatching `onRowPress` directly finds nothing and the
     * event is dropped in silence — no warning, no crash, just a list that ignores taps.
     */
    const val NAME = "topRowPress"
  }
}

/** The one event addressed by *section* rather than by row — a header's trailing button. */
class SectionActionEvent(surfaceId: Int, viewTag: Int, private val sectionId: String) :
  Event<SectionActionEvent>(surfaceId, viewTag) {
  override fun getEventName() = NAME

  override fun getEventData(): WritableMap =
    Arguments.createMap().apply { putString("sectionId", sectionId) }

  companion object {
    const val NAME = "topSectionAction"
  }
}

/** The visible row range, as inclusive indices into the flattened row list. */
class VisibleRangeChangeEvent(
  surfaceId: Int,
  viewTag: Int,
  private val firstIndex: Int,
  private val lastIndex: Int,
) : Event<VisibleRangeChangeEvent>(surfaceId, viewTag) {
  override fun getEventName() = NAME

  override fun getEventData(): WritableMap =
    Arguments.createMap().apply {
      putInt("firstIndex", firstIndex)
      putInt("lastIndex", lastIndex)
    }

  companion object {
    const val NAME = "topVisibleRangeChange"
  }
}

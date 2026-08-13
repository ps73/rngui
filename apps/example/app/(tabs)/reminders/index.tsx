import { useCallback, useState } from 'react'
import { router } from 'expo-router'
import { CollectionView, type MenuItemSpec } from '@rngui/collection-view'

/**
 * Target: the iOS Reminders "New Reminder" sheet.
 *
 * This is the row-kind harness. Every control the library has is on this screen, and three of them
 * are here to prove something specific rather than to look right:
 *
 * 1. **Typing survives a re-render.** Each keystroke round-trips to JavaScript, `setState` runs, the
 *    tree is re-serialized and re-applied — and the field must keep its first responder, its caret
 *    position, and every character. That needs three things: surviving rows are
 *    `reconfigureItems`-ed rather than reloaded; the cell treats an incoming value it has already
 *    sent as an echo instead of an instruction; and a content-only update does not animate.
 * 2. **The inline pickers are ordinary conditional rows.** `{dateOn && dateExpanded && <Row>…}` —
 *    no expansion API, no UIKit-owned outline state. Two booleans, because the real app lets a row
 *    be *on but collapsed*: the switch enables the reminder and tapping the row folds the picker
 *    away without clearing it. Declarative state goes in, an animated insert comes out.
 * 3. **The notes field grows.** A `UITextView` with scrolling off reports its full height, so the
 *    cell self-sizes; the layout is then told to re-measure just that item.
 */
const PRIORITIES: MenuItemSpec[] = [
  { id: 'none', title: 'None' },
  { id: 'low', title: 'Low', systemImage: 'exclamationmark' },
  { id: 'medium', title: 'Medium', systemImage: 'exclamationmark.2' },
  { id: 'high', title: 'High', systemImage: 'exclamationmark.3' },
]

const LISTS = [
  { id: 'reminders', title: 'Reminders' },
  { id: 'shopping', title: 'Shopping' },
  { id: 'work', title: 'Work' },
]

interface Subtask {
  id: string
  title: string
  done: boolean
}

const INITIAL_SUBTASKS: Subtask[] = [
  { id: 'sub-1', title: 'Book the rehearsal room', done: false },
  { id: 'sub-2', title: 'Restring the bass', done: true },
  { id: 'sub-3', title: 'Charge the pedalboard', done: false },
]

/** "Today" reads better than a date when it is today — which is what the real app does. */
function formatDate(millis: number): string {
  const date = new Date(millis)
  const today = new Date()
  const sameDay =
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
  if (sameDay) return 'Today'
  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

function formatTime(millis: number): string {
  return new Date(millis).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function RemindersScreen() {
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [url, setUrl] = useState('')
  const [dateOn, setDateOn] = useState(false)
  const [timeOn, setTimeOn] = useState(false)
  const [urgent, setUrgent] = useState(false)
  // Expansion is tracked separately from the switch, exactly as the real app behaves: turning the
  // switch on opens the picker, but the row itself then collapses and reopens it without ever
  // clearing the value. One boolean could not express "on but collapsed".
  const [dateExpanded, setDateExpanded] = useState(false)
  const [timeExpanded, setTimeExpanded] = useState(false)
  const [due, setDue] = useState(() => Date.now())
  const [priority, setPriority] = useState('none')
  const [flagged, setFlagged] = useState(false)
  const [list, setList] = useState('reminders')
  const [subtasks, setSubtasks] = useState(INITIAL_SUBTASKS)
  const [tail, setTail] = useState('')

  const toggleSubtask = useCallback((id: string, done: boolean) => {
    setSubtasks((current) =>
      current.map((task) => (task.id === id ? { ...task, done } : task))
    )
  }, [])

  const removeSubtask = useCallback((id: string) => {
    setSubtasks((current) => current.filter((task) => task.id !== id))
  }, [])

  const setDate = useCallback((on: boolean) => {
    setDateOn(on)
    setDateExpanded(on)
  }, [])

  const setTime = useCallback((on: boolean) => {
    setTimeOn(on)
    setTimeExpanded(on)
  }, [])

  const reset = useCallback(() => {
    setTitle('')
    setNotes('')
    setUrl('')
    setDateOn(false)
    setTimeOn(false)
    setDateExpanded(false)
    setTimeExpanded(false)
    setUrgent(false)
    setPriority('none')
    setFlagged(false)
    setSubtasks(INITIAL_SUBTASKS)
  }, [])

  return (
    // `keyboardAware` is the whole point of this screen now that it has three text rows: without
    // it, focusing the URL field puts the caret behind the keyboard with no inset and no scroll.
    <CollectionView.Root keyboardAware keyboardAwareOffset={12}>
      <CollectionView.Section id="entry">
        {/*
          A `TextArea` rather than a `TextField`, because the real title wraps to a second line —
          and given a larger font through the row, since that is a property of this row and not of
          the list. The size and weight are the only fields set, so the design and Dynamic Type
          scaling the list established still apply.
        */}
        <CollectionView.Row id="title" font={{ size: 22, weight: 'semibold' }}>
          <CollectionView.TextArea
            placeholder="Title"
            value={title}
            onChangeText={setTitle}
            autoCapitalize="sentences"
          />
        </CollectionView.Row>
        <CollectionView.Row id="notes">
          <CollectionView.TextArea
            placeholder="Notes"
            value={notes}
            onChangeText={setNotes}
          />
        </CollectionView.Row>
        <CollectionView.Row id="url">
          <CollectionView.TextField
            placeholder="URL"
            value={url}
            onChangeText={setUrl}
            keyboardType="url"
            autoCapitalize="none"
          />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        id="details"
        header="Date & Time"
        footer="Mark this reminder as urgent to set an alarm."
      >
        {/*
          Three things at once, and a row that could only do one of them would not be this row:
          a switch that enables the reminder, a tinted second line showing the current value, and
          the row's own `onPress` collapsing the picker without turning the switch off.
        */}
        <CollectionView.Row
          id="date-toggle"
          onPress={dateOn ? () => setDateExpanded((open) => !open) : undefined}
        >
          <CollectionView.Icon systemImage="calendar" />
          <CollectionView.Label>Date</CollectionView.Label>
          {dateOn && (
            <CollectionView.Description tinted>
              {formatDate(due)}
            </CollectionView.Description>
          )}
          <CollectionView.Switch value={dateOn} onValueChange={setDate} />
        </CollectionView.Row>
        {dateOn && dateExpanded && (
          <CollectionView.Row id="date-picker">
            <CollectionView.DatePicker
              mode="date"
              variant="inline"
              value={due}
              onChange={setDue}
            />
          </CollectionView.Row>
        )}

        <CollectionView.Row
          id="time-toggle"
          onPress={timeOn ? () => setTimeExpanded((open) => !open) : undefined}
        >
          <CollectionView.Icon systemImage="clock" />
          <CollectionView.Label>Time</CollectionView.Label>
          {timeOn && (
            <CollectionView.Description tinted>
              {formatTime(due)}
            </CollectionView.Description>
          )}
          <CollectionView.Switch value={timeOn} onValueChange={setTime} />
        </CollectionView.Row>
        {timeOn && timeExpanded && (
          <CollectionView.Row id="time-picker">
            <CollectionView.DatePicker
              mode="time"
              variant="wheels"
              value={due}
              onChange={setDue}
            />
          </CollectionView.Row>
        )}

        <CollectionView.Row id="urgent">
          <CollectionView.Icon systemImage="alarm" />
          <CollectionView.Label>Urgent</CollectionView.Label>
          <CollectionView.Switch value={urgent} onValueChange={setUrgent} />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        id="attributes"
        header="Attributes"
        footer="Priority is a UIButton presenting a UIMenu — what Settings actually uses — so the anchoring, the dismissal and the checkmark on the current item all come from UIKit."
      >
        <CollectionView.Row id="priority">
          <CollectionView.Icon systemImage="exclamationmark.circle" />
          <CollectionView.Label>Priority</CollectionView.Label>
          <CollectionView.Menu
            items={PRIORITIES}
            value={priority}
            onSelect={setPriority}
          />
        </CollectionView.Row>
        <CollectionView.Row id="flag">
          <CollectionView.Icon systemImage="flag" />
          <CollectionView.Label>Flag</CollectionView.Label>
          <CollectionView.Switch value={flagged} onValueChange={setFlagged} />
        </CollectionView.Row>
        <CollectionView.Row id="remind">
          <CollectionView.Label>Remind me</CollectionView.Label>
          <CollectionView.DatePicker
            mode="dateAndTime"
            variant="compact"
            value={due}
            onChange={setDue}
          />
        </CollectionView.Row>
        <CollectionView.Row id="syncing">
          <CollectionView.Label>Syncing</CollectionView.Label>
          <CollectionView.Spinner />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        id="list"
        header="List"
        footer="Radio accessories. UIKit has no exclusivity in a list — clearing the siblings is the caller's job, which is what a controlled component should do anyway."
      >
        {LISTS.map((option) => (
          <CollectionView.Row key={option.id} id={`list-${option.id}`}>
            <CollectionView.Label>{option.title}</CollectionView.Label>
            <CollectionView.Radio
              value={list === option.id}
              onValueChange={() => setList(option.id)}
            />
          </CollectionView.Row>
        ))}
      </CollectionView.Section>

      <CollectionView.Section
        id="subtasks"
        header="Subtasks"
        footer="Swipe a row either way: left for Delete and Flag, right to complete it — or, on a row that is already done, to undo it. Deleting is JavaScript's decision: native reports the tap and springs the row back, and the row leaves on the next commit as an animated diff — so the layout and the data source never disagree about whether it is gone."
      >
        {subtasks.map((task) => (
          <CollectionView.Row key={task.id} id={task.id}>
            <CollectionView.Label>{task.title}</CollectionView.Label>
            <CollectionView.Checkbox
              value={task.done}
              onValueChange={(done) => toggleSubtask(task.id, done)}
            />
            <CollectionView.SwipeActions>
              <CollectionView.SwipeAction
                id="delete"
                title="Delete"
                systemImage="trash"
                style="destructive"
                onPress={() => removeSubtask(task.id)}
              />
              <CollectionView.SwipeAction
                id="flag"
                title="Flag"
                systemImage="flag"
                backgroundColor="#FF9500"
                onPress={() => toggleSubtask(task.id, !task.done)}
              />
            </CollectionView.SwipeActions>
            {/*
              The other edge, which is the one Reminders itself uses for this gesture: a task is
              completed by swiping it *right*. Ids are unique across both groups, not per edge —
              one row has one set of handlers, so a `delete` on each side would keep only one.
            */}
            <CollectionView.SwipeActions edge="leading">
              {/*
                Reversible, and it reads its own row to decide. An action that always wrote `true`
                would still be *offered* on a row that is already complete, and doing nothing is the
                one outcome a swipe should never have.
              */}
              <CollectionView.SwipeAction
                id="complete"
                title={task.done ? 'Undo' : 'Complete'}
                systemImage={
                  task.done ? 'arrow.uturn.backward.circle' : 'checkmark.circle'
                }
                backgroundColor={task.done ? '#8E8E93' : '#34C759'}
                onPress={() => toggleSubtask(task.id, !task.done)}
              />
            </CollectionView.SwipeActions>
          </CollectionView.Row>
        ))}
      </CollectionView.Section>

      <CollectionView.Section
        id="keyboard"
        header="Keyboard"
        footer="This field sits at the very end of a long list on purpose — it is the case keyboardAware exists for. Focusing it while the keyboard is closed puts it straight behind the keyboard, so the list has to inset itself and scroll the caret back into view."
      >
        <CollectionView.Row id="tail">
          <CollectionView.TextField
            placeholder="A field at the end of the list"
            value={tail}
            onChangeText={setTail}
          />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        id="actions"
        footer="The sheet presents this same list inside a UIKit form sheet — the case where keyboard insets measured against the screen rather than the list go wrong."
      >
        <CollectionView.Row
          id="present-sheet"
          onPress={() => router.push('/reminders/new')}
        >
          <CollectionView.Icon systemImage="square.and.pencil" />
          <CollectionView.Label>New Reminder in a sheet</CollectionView.Label>
          <CollectionView.Chevron />
        </CollectionView.Row>
        <CollectionView.Row id="reset">
          <CollectionView.Button role="destructive" onPress={reset}>
            Delete Reminder
          </CollectionView.Button>
        </CollectionView.Row>
      </CollectionView.Section>
    </CollectionView.Root>
  )
}

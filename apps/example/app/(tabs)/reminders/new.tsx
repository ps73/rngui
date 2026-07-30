import { useState } from 'react'
import { router } from 'expo-router'
import { CollectionView } from '@rngui/collection-view'

/**
 * The same list, in a **form sheet** — which is the one presentation where keyboard handling is
 * most likely to be quietly wrong.
 *
 * A sheet does not reach the bottom of the screen, so the naive overlap calculation
 * (`screenHeight - keyboardHeight`) over-reports by exactly the gap beneath the sheet. The list
 * then insets itself too far and the content jumps above the keyboard with a visible band of dead
 * space. `KeyboardObserver` measures the keyboard's end frame against *this list's* own bounds in
 * window space instead, so the answer is the same in a sheet, a full screen, or a bottom sheet.
 *
 * The fields are deliberately stacked so the last one sits below the keyboard line: focusing it is
 * the case that exercises both halves at once — the inset growing and the caret being scrolled
 * back into view.
 */
export default function NewReminderSheet() {
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [url, setUrl] = useState('')
  const [location, setLocation] = useState('')
  const [tag, setTag] = useState('')

  return (
    <CollectionView.Root keyboardAware keyboardAwareOffset={12}>
      <CollectionView.Section id="entry">
        <CollectionView.Row id="title" font={{ size: 22, weight: 'semibold' }}>
          <CollectionView.TextArea
            placeholder="Title"
            value={title}
            onChangeText={setTitle}
          />
        </CollectionView.Row>
        <CollectionView.Row id="notes">
          <CollectionView.TextArea
            placeholder="Notes"
            value={notes}
            onChangeText={setNotes}
          />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        id="more"
        header="Details"
        footer="Each field is further down than the last. In a form sheet the bottom of this list is nowhere near the bottom of the screen, so an overlap measured against the screen would inset the list by far too much."
      >
        <CollectionView.Row id="url">
          <CollectionView.TextField
            placeholder="URL"
            value={url}
            onChangeText={setUrl}
            keyboardType="url"
            autoCapitalize="none"
          />
        </CollectionView.Row>
        <CollectionView.Row id="location">
          <CollectionView.TextField
            placeholder="Location"
            value={location}
            onChangeText={setLocation}
          />
        </CollectionView.Row>
        <CollectionView.Row id="tag">
          <CollectionView.TextField
            placeholder="Tag"
            value={tag}
            onChangeText={setTag}
            returnKeyType="done"
          />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section id="actions">
        <CollectionView.Row id="done">
          <CollectionView.Button onPress={() => router.back()}>
            Done
          </CollectionView.Button>
        </CollectionView.Row>
      </CollectionView.Section>
    </CollectionView.Root>
  )
}

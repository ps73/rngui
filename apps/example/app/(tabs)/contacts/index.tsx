import { useMemo, useRef } from 'react'
import { CollectionView, type VisibleRange } from '@rngui/collection-view'
import { buildContacts } from '../../../src/data/contacts'

/**
 * Target: the iOS Contacts app — and the performance harness for the whole library.
 *
 * Three things are being proven here rather than demonstrated:
 *
 * 1. **Recycling.** 2,000 rows, all of them stock cells, so UIKit pools them. Nothing about this
 *    screen is virtualised in JavaScript; the entire tree crosses once and native owns the rest.
 * 2. **The transport assumption.** The section/row tree crosses as one JSON string, and the whole
 *    reason for that is a measurement — typed codegen props would mean hundreds of thousands of
 *    hash lookups per commit at this size. Native prints the decode and apply cost in debug
 *    builds whenever a tree update crosses half a 120Hz frame, so the assumption reports when it
 *    stops holding.
 * 3. **The `plain` appearance**, with headers that pin to the top of the viewport, and the A–Z
 *    scrubber — which has no UIKit equivalent at all, since `sectionIndexTitles` is a
 *    `UITableView` API.
 */
const CONTACT_COUNT = 2000

export default function ContactsScreen() {
  // Built once. The point of this screen is that 2,000 rows cost nothing *after* the first
  // commit, which is only true if the data behind them is not rebuilt on every render.
  const sections = useMemo(() => buildContacts(CONTACT_COUNT), [])

  const visibleRange = useRef<VisibleRange>({ firstIndex: -1, lastIndex: -1 })

  return (
    <CollectionView.Root
      listAppearance="plain"
      // 13pt matches the system control's compact metric; the block is centred rather than
      // stretched, which is what keeps it reading as one object on a tall screen.
      sectionIndex={{ rowHeight: 13 }}
      // Off because the scrubber already owns this edge — two rails on the same strip read as a
      // mistake even when they are inset apart.
      showsVerticalScrollIndicator={false}
      appearance={{ headerBackgroundStyle: 'blurred' }}
      // Wired here because 2,000 rows is where the tracking either costs something or doesn't.
      // Note what it deliberately does *not* do: drive state. Rendering from this would mean a
      // React render per scroll frame, which is precisely the cost the native side exists to
      // avoid. It is a window hint for `Host` children, not a scroll position to bind to.
      onVisibleRangeChange={(range) => {
        visibleRange.current = range
      }}
    >
      {sections.map((section) => (
        <CollectionView.Section
          key={section.letter}
          id={section.letter}
          header={section.letter}
          indexTitle={section.letter}
        >
          {section.contacts.map((contact) => (
            <CollectionView.Row key={contact.id} id={contact.id} onPress={noop}>
              <CollectionView.Label>{contact.name}</CollectionView.Label>
            </CollectionView.Row>
          ))}
        </CollectionView.Section>
      ))}
    </CollectionView.Root>
  )
}

/**
 * Shared by all 2,000 rows rather than written inline.
 *
 * A fresh arrow per row would allocate 2,000 closures on every render for no benefit — rows need
 * *a* handler to be selectable, not distinct ones. Which row was tapped comes back as its id.
 */
function noop() {}

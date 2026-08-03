import { useMemo, useRef } from 'react'
import { CollectionView, type VisibleRange } from '@rngui/collection-view'
import { buildContacts } from '../data/contacts'

/**
 * Google Contacts, rebuilt — the Android counterpart to `ContactsIOS`, and the same 2,000-row
 * recycling harness underneath.
 *
 * **Three things differ from the iOS screen, and each is a platform's own answer rather than a
 * translation of the other's:**
 *
 * - **Monogram avatars in colour.** Google draws a filled circle with the person's initials; iOS
 *   Contacts draws a grey person glyph. The circle is not decoration — it is what the eye tracks
 *   down a list of two thousand names, and a grey glyph repeated two thousand times is not.
 * - **No swipe-to-delete.** On iOS a swipe on a list row means "act on this row", and Contacts is
 *   the canonical example. Material assigns the same gesture to *dismiss*, and Google Contacts has
 *   no swipe at all — selection is long-press. Porting the swipe would have been the easy thing and
 *   the wrong one. The API is still exercised on Android: Reminders' subtasks swipe.
 * - **The scrubber is already different, and for free.** `sectionIndex` renders iOS's A–Z rail and
 *   Android's dragging thumb-with-bubble from one prop, because native decides — so this screen
 *   asks for the same thing the iOS one does and gets the local control.
 *
 * What is deliberately *unchanged* is everything about the data path: one tree, crossing once, 2,000
 * stock rows that native pools. That was never an iOS design.
 */
const CONTACT_COUNT = 2000

/**
 * Google's avatar palette, near enough.
 *
 * **Fixed hexes rather than theme colours, and mid-tone on purpose.** These cross as static
 * per-row props, so unlike an `appearance` field they cannot re-resolve when the device changes
 * mode — and reading them from the unistyles theme would mean re-rendering 2,000 rows on every
 * appearance change, which is the one thing this screen exists to avoid. Mid-tones with white
 * letters on them clear the contrast bar against both the light and the dark list surface, which
 * is what makes one fixed value per contact acceptable here.
 */
const AVATAR_COLORS = [
  '#B4462F',
  '#8B5000',
  '#4C6B2F',
  '#00696D',
  '#3F5F90',
  '#5B4A9E',
  '#8A3F6B',
  '#7A4C2E',
] as const

export default function ContactsScreenAndroid() {
  // Built once, and the avatars derived with it. Deriving initials and a colour per render would
  // put 2,000 string operations on every commit, which is exactly the cost this screen measures.
  const sections = useMemo(() => {
    return buildContacts(CONTACT_COUNT).map((section) => ({
      letter: section.letter,
      contacts: section.contacts.map((contact) => ({
        ...contact,
        monogram: initial(contact.name),
        color: AVATAR_COLORS[hash(contact.id) % AVATAR_COLORS.length],
      })),
    }))
  }, [])

  const visibleRange = useRef<VisibleRange>({ firstIndex: -1, lastIndex: -1 })

  return (
    <CollectionView.Root
      listAppearance="plain"
      sectionIndex
      // Off because the fast-scroll thumb already owns this edge, and two rails on the same strip
      // read as a mistake on either platform.
      showsVerticalScrollIndicator={false}
      // The pinned letter travels over the rows with nothing behind it, which is what Contacts does
      // on both platforms — legible here only because every row leads with a filled circle, so the
      // letter passes over a graphic rather than over a name.
      appearance={{ headerBackgroundStyle: 'transparent' }}
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
            <CollectionView.Row
              key={contact.id}
              id={contact.id}
              onPress={noop}
              // M3's one-line list item with a 40dp leading avatar.
              height={56}
            >
              <CollectionView.Icon
                monogram={contact.monogram}
                background={contact.color}
              />
              <CollectionView.Label>{contact.name}</CollectionView.Label>
            </CollectionView.Row>
          ))}
        </CollectionView.Section>
      ))}
    </CollectionView.Root>
  )
}

/**
 * One letter, which is what Google Contacts draws.
 *
 * **Not two.** A two-letter monogram is the Outlook and Teams convention and it is what most people
 * reach for by reflex — `imageMonogram` takes up to two for exactly that reason. Google's address
 * book has always shown a single initial, and at a 40dp circle the difference is the one between a
 * mark you read at a glance and a small word you have to focus on.
 */
function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase()
}

/**
 * A stable colour per contact.
 *
 * Hashed from the id rather than from the name so that two people called the same thing still get
 * different circles — which is the case the colour is most useful in.
 */
function hash(id: string): number {
  let value = 0
  for (let i = 0; i < id.length; i += 1) {
    value = (value * 31 + id.charCodeAt(i)) | 0
  }
  return Math.abs(value)
}

/** Shared by all 2,000 rows; a fresh closure each would allocate 2,000 per render for nothing. */
function noop() {}

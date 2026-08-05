import { useMemo, useRef, useState } from 'react'
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
 * - **Swipe-to-delete, against the local idiom and on purpose.** Google Contacts has no swipe —
 *   Material assigns that gesture to *dismiss*, and selection there is long-press. This screen had
 *   none for that reason, and now has it because the demo is worth more than the purity: swipe is
 *   part of the public API and a 2,000-row list is where it is worth seeing. The README's platform
 *   table still says what the idiomatic answer would be.
 *
 *   **It also demonstrates a real limit.** Android hands out only 200dp per edge of
 *   `systemGestureExclusionRects`, so on a list where *every* row is swipeable the rows past the
 *   first few still lose a near-edge drag to the system back gesture. Start the swipe away from the
 *   edge and it works; that is the platform's budget, not this library's choice.
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
  /**
   * Deleting is JavaScript's decision, exactly as on iOS.
   *
   * Native reports the tap and springs the row back; the row leaves on the *next* commit as an
   * animated diff, so the layout and the data source never disagree about whether it is gone.
   */
  const [deleted, setDeleted] = useState<ReadonlySet<string>>(() => new Set())

  // Built once, and the avatars derived with it. Deriving initials and a colour per render would
  // put 2,000 string operations on every commit, which is exactly the cost this screen measures.
  const built = useMemo(() => {
    return buildContacts(CONTACT_COUNT).map((section) => ({
      letter: section.letter,
      contacts: section.contacts.map((contact) => ({
        ...contact,
        monogram: initial(contact.name),
        color: AVATAR_COLORS[hash(contact.id) % AVATAR_COLORS.length],
      })),
    }))
  }, [])

  const sections = useMemo(() => {
    if (deleted.size === 0) return built
    return (
      built
        .map((section) => ({
          ...section,
          contacts: section.contacts.filter((c) => !deleted.has(c.id)),
        }))
        // A section that loses every contact loses its letter too, and with it its stop on the
        // fast scroller — which would otherwise scroll to a header with nothing under it.
        .filter((section) => section.contacts.length > 0)
    )
  }, [built, deleted])

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

              <CollectionView.SwipeActions>
                <CollectionView.SwipeAction
                  id="delete"
                  title="Delete"
                  systemImage="trash"
                  style="destructive"
                  onPress={() =>
                    setDeleted((previous) => new Set(previous).add(contact.id))
                  }
                />
              </CollectionView.SwipeActions>
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

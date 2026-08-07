import { useCallback, useMemo, useRef, useState } from 'react'
import { RefreshControl } from 'react-native'
import { CollectionView, type VisibleRange } from '@rngui/collection-view'
import { buildContacts } from '../data/contacts'

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

/**
 * A flat grey, not a theme colour.
 *
 * This crosses as one static hex on a per-row prop, so it cannot adapt to dark mode the way an
 * `appearance` field does — and reading it from the unistyles theme would mean re-rendering 2,000
 * rows on every appearance change, which is the one thing this screen exists to avoid. The system
 * grey happens to be the same value in both modes, so nothing is lost by fixing it.
 */
const AVATAR_COLOR = '#8E8E93'

export default function ContactsScreenIOS() {
  // Built once. The point of this screen is that 2,000 rows cost nothing *after* the first
  // commit, which is only true if the data behind them is not rebuilt on every render.
  const built = useMemo(() => buildContacts(CONTACT_COUNT), [])

  /**
   * Deleting is JavaScript's decision, which is the whole point of the swipe demo.
   *
   * Native reports the tap and springs the row back; the row leaves on the *next* commit as an
   * animated diff, so the layout and the data source never disagree about whether it is gone. A
   * list that removed the row natively would be a list whose native state had to be reconciled
   * afterwards, which is the bug this design avoids by construction.
   */
  const [deleted, setDeleted] = useState<ReadonlySet<string>>(() => new Set())

  const sections = useMemo(() => {
    if (deleted.size === 0) return built
    return (
      built
        .map((section) => ({
          ...section,
          contacts: section.contacts.filter(
            (contact) => !deleted.has(contact.id)
          ),
        }))
        // A section that loses every contact loses its letter too — including its stop on the
        // scrubber, which would otherwise scroll to a header with nothing under it.
        .filter((section) => section.contacts.length > 0)
    )
  }, [built, deleted])

  const visibleRange = useRef<VisibleRange>({ firstIndex: -1, lastIndex: -1 })

  /**
   * Pull to refresh, and the point of it here is the *controlled* half.
   *
   * `refreshing` is state, not something native owns: the spinner stays up for exactly as long as
   * this says so, and a caller who never set it would see it stop on the next render. The timer
   * stands in for a fetch — without one the round trip is too fast to see whether the contract
   * holds at all.
   */
  const [refreshing, setRefreshing] = useState(false)
  const onRefresh = useCallback(() => {
    setRefreshing(true)
    setTimeout(() => {
      // Deleting is the only thing this screen changes, so undeleting is the only honest thing a
      // refresh can restore.
      setDeleted(new Set())
      setRefreshing(false)
    }, 1200)
  }, [])

  return (
    <CollectionView.Root
      listAppearance="plain"
      // The element `ScrollView` takes, read rather than mounted — `tintColor` and `title` are the
      // iOS half of `RefreshControl`, and this is where they are exercised.
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={onRefresh}
          tintColor={AVATAR_COLOR}
          title="Updating contacts…"
          titleColor={AVATAR_COLOR}
        />
      }
      // 13pt matches the system control's compact metric; the block is centred rather than
      // stretched, which is what keeps it reading as one object on a tall screen.
      sectionIndex={{ rowHeight: 13 }}
      // Off because the scrubber already owns this edge — two rails on the same strip read as a
      // mistake even when they are inset apart.
      showsVerticalScrollIndicator={false}
      // No background at all, which is what the real Contacts app does: the letter is a small grey
      // glyph floating over the rows, and the only thing softening the top of the screen is the
      // navigation bar's own scroll edge effect. A material here — hard-edged or faded — is one
      // surface too many, and it shows up as a second edge travelling down the screen.
      //
      // Legible only because each row leads with an avatar, so the pinned letter overlaps a graphic
      // rather than a name. Take the icons away and this needs a background again.
      appearance={{ headerBackgroundStyle: 'transparent' }}
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
            <CollectionView.Row
              key={contact.id}
              id={contact.id}
              onPress={noop}
              // Taller than a default row, as Contacts is, and enough for the avatar to sit in.
              height={56}
            >
              {/*
                The no-photo state, which is what Contacts shows for most people. Not decoration:
                the pinned header has no background, so the letter travelling down the screen needs
                a graphic to pass over rather than a name.
              */}
              <CollectionView.Icon
                systemImage="person.crop.circle.fill"
                color={AVATAR_COLOR}
                size={38}
              />
              <CollectionView.Label>{contact.name}</CollectionView.Label>

              {/*
                Swipe-to-delete, which is what a contacts list is *for* on iOS — and on Android is
                deliberately off-idiom: Material says a swipe means dismiss, and an Android-first
                design would reach for an overflow menu. Demonstrated on both because the API is
                shared, and the README says which one is the local idiom.
              */}
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
 * Shared by all 2,000 rows rather than written inline.
 *
 * A fresh arrow per row would allocate 2,000 closures on every render for no benefit — rows need
 * *a* handler to be selectable, not distinct ones. Which row was tapped comes back as its id.
 */
function noop() {}

/**
 * A deterministic address book, big enough to be a real recycling test.
 *
 * Deterministic on purpose — `Math.random()` would mean the list changed between reloads, and
 * "did that row look wrong or is it just a different row" is not a question worth having while
 * profiling. The same seed gives the same 2,000 people every launch.
 */

const FIRST_NAMES = [
  'Aiden',
  'Alba',
  'Amara',
  'Anders',
  'Anouk',
  'Arjun',
  'Astrid',
  'Beatriz',
  'Bram',
  'Camille',
  'Cato',
  'Cecilia',
  'Cillian',
  'Dario',
  'Delphine',
  'Dmitri',
  'Eero',
  'Elif',
  'Elowen',
  'Emeka',
  'Esther',
  'Fabien',
  'Farida',
  'Finnian',
  'Freya',
  'Gabriel',
  'Giulia',
  'Hana',
  'Hugo',
  'Ilse',
  'Imani',
  'Ines',
  'Iris',
  'Jasper',
  'Johan',
  'Juno',
  'Kaia',
  'Kenji',
  'Klara',
  'Lars',
  'Leila',
  'Lorenzo',
  'Lucia',
  'Maarten',
  'Mateo',
  'Maya',
  'Milena',
  'Nadia',
  'Niels',
  'Noor',
  'Oskar',
  'Paloma',
  'Pieter',
  'Priya',
  'Rafael',
  'Rosa',
  'Sanne',
  'Sigrid',
  'Soren',
  'Tariq',
  'Theo',
  'Uma',
  'Valentina',
  'Viggo',
  'Wren',
  'Xavier',
  'Yara',
  'Yusuf',
  'Zara',
  'Zoltan',
] as const

const LAST_NAMES = [
  'Abadi',
  'Andersen',
  'Arnaud',
  'Bakker',
  'Beaumont',
  'Bergström',
  'Blom',
  'Bonnet',
  'Cardoso',
  'Castellano',
  'Chalthoum',
  'Costa',
  'Dahl',
  'Delacroix',
  'Demir',
  'Dubois',
  'Eriksen',
  'Espinoza',
  'Fabre',
  'Ferrari',
  'Fischer',
  'Fontaine',
  'Gallo',
  'García',
  'Gerber',
  'Gruber',
  'Haugen',
  'Hendriks',
  'Holm',
  'Ibarra',
  'Iversen',
  'Jansen',
  'Jelinek',
  'Joubert',
  'Kaufmann',
  'Keller',
  'Kowalski',
  'Kruger',
  'Laurent',
  'Lindqvist',
  'Lombardi',
  'Marchetti',
  'Mendes',
  'Moreau',
  'Nakamura',
  'Navarro',
  'Nielsen',
  'Novak',
  'Okafor',
  'Olsen',
  'Ortega',
  'Pavlović',
  'Pereira',
  'Petit',
  'Quintero',
  'Rasmussen',
  'Ricci',
  'Rousseau',
  'Ruiz',
  'Sandoval',
  'Schneider',
  'Serrano',
  'Silva',
  'Sorensen',
  'Steiner',
  'Tanaka',
  'Thibault',
  'Toledo',
  'Ueda',
  'Vandenberg',
  'Varga',
  'Vasquez',
  'Verhoeven',
  'Vogel',
  'Weber',
  'Wieczorek',
  'Wright',
  'Yılmaz',
  'Zabala',
  'Zielinski',
  'Öztürk',
] as const

export interface Contact {
  id: string
  name: string
}

export interface ContactSection {
  /** The letter, used as both the sticky header and the scrubber stop. */
  letter: string
  contacts: Contact[]
}

/**
 * Builds `count` contacts, grouped and sorted the way Contacts groups them.
 *
 * The two prime-ish strides are what keep first and last names from marching in lockstep — a
 * plain `i % length` on both would repeat the same pairing every 70 entries and produce far
 * fewer distinct names than rows.
 */
export function buildContacts(count: number): ContactSection[] {
  const people: Contact[] = Array.from({ length: count }, (_, i) => {
    const first = FIRST_NAMES[(i * 17) % FIRST_NAMES.length]
    const last = LAST_NAMES[(i * 23) % LAST_NAMES.length]
    return {
      // Indexed rather than derived from the name. Names repeat at this size, and a duplicate row
      // id is dropped *silently* by the diffable data source — rows would simply be missing.
      id: `c${i}`,
      name: `${first} ${last}`,
    }
  })

  people.sort((a, b) => collate(a, b))

  const sections: ContactSection[] = []
  for (const person of people) {
    const letter = indexLetter(person.name)
    const current = sections[sections.length - 1]
    if (current?.letter === letter) current.contacts.push(person)
    else sections.push({ letter, contacts: [person] })
  }
  return sections
}

/**
 * Sorts by last name, then first — and does it with `localeCompare`, because half of these names
 * carry diacritics. A plain `<` sorts by code point, which puts `Öztürk` after `Zielinski` and
 * then the `Ö` section appears at the bottom of an otherwise alphabetical list.
 */
function collate(a: Contact, b: Contact): number {
  const byLast = lastName(a.name).localeCompare(lastName(b.name))
  return byLast !== 0 ? byLast : a.name.localeCompare(b.name)
}

function lastName(name: string): string {
  return name.slice(name.indexOf(' ') + 1)
}

/**
 * The section letter for a name, folded to plain ASCII.
 *
 * `Öztürk` belongs under `O` and `Yılmaz` under `Y`, which is what the system does — an `Ö`
 * section of its own would be a scrubber stop nobody is looking for. Anything that still is not
 * a letter after folding goes under `#`, as it does in Contacts.
 */
function indexLetter(name: string): string {
  const initial = lastName(name)
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .charAt(0)
    .toUpperCase()
  return /^[A-Z]$/.test(initial) ? initial : '#'
}

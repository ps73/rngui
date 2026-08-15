import { useState } from 'react'
import { View } from 'react-native'
import { CollectionView } from '@rngui/collection-view'
import { StyleSheet } from 'react-native-unistyles'

/**
 * Target: the Apple Health "Mental Wellbeing" screen.
 *
 * The screen where compositional layout earns its keep, and where the two ways of putting rich
 * content in a list sit side by side so the trade is visible:
 *
 * - **`Card` rows recycle.** They are described by a `RowSpec`, so the eight here cost however many
 *   cells fit on screen, not eight. Anything that repeats should be one of these.
 * - **The `Host` row does not.** It is a real React subtree, which is the right answer exactly once
 *   — for the chart, which is genuinely one of a kind.
 *
 * The chip strip is the thing a `UITableView` simply cannot do: a horizontally scrolling section
 * *inside* a vertical list, with its own reuse pool.
 */
const RANGES = [
  { id: 'day', title: 'Day', systemImage: 'sun.max' },
  { id: 'week', title: 'Week', systemImage: 'calendar' },
  { id: 'month', title: 'Month', systemImage: 'calendar.badge.clock' },
  { id: 'sixmonths', title: '6 Months' },
  { id: 'year', title: 'Year' },
]

const TEAL = '#0FA3A3'

export default function HealthScreen() {
  const [range, setRange] = useState('week')
  // Drives the header button's own title, which is the point: it proves the tap made the round trip
  // out to native and back rather than the button merely being drawn.
  const [showAll, setShowAll] = useState(false)
  // The measurements below. Plain strings: the unit is drawn by the row, so these hold only what
  // the user typed.
  const [height, setHeight] = useState('187')
  const [weight, setWeight] = useState('86,4')
  const [bloodType, setBloodType] = useState('O+')
  const [restingHR, setRestingHR] = useState('58')

  return (
    <CollectionView.Root
      // The measurements section is editable, so the list has to get out of the keyboard's way —
      // and doing it here shows it composing with a chip strip and a Host row above.
      keyboardAware
      appearance={{
        tintColor: TEAL,
        headerTextColor: TEAL,
        // A wash rather than a flat fill — the case a single `background` colour cannot express.
        // Drawn into the collection view's backgroundView as one CAGradientLayer.
        backgroundGradient: {
          colors: ['#D8F3F1', '#F2F2F7'],
          locations: [0, 0.55],
        },
      }}
      darkAppearance={{
        tintColor: '#5AC8C8',
        headerTextColor: '#5AC8C8',
        backgroundGradient: {
          colors: ['#0B3A3A', '#000000'],
          locations: [0, 0.55],
        },
      }}
    >
      <CollectionView.Section id="range" layout="chips">
        {RANGES.map((option) => (
          <CollectionView.Row
            key={option.id}
            id={`range-${option.id}`}
            onPress={() => setRange(option.id)}
          >
            {option.systemImage != null && (
              <CollectionView.Icon systemImage={option.systemImage} />
            )}
            <CollectionView.Label>{option.title}</CollectionView.Label>
            <CollectionView.Checkbox value={range === option.id} />
          </CollectionView.Row>
        ))}
      </CollectionView.Section>

      <CollectionView.Section
        id="highlights"
        header="Highlights"
        // The "Show All" beside a section title, which Health puts on every summary group. A
        // `UIButton` in the header's own trailing accessory slot, so it lines up with the
        // disclosure chevrons in the rows below it rather than being placed by hand.
        action={{
          title: showAll ? 'Show Less' : 'Show All',
          onPress: () => setShowAll((current) => !current),
        }}
        footer="Every card here is a recycled cell described by a RowSpec — not a React subtree. That is the difference between eight cards and eight live component trees."
      >
        <CollectionView.Row id="state-of-mind">
          <CollectionView.Card
            systemImage="brain.head.profile"
            value="Slightly Pleasant"
            caption="Your mood has trended more pleasant this week than last."
          >
            State of Mind
          </CollectionView.Card>
        </CollectionView.Row>
        <CollectionView.Row id="mindful">
          <CollectionView.Card
            systemImage="figure.mind.and.body"
            value="42 min"
            caption="Mindful minutes, up 12 from last week."
          >
            Mindful Minutes
          </CollectionView.Card>
        </CollectionView.Row>
        {showAll && (
          <CollectionView.Row id="daylight">
            <CollectionView.Card
              systemImage="sun.horizon"
              value="1 h 18 min"
              caption="Daily average time in daylight."
            >
              Time in Daylight
            </CollectionView.Card>
          </CollectionView.Row>
        )}
      </CollectionView.Section>

      <CollectionView.Section
        id="chart"
        header="This Week"
        footer="A Host row: a real React Native view reparented into the cell. Correct for a chart, which is one of a kind — and the wrong tool for the cards above, which repeat."
      >
        {/*
          `background="card"` because the chart draws no surface of its own, so without it the row
          sits on the page background while its section header and footer describe a card that is
          not there. The opposite case is the chip strips on the Custom screen, which are designed
          to float — hence the opt-in rather than a default.
        */}
        <CollectionView.Host id="chart-host" height={160} background="card">
          <Chart />
        </CollectionView.Host>
      </CollectionView.Section>

      <CollectionView.Section
        id="measurements"
        header="Body Measurements"
        footer="A unit is drawn beside the field, never typed into it: what onChangeText reports is only ever what the user entered. The last row has no label, which is what right-aligns a field that would otherwise fill the row — a suffix has to touch its value."
      >
        <CollectionView.Row id="height">
          <CollectionView.Label>Height</CollectionView.Label>
          <CollectionView.TextField
            value={height}
            onChangeText={setHeight}
            keyboardType="decimal"
            placeholder="—"
            unit="cm"
          />
        </CollectionView.Row>
        <CollectionView.Row id="weight">
          <CollectionView.Label>Weight</CollectionView.Label>
          <CollectionView.TextField
            value={weight}
            onChangeText={setWeight}
            keyboardType="decimal"
            placeholder="—"
            unit="kg"
          />
        </CollectionView.Row>
        {/* No unit — the path that existed before this feature, unchanged. */}
        <CollectionView.Row id="blood-type">
          <CollectionView.Label>Blood Type</CollectionView.Label>
          <CollectionView.TextField
            value={bloodType}
            onChangeText={setBloodType}
            autoCapitalize="characters"
            placeholder="—"
          />
        </CollectionView.Row>
        {/* A unit with no label: the field still right-aligns so the two stay together. */}
        <CollectionView.Row id="resting-hr">
          <CollectionView.TextField
            value={restingHR}
            onChangeText={setRestingHR}
            keyboardType="numeric"
            placeholder="Resting heart rate"
            unit="bpm"
          />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        id="more"
        header="More"
        // The symbol form of the same thing, and disabled — so the screen proves the button keeps
        // its own dimmed state rather than the list having to fake one.
        action={{
          systemImage: 'ellipsis.circle',
          disabled: true,
          onPress: () => {},
        }}
      >
        <CollectionView.Row id="symptoms" onPress={() => {}}>
          <CollectionView.Icon systemImage="list.bullet.clipboard" />
          <CollectionView.Label>Symptoms</CollectionView.Label>
          <CollectionView.Chevron />
        </CollectionView.Row>
        <CollectionView.Row id="articles" onPress={() => {}}>
          <CollectionView.Icon systemImage="book" />
          <CollectionView.Label>Articles</CollectionView.Label>
          <CollectionView.Chevron />
        </CollectionView.Row>
      </CollectionView.Section>
    </CollectionView.Root>
  )
}

/**
 * A deliberately plain bar chart — plain React Native views.
 *
 * The point is not the chart but where it lives: reparented into a `UICollectionViewCell`, clipped
 * and scrolled by UIKit, with no floating overlay and no per-frame repositioning.
 */
const BARS = [0.35, 0.6, 0.45, 0.8, 0.55, 0.9, 0.7]

function Chart() {
  return (
    <View style={styles.chart}>
      {BARS.map((height, index) => (
        <View
          key={index}
          style={[styles.bar, { height: `${height * 100}%` }]}
        />
      ))}
    </View>
  )
}

const styles = StyleSheet.create({
  chart: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 16,
  },
  bar: {
    flex: 1,
    marginHorizontal: 4,
    borderRadius: 6,
    backgroundColor: TEAL,
  },
})

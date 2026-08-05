import { useState } from 'react'
import { Platform, Pressable, Text, View } from 'react-native'
import { router } from 'expo-router'
import {
  CollectionView,
  type ColorScheme,
  type FontFamily,
} from '@rngui/collection-view'
import { StyleSheet } from 'react-native-unistyles'
import { INTER_FAMILY } from '../../../src/fonts'

const SCHEMES: readonly ColorScheme[] = ['system', 'light', 'dark']

/**
 * Four generic names and one bundled face, in one list — because `family` takes both.
 *
 * The generic names are the ones React Native's `<Text>` already understands, so the chip labels
 * are literally the values you would type into either component.
 */
const FAMILIES: readonly FontFamily[] = [
  'system-ui',
  'ui-serif',
  'ui-rounded',
  'ui-monospace',
  INTER_FAMILY,
]
/** The two arrangements https://m3.material.io/components/lists/specs defines. */
const ANDROID_LIST_STYLES = ['standard', 'segmented'] as const

const VARIANTS = ['grouped', 'inverted'] as const

/**
 * Points on Inter's `wght` axis, including two no static family could offer.
 *
 * `350` and `550` are the entire argument for variable fonts: `FontSpec.weight` can only reach the
 * nine named weights, because those are the only files a static family ships. An axis is
 * continuous, so these are as real as `400`.
 */
const WEIGHT_AXIS = [300, 350, 400, 550, 700, 900] as const

/**
 * The theming playground, and eventually the custom-components screen.
 *
 * Every control here is a real React Native view inside a `CollectionView.Host` row — which is
 * the point twice over: it exercises hosted children, and it means the list is restyled by
 * ordinary React state rather than by anything special.
 *
 * What to look for when the interface style flips (Cmd+Shift+A in the simulator): the rows
 * restyle with **no JavaScript render at all**. Colours cross as a light/dark pair and become
 * `UIColor(dynamicProvider:)` natively, so UIKit re-resolves them itself.
 */
export default function CustomScreen() {
  const [colorScheme, setColorScheme] = useState<ColorScheme>('system')
  const [family, setFamily] = useState<FontFamily>('ui-rounded')
  const [variant, setVariant] = useState<(typeof VARIANTS)[number]>('grouped')
  const [listStyle, setListStyle] =
    useState<(typeof ANDROID_LIST_STYLES)[number]>('segmented')
  const [expanded, setExpanded] = useState(false)
  const [weightAxis, setWeightAxis] =
    useState<(typeof WEIGHT_AXIS)[number]>(400)
  const [brightness, setBrightness] = useState(0.4)
  const [volume, setVolume] = useState(7)

  /**
   * One spec, shared by rows, headers and footers.
   *
   * One field decides the face, whether that face is a generic name or a bundled one — the same
   * shape `<Text style={{ fontFamily }}>` has. The axis only rides along for the variable font,
   * because a generic name has no axes to drive.
   */
  const font =
    family === INTER_FAMILY
      ? { family, variations: `wght=${weightAxis}` }
      : { family }

  return (
    <CollectionView.Root
      colorScheme={colorScheme}
      androidListStyle={listStyle}
      inverted={variant === 'inverted'}
      appearance={{
        tintColor: '#AF52DE',
        headerTextColor: '#AF52DE',
        font,
        // The header's own weight is layered *over* the shared spec, field by field — so a header
        // stays heavier than its rows whichever face is selected. With Inter that means the axis
        // and the named weight are both in play, and the named one wins because it resolves the
        // face while the axis only varies it.
        headerFont: { ...font, weight: 'semibold' },
        footerFont: font,
      }}
      // Only the tint differs in dark mode. Everything else falls back to the light values field
      // by field, which is why setting one colour here does not mean restating the rest.
      darkAppearance={{
        tintColor: '#BF5AF2',
        headerTextColor: '#BF5AF2',
      }}
    >
      <CollectionView.Section
        header="Interface style"
        footer="`colorScheme` drives overrideUserInterfaceStyle, so UIKit's own semantic colours — separators, accessories, label text — follow the app's choice rather than the device's."
      >
        <CollectionView.Host id="scheme" height={64}>
          <Chips
            options={SCHEMES}
            value={colorScheme}
            onChange={setColorScheme}
          />
        </CollectionView.Host>
      </CollectionView.Section>

      <CollectionView.Section
        header="Typeface"
        footer="One `family`, taking the same five generic names React Native's <Text> takes — on iOS they become a UIFontDescriptor.SystemDesign, on Android the matching system face, with ui-rounded degrading to the default one because Android has no rounded family. `Inter` is a bundled variable font, registered by expo-font and resolved natively by name."
      >
        <CollectionView.Host id="face" height={64}>
          <Chips options={FAMILIES} value={family} onChange={setFamily} />
        </CollectionView.Host>
      </CollectionView.Section>

      {family === INTER_FAMILY && (
        <CollectionView.Section
          header="Weight axis"
          footer="Inter's `wght` axis, as `variations: 'wght=550'`. Applied through Core Text, which UIKit has no API for. 350 and 550 are the point: `weight` can only name the nine weights a static family ships, and an axis is continuous."
        >
          <CollectionView.Host id="weight" height={64}>
            <Chips
              options={WEIGHT_AXIS}
              value={weightAxis}
              onChange={setWeightAxis}
            />
          </CollectionView.Host>
        </CollectionView.Section>
      )}

      <CollectionView.Section
        header="Variant"
        footer="Inverted swaps iOS's grouped look — a tinted background behind plain cards — for a plain background with tinted rows. A preset resolved in JavaScript, so anything in `appearance` still wins."
      >
        <CollectionView.Host id="variant" height={64}>
          <Chips options={VARIANTS} value={variant} onChange={setVariant} />
        </CollectionView.Host>
      </CollectionView.Section>

      {Platform.OS === 'android' && (
        <CollectionView.Section
          header="Material list style"
          footer="The two arrangements the M3 list spec defines. `standard` sits items flush with dividers between them; `segmented` gives each its own rounded container, and a selected item a larger radius and the secondaryContainer colour. Visual only — neither changes how the list behaves. Unset follows `listAppearance`."
        >
          <CollectionView.Host id="android-list-style" height={64}>
            <Chips
              options={ANDROID_LIST_STYLES}
              value={listStyle}
              onChange={setListStyle}
            />
          </CollectionView.Host>
        </CollectionView.Section>
      )}

      <CollectionView.Section
        header="Sliders"
        footer="A UISlider on iOS and a Material 3 Slider on Android — the one control whose shape the two platforms have genuinely diverged on, not just its colours. Brightness reports continuously so the value above it tracks the thumb; Volume reports only on release, which is what most callers should do. The step is drawn as tick marks on Android and enforced-but-invisible on iOS, because UISlider has never had them."
      >
        <CollectionView.Row id="brightness-value">
          <CollectionView.Icon systemImage="sun.max" />
          <CollectionView.Label>Brightness</CollectionView.Label>
          <CollectionView.Value>
            {`${Math.round(brightness * 100)}%`}
          </CollectionView.Value>
        </CollectionView.Row>
        <CollectionView.Row id="brightness">
          <CollectionView.Slider
            value={brightness}
            minimumImage="sun.min"
            maximumImage="sun.max"
            onValueChange={setBrightness}
          />
        </CollectionView.Row>

        <CollectionView.Row id="volume-value">
          <CollectionView.Icon systemImage="speaker.wave.3.fill" />
          <CollectionView.Label>Volume</CollectionView.Label>
          <CollectionView.Value>{`${volume} / 10`}</CollectionView.Value>
        </CollectionView.Row>
        <CollectionView.Row id="volume">
          <CollectionView.Slider
            value={volume}
            minimumValue={0}
            maximumValue={10}
            step={1}
            // Only on release. A continuous handler here would be ten commits per drag for a value
            // that is only meaningful once the finger lifts.
            onSlidingComplete={setVolume}
          />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        header="Integrations"
        footer="@gorhom/bottom-sheet reaches the list through the @rngui/collection-view/bottom-sheet entry point, which is a separate import so nobody pays for reanimated and gesture-handler unless they use it."
      >
        <CollectionView.Row
          id="sheet"
          onPress={() => router.push('/custom/sheet')}
        >
          <CollectionView.Icon systemImage="rectangle.bottomhalf.filled" />
          <CollectionView.Label>Bottom sheet</CollectionView.Label>
          <CollectionView.Chevron />
        </CollectionView.Row>
        <CollectionView.Row
          id="windowing"
          onPress={() => router.push('/custom/windowing')}
        >
          <CollectionView.Icon systemImage="rectangle.stack" />
          <CollectionView.Label>Host windowing</CollectionView.Label>
          <CollectionView.Chevron />
        </CollectionView.Row>
      </CollectionView.Section>

      <CollectionView.Section
        header="Self-measuring host"
        footer="This row states no height. Root reads it off the mounted subtree with onLayout and sends it back down, so the cell resizes when the content does — tap to see it. State a height whenever you know it: measuring costs one extra render, which on first mount is a visible settle."
      >
        <CollectionView.Host id="measured">
          <Pressable
            onPress={() => setExpanded((current) => !current)}
            style={styles.measured}
          >
            <Text style={styles.measuredText}>
              {expanded
                ? 'Tap to shrink. A hosted row has no intrinsic content size — Fabric lays this subtree out with Yoga, so an estimated cell would measure it as zero and the height has to travel back through JavaScript. That round trip is what makes this paragraph able to grow the row it lives in, rather than being clipped by a number someone hard-coded months ago.'
                : 'Tap to grow.'}
            </Text>
          </Pressable>
        </CollectionView.Host>
      </CollectionView.Section>

      <CollectionView.Section header="Themed rows">
        <CollectionView.Row>
          <CollectionView.Label>Row label</CollectionView.Label>
          <CollectionView.Value>Trailing value</CollectionView.Value>
        </CollectionView.Row>
        <CollectionView.Row onPress={() => {}}>
          <CollectionView.Label>Pressable row</CollectionView.Label>
          <CollectionView.Chevron />
        </CollectionView.Row>
        <CollectionView.Row>
          <CollectionView.Label>With a subtitle</CollectionView.Label>
          <CollectionView.Description>
            Secondary text, in the same typeface
          </CollectionView.Description>
        </CollectionView.Row>
        <CollectionView.Row>
          <CollectionView.Label>Selected</CollectionView.Label>
          <CollectionView.Checkmark />
        </CollectionView.Row>
      </CollectionView.Section>
    </CollectionView.Root>
  )
}

function Chips<T extends string | number>({
  options,
  value,
  onChange,
}: {
  options: readonly T[]
  value: T
  onChange: (next: T) => void
}) {
  return (
    <View style={styles.chips}>
      {options.map((option) => {
        const active = option === value
        return (
          <Pressable
            key={option}
            onPress={() => onChange(option)}
            style={[styles.chip, active && styles.chipActive]}
          >
            <Text
              numberOfLines={1}
              // The generic family names are long enough to wrap at this width otherwise.
              adjustsFontSizeToFit
              minimumFontScale={0.8}
              style={[styles.chipLabel, active && styles.chipLabelActive]}
            >
              {option}
            </Text>
          </Pressable>
        )
      })}
    </View>
  )
}

const styles = StyleSheet.create((theme) => ({
  chips: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
  },
  chip: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 999,
    alignItems: 'center',
    backgroundColor: theme.colors.background,
  },
  chipActive: {
    backgroundColor: '#AF52DE',
  },
  chipLabel: {
    fontSize: 12,
    fontWeight: '500',
    textAlign: 'center',
    color: theme.colors.secondaryLabel,
  },
  chipLabelActive: {
    color: '#FFFFFF',
  },
  measured: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  measuredText: {
    fontSize: 15,
    lineHeight: 21,
    color: theme.colors.label,
  },
}))

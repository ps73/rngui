import { useState } from 'react'
import { Pressable, Text, View } from 'react-native'
import {
  CollectionView,
  type ColorScheme,
  type FontDesign,
} from '@rngui/collection-view'
import { StyleSheet } from 'react-native-unistyles'

const SCHEMES: readonly ColorScheme[] = ['system', 'light', 'dark']
const DESIGNS: readonly FontDesign[] = [
  'default',
  'rounded',
  'serif',
  'monospaced',
]
const VARIANTS = ['grouped', 'inverted'] as const

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
  const [design, setDesign] = useState<FontDesign>('rounded')
  const [variant, setVariant] = useState<(typeof VARIANTS)[number]>('grouped')

  return (
    <CollectionView.Root
      colorScheme={colorScheme}
      inverted={variant === 'inverted'}
      appearance={{
        tintColor: '#AF52DE',
        headerTextColor: '#AF52DE',
        font: { design },
        headerFont: { design, weight: 'semibold' },
        footerFont: { design },
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
        header="Font design"
        footer="UIFontDescriptor.SystemDesign — the ui-rounded, ui-serif and ui-monospace equivalents. Applied to row labels, headers and footers, and scaled with Dynamic Type."
      >
        <CollectionView.Host id="design" height={64}>
          <Chips options={DESIGNS} value={design} onChange={setDesign} />
        </CollectionView.Host>
      </CollectionView.Section>

      <CollectionView.Section
        header="Variant"
        footer="Inverted swaps iOS's grouped look — a tinted background behind plain cards — for a plain background with tinted rows. A preset resolved in JavaScript, so anything in `appearance` still wins."
      >
        <CollectionView.Host id="variant" height={64}>
          <Chips options={VARIANTS} value={variant} onChange={setVariant} />
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

function Chips<T extends string>({
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
              // The four font-design labels are long enough to wrap at this width otherwise.
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
}))

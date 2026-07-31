import { ScrollView, Text, View } from 'react-native'
import { StyleSheet } from 'react-native-unistyles'

/**
 * A plain React Native `ScrollView` list, used as the **baseline** for header behaviour.
 *
 * This exists on purpose and should not be deleted once `@rngui/collection-view` lands.
 * `ScrollView` is the one scroll view UIKit and react-native-screens are guaranteed to
 * find, so it defines what "correct" looks like: where the large title starts to
 * collapse, how the blur fades in at the scroll edge, and how far the content is inset
 * at the top and bottom. Any divergence between a screen using this and a screen using
 * the native collection view is a bug in the collection view, and having both side by
 * side is what makes that comparison cheap.
 */
export function BaselineList({ rows = 40 }: { rows?: number }) {
  return (
    <ScrollView
      // Lets UIKit own the top and bottom insets: the header and the tab bar are folded
      // into `adjustedContentInset`, so nothing here needs a manual safe-area padding.
      contentInsetAdjustmentBehavior="automatic"
      style={styles.scroll}
      contentContainerStyle={styles.content}
    >
      {Array.from({ length: rows }, (_, i) => (
        <View key={i} style={styles.row}>
          <Text style={styles.label}>Row {i + 1}</Text>
        </View>
      ))}
    </ScrollView>
  )
}

const styles = StyleSheet.create((theme) => ({
  scroll: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  content: {
    paddingVertical: theme.gap(2),
  },
  row: {
    backgroundColor: theme.colors.rowBackground,
    marginHorizontal: theme.gap(2),
    paddingHorizontal: theme.gap(2),
    height: 44,
    justifyContent: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: theme.colors.separator,
  },
  label: {
    fontSize: 17,
    color: theme.colors.label,
  },
}))

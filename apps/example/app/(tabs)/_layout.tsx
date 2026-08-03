import { Platform } from 'react-native'
import { NativeTabs } from 'expo-router/unstable-native-tabs'

/**
 * A real `UITabBarController`, via expo-router's native tabs.
 *
 * Native rather than the JS `Tabs` for two reasons that matter to this library: the tab
 * bar contributes a real bottom safe-area inset that UIKit folds into the collection
 * view's `adjustedContentInset`, and scroll-to-top on tab re-tap is wired to whichever
 * scroll view UIKit discovers in the screen — both of which are behaviours the
 * collection view has to earn rather than fake.
 *
 * Each trigger names an SF Symbol *and* an Android drawable, because a native tab bar draws the
 * platform's own icons and neither set can stand in for the other. The Android ones are vector
 * drawables in `assets/android/drawable`, wired in by the config plugin in `app.config.ts`.
 */
export default function TabsLayout() {
  return (
    <NativeTabs
      // **Android hides the labels of unselected tabs by default**, and with five tabs and no
      // Android drawables set below, that left every inactive tab as blank space. `auto` is a
      // Material `NavigationBar` behaviour — labels for the selected item only, once there are
      // enough destinations — and it is the wrong default for a bar whose items are only
      // distinguishable by their label.
      //
      // iOS ignores this: a `UITabBar` item always shows its title.
      labelVisibilityMode="labeled"
    >
      <NativeTabs.Trigger name="settings">
        <NativeTabs.Trigger.Label>Settings</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf="gearshape.fill"
          drawable="ic_tab_settings"
        />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="reminders">
        <NativeTabs.Trigger.Label>Reminders</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="checklist" drawable="ic_tab_reminders" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="contacts">
        <NativeTabs.Trigger.Label>Contacts</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon
          sf="person.crop.circle.fill"
          drawable="ic_tab_contacts"
        />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="health">
        <NativeTabs.Trigger.Label>Health</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="heart.fill" drawable="ic_tab_health" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="custom">
        <NativeTabs.Trigger.Label>Custom</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="cube.fill" drawable="ic_tab_custom" />
      </NativeTabs.Trigger>
    </NativeTabs>
  )
}

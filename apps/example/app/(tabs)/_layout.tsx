import { NativeTabs } from 'expo-router/unstable-native-tabs'

/**
 * A real `UITabBarController`, via expo-router's native tabs.
 *
 * Native rather than the JS `Tabs` for two reasons that matter to this library: the tab
 * bar contributes a real bottom safe-area inset that UIKit folds into the collection
 * view's `adjustedContentInset`, and scroll-to-top on tab re-tap is wired to whichever
 * scroll view UIKit discovers in the screen — both of which are behaviours the
 * collection view has to earn rather than fake.
 */
export default function TabsLayout() {
  return (
    <NativeTabs>
      <NativeTabs.Trigger name="settings">
        <NativeTabs.Trigger.Label>Settings</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="gearshape.fill" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="reminders">
        <NativeTabs.Trigger.Label>Reminders</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="checklist" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="contacts">
        <NativeTabs.Trigger.Label>Contacts</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="person.crop.circle.fill" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="health">
        <NativeTabs.Trigger.Label>Health</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="heart.fill" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="custom">
        <NativeTabs.Trigger.Label>Custom</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="cube.fill" />
      </NativeTabs.Trigger>
    </NativeTabs>
  )
}

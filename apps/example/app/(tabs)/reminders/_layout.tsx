import { Stack } from 'expo-router'
import { stackScreenOptions } from '../../../src/nav/screen-options'

export default function RemindersLayout() {
  return (
    <Stack screenOptions={stackScreenOptions}>
      <Stack.Screen name="index" options={{ title: 'Reminders' }} />
      {/*
        A real UIKit form sheet, not a JS modal. It is here because a sheet is the presentation
        where the keyboard maths is most likely to be wrong: the list's bottom edge and the
        screen's are nowhere near each other, so anything measuring the overlap against the screen
        insets the list by the gap beneath the sheet as well.
      */}
      <Stack.Screen
        name="new"
        options={{
          title: 'New Reminder',
          presentation: 'formSheet',
          headerLargeTitle: false,
          sheetAllowedDetents: [0.6, 1],
          sheetGrabberVisible: true,
        }}
      />
    </Stack>
  )
}

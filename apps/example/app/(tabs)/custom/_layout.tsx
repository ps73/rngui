import { Stack } from 'expo-router'
import { stackScreenOptions } from '../../../src/nav/screen-options'

export default function CustomLayout() {
  return (
    <Stack screenOptions={stackScreenOptions}>
      <Stack.Screen name="index" options={{ title: 'Custom' }} />
      {/*
        The only screen in the app that opts out of the large blurred header, and both halves are
        deliberate. A large title collapses against the screen's scroll view — here that is the one
        inside the sheet, which moves independently, so the title would lurch as the sheet is
        dragged. And a transparent header has nothing behind it but this screen's own content,
        which would then start underneath it.
      */}
      <Stack.Screen
        name="sheet"
        options={{
          title: 'Bottom Sheet',
          headerLargeTitle: false,
          headerTransparent: false,
        }}
      />
    </Stack>
  )
}

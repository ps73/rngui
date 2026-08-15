import { Stack } from 'expo-router'
import { stackScreenOptions } from '../../../src/nav/screen-options'

export default function CustomLayout() {
  return (
    <Stack screenOptions={stackScreenOptions}>
      <Stack.Screen
        name="index"
        options={{ title: 'Custom', headerLargeTitle: false }}
      />
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
      {/*
        The readout above the list is a sibling of it, so the list is no longer the screen's first
        descendant — which is the one thing the blurred large-title header needs. Opting out keeps
        the screen honest rather than shipping a header that silently does not animate.
      */}
      <Stack.Screen
        name="windowing"
        options={{
          title: 'Host windowing',
          headerLargeTitle: false,
          headerTransparent: false,
        }}
      />
    </Stack>
  )
}

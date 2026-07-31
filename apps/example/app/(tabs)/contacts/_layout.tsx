import { Stack } from 'expo-router'
import { stackScreenOptions } from '../../../src/nav/screen-options'

export default function ContactsLayout() {
  return (
    <Stack screenOptions={stackScreenOptions}>
      <Stack.Screen name="index" options={{ title: 'Contacts' }} />
    </Stack>
  )
}

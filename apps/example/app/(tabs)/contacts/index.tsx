import { Platform } from 'react-native'
import ContactsAndroid from '../../../src/screens/ContactsAndroid'
import ContactsIOS from '../../../src/screens/ContactsIOS'

/** iOS Contacts and Google Contacts, over the same 2,000-row harness. See `settings/index.tsx`. */
export default Platform.OS === 'android' ? ContactsAndroid : ContactsIOS

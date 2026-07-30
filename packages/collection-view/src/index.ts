import { Root } from './CollectionView'
import {
  Button,
  Checkbox,
  Checkmark,
  Chevron,
  Card,
  DatePicker,
  Description,
  Icon,
  Host,
  Label,
  Menu,
  Radio,
  Row,
  Section,
  Spinner,
  SwipeAction,
  SwipeActions,
  Switch,
  TextArea,
  TextField,
  Value,
} from './components'

export { resolveColor, useCollectionViewAppearance } from './appearance'

export type {
  ColorScheme,
  RootProps,
  SectionIndexOptions,
  VisibleRange,
} from './CollectionView'
export type { InheritedAppearance } from './appearance'
export type {
  ButtonProps,
  CardProps,
  DatePickerProps,
  DescriptionProps,
  IconProps,
  HostProps,
  MenuProps,
  RowProps,
  SectionProps,
  SwipeActionProps,
  SwipeActionsProps,
  TextAreaProps,
  TextInputProps,
  ToggleProps,
} from './components'
export type {
  Appearance,
  AccessoryKind,
  AutoCapitalize,
  ButtonRole,
  DatePickerMode,
  DatePickerStyle,
  FontDesign,
  FontSpec,
  GradientSpec,
  SectionLayout,
  HeaderBackgroundStyle,
  KeyboardType,
  ListAppearance,
  MenuItemSpec,
  ReturnKeyType,
  RowKind,
  RowSpec,
  SectionSpec,
  SwipeActionSpec,
  SwipeActionStyle,
  Tree,
} from './tree'

/**
 * A real `UICollectionView`, addressed through a Radix-style compound API.
 *
 * ```tsx
 * <CollectionView.Root
 *   appearance={{ rowBackground: '#fff', font: { design: 'rounded' } }}
 *   darkAppearance={{ rowBackground: '#1c1c1e' }}
 * >
 *   <CollectionView.Section header="General" footer="Applies to this device only.">
 *     <CollectionView.Row onPress={openWifi}>
 *       <CollectionView.Label>Wi-Fi</CollectionView.Label>
 *       <CollectionView.Value>Network</CollectionView.Value>
 *       <CollectionView.Chevron />
 *     </CollectionView.Row>
 *   </CollectionView.Section>
 *
 *   <CollectionView.Host height={180}>
 *     <MyChart />
 *   </CollectionView.Host>
 * </CollectionView.Root>
 * ```
 *
 * Everything except `Host` renders `null` — the tree is read into descriptors rather than
 * mounted, so UIKit builds and recycles the cells itself.
 */
export const CollectionView = {
  Root,
  Section,
  Row,
  Host,

  // Row slots
  Label,
  Description,
  Value,

  // Accessories
  Icon,
  Chevron,
  Checkmark,
  Checkbox,
  Radio,
  Spinner,

  // Controls
  Card,
  Switch,
  TextField,
  TextArea,
  Menu,
  DatePicker,
  Button,

  // Swipe actions
  SwipeActions,
  SwipeAction,
}

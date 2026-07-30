import type { ReactNode } from 'react'

/**
 * Marks a component as part of the compound API.
 *
 * A symbol rather than a string `displayName` check: display names are stripped or mangled
 * by some production transforms, and a consumer could legitimately have their own component
 * called `Row`. The symbol is unforgeable.
 */
export const NODE_TAG = Symbol.for('rngui.nodeTag')

export type TaggedComponent<P> = ((props: P) => null) & {
  readonly [NODE_TAG]: string
}

/**
 * Builds a component that renders nothing and exists purely as a descriptor.
 *
 * This is what makes the compound API free. `<CollectionView.Row>` never becomes a host
 * instance — React reconciles a component that returns `null`, and the tree is *read* by
 * the serializer rather than mounted. A thousand rows cost a thousand `null` renders and no
 * views at all, which is the entire point of handing the data to UIKit and letting it
 * recycle its own cells.
 *
 * The one exception is `Host`, whose children really are rendered; see its own docs.
 */
export function tagged<P>(
  tag: string,
  displayName: string
): TaggedComponent<P> {
  const component = (_props: P) => null
  component.displayName = displayName
  return Object.assign(component, { [NODE_TAG]: tag }) as TaggedComponent<P>
}

/** Anything that can appear as a child in the compound API. */
export type Children = { children?: ReactNode }

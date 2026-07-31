package com.rngui.collectionview

import android.content.Context
import android.view.ViewGroup

/**
 * The Android placeholder for `<CollectionView.Root>`.
 *
 * **A `ViewGroup`, and that is the only load-bearing decision in this file.** The iOS component
 * hosts arbitrary React children, and a manager whose view is not a group makes the mounting layer
 * throw — `Trying to add a view to a view that doesn't support children` — the moment one arrives.
 * That would turn "Android isn't implemented yet" into "importing this package crashes the app".
 *
 * `Root` withholds `<CollectionView.Host>` children on Android, so in practice nothing is mounted
 * here at all. This is the belt to that suspenders: the JavaScript decision is easy to lose in a
 * refactor, and `SimpleViewManager` would turn losing it into a crash rather than a stray view.
 *
 * `onLayout` is empty because React positions children itself: `ViewGroupManager` reports
 * `needsCustomLayoutForChildren() == false`, so Fabric's mounting layer calls `layout()` on each
 * child directly from the Yoga result. Measuring them here would be worse than useless — React
 * Native views have no intrinsic size to measure.
 *
 * The real implementation is a `RecyclerView` with Material 3 Expressive list items; see the
 * package README.
 */
class RNGUICollectionViewView(
  context: Context,
) : ViewGroup(context) {
  override fun onLayout(
    changed: Boolean,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ) = Unit
}

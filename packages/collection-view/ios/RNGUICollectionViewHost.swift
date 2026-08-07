import UIKit

/**
 * Owns every piece of UIKit in the component.
 *
 * Deliberately knows nothing about React. The ObjC++ component view converts Fabric's C++ props
 * into plain values and calls in here, and that wall is the whole architecture: Swift cannot
 * import folly or jsi, so keeping those types on the far side of a thin `.mm` shim is what makes
 * a Swift implementation possible at all.
 *
 * Everything the shim touches is explicitly `@objc` — Swift enums, structs, tuples and generics
 * are invisible to Objective-C, so this class's outward surface is scalars, `NSString`, `UIView`
 * and arrays of them only.
 */
/// `@MainActor` because every member touches UIKit. `UIView` and friends carry the annotation in
/// the SDK, so an unannotated `NSObject` subclass cannot call them — and the isolation is true
/// regardless: Fabric delivers `updateProps` and mounts children on the main thread.
@MainActor
@objc(RNGUICollectionViewHost)
public final class RNGUICollectionViewHost: NSObject {
  /**
   * The view the component view installs as its `contentView`.
   *
   * A container rather than the collection view itself, for three reasons. The A–Z section-index
   * scrubber will have to be a *sibling* of the collection view, since it must not scroll with
   * the content; hosted React views are parked here, hidden, while no cell is displaying them;
   * and `overrideUserInterfaceStyle` is set here so it propagates to everything below.
   *
   * The collection view is added first and must stay at subview index 0 — see the note in
   * `RNGUICollectionViewComponentView.mm`. Parked hosted views therefore land at index 1 and
   * above, which keeps the `subviews[0]` chain that react-native-screens walks intact.
   */
  @objc public var containerView: UIView { container }
  private let container = ContainerView()

  @objc public let collectionView: UICollectionView

  /**
   * Reports the visible row range, as inclusive indices into the flattened row list.
   *
   * Installed by the component view, which closes over the Fabric event emitter. A block rather
   * than a delegate because the `.mm` is the only thing that can speak to the emitter, and
   * `(Int, Int) -> Void` is the widest signature that survives the `@objc` boundary intact.
   */
  @objc public var onVisibleRangeChange: ((Int, Int) -> Void)?

  /**
   * Row-scoped event blocks, installed by the component view.
   *
   * Each carries the row **id**, never an index. Indices shift the moment a row is inserted — an
   * inline date picker appearing between a switch and its footer moves every index after it — and
   * an event that arrives against a stale index updates a row the user never touched.
   */
  @objc public var onRowPress: ((String) -> Void)?
  @objc public var onSwitchChange: ((String, Bool) -> Void)?
  @objc public var onTextChange: ((String, String) -> Void)?
  @objc public var onFocusChange: ((String, Bool) -> Void)?
  @objc public var onDateChange: ((String, Double) -> Void)?
  /// Continuous, one per drag frame. See `onSliderCommit` for the once-per-gesture counterpart.
  @objc public var onSliderChange: ((String, Double) -> Void)?
  @objc public var onSliderCommit: ((String, Double) -> Void)?
  @objc public var onMenuSelect: ((String, String) -> Void)?
  @objc public var onSwipeAction: ((String, String) -> Void)?
  /// Carries a *section* id, unlike every other block here. A header button belongs to no row.
  @objc public var onSectionAction: ((String) -> Void)?

  /**
   * `UIScrollViewDelegate`, forwarded as five blocks rather than one with a kind discriminator.
   *
   * A discriminator would mean an integer table on each side of the `.mm` boundary that has to
   * stay in step by hand — the failure this file avoids everywhere else. Five properties cost five
   * lines and cannot drift.
   *
   * Each carries `(contentOffset, contentSize, layoutMeasurement, adjustedContentInset)`, which is
   * `ScrollView`'s payload minus `zoomScale`; a collection view never zooms, so the `.mm` sends a
   * constant `1` rather than plumbing a value that can only have one.
   */
  @objc public var onScroll: ((CGPoint, CGSize, CGSize, UIEdgeInsets) -> Void)?
  @objc public var onScrollBeginDrag: ((CGPoint, CGSize, CGSize, UIEdgeInsets) -> Void)?
  @objc public var onScrollEndDrag: ((CGPoint, CGSize, CGSize, UIEdgeInsets) -> Void)?
  @objc public var onMomentumScrollBegin:
    ((CGPoint, CGSize, CGSize, UIEdgeInsets) -> Void)?
  @objc public var onMomentumScrollEnd:
    ((CGPoint, CGSize, CGSize, UIEdgeInsets) -> Void)?
  @objc public var onContentSizeChange: ((CGSize) -> Void)?

  /// The pull. No payload, as `RefreshControl` has it.
  @objc public var onRefresh: (() -> Void)?

  // MARK: Insets and keyboard

  /// What the caller asked for, kept apart from what is currently applied. The keyboard path needs
  /// `max(overlap, base.bottom)` and must never shrink the list below the caller's own inset.
  private var baseContentInset: UIEdgeInsets = .zero
  private var keyboardOverlap: CGFloat = 0
  private var adjustsKeyboardInsets = false
  private var keyboardAware = false
  private var keyboardAwareOffset: CGFloat = 0
  private var insetBehavior: UIScrollView.ContentInsetAdjustmentBehavior = .automatic
  private var adjustsContentInsets = true
  private var keyboardObserver: KeyboardObserver!

  /**
   * The gradient behind the content.
   *
   * Installed as the collection view's `backgroundView` rather than as a layer on the collection
   * view itself, because `backgroundView` is the one thing UIKit keeps pinned to the *bounds* while
   * the content scrolls over it. A layer added to the collection view would scroll with the content
   * and a layer on the container would be covered by the list's own background colour.
   */
  private let gradientLayer = CAGradientLayer()
  private lazy var gradientView: GradientBackgroundView = {
    let view = GradientBackgroundView()
    view.layer.addSublayer(gradientLayer)
    view.gradient = gradientLayer
    return view
  }()

  private let sectionIndexBar = SectionIndexBar()
  private var sectionIndexTopConstraint: NSLayoutConstraint!
  private var sectionIndexBottomConstraint: NSLayoutConstraint!
  private var showsSectionIndex = false
  private var tracksVisibleRange = false
  private var visibleRangeEmitScheduled = false
  private var lastEmittedRange: (first: Int, last: Int)?

  // MARK: Pull to refresh

  /**
   * Built once and kept, but **not** handed to the collection view until asked for.
   *
   * Assigning `collectionView.refreshControl` is what turns the pull gesture on, so a list with no
   * `refreshControl` prop must never see the assignment — otherwise every list in the library
   * would suddenly rubber-band into a spinner.
   */
  private let refreshControl = UIRefreshControl()
  private var refreshEnabled = false
  /// What JavaScript last asked for, kept apart from what the control is actually doing. The two
  /// disagree for exactly as long as it takes a deferred `beginRefreshing` to land.
  private var desiredRefreshing = false

  private var tracksScroll = false
  /// Retained for as long as the host lives; releasing it is what unregisters the observer.
  private var contentSizeObservation: NSKeyValueObservation?
  private var lastEmittedContentSize: CGSize?

  private var listLayout: UICollectionViewCompositionalLayout!
  private var dataSource: UICollectionViewDiffableDataSource<String, String>!

  private var sections: [SectionSpec] = []
  private var rowsById: [String: RowSpec] = [:]
  /**
   * Row id to its position in the flattened row list, for `onVisibleRangeChange`.
   *
   * Flat indices rather than `{section, item}` pairs because the consumer of this is JavaScript
   * windowing a list of hosted rows, and it holds a flat array of children — `hostIndex` is
   * already a flat index into that same ordering.
   */
  private var flatIndexByRowId: [String: Int] = [:]
  private var listAppearance: ListAppearance = .insetGrouped
  private var resolver = AppearanceResolver(light: nil, dark: nil)
  /// The appearance the currently displayed supplementary views were configured with.
  private var appliedLight: Appearance?
  private var appliedDark: Appearance?
  private var hostedViews: [UIView] = []

  // One registration per cell kind, and each is its own reuse pool — which is what keeps a stock
  // list cell from ever being handed back to a hosted row.
  //
  // Built eagerly in `init`, never `lazy`. A lazy property is created on first *access*, and the
  // first access is inside the diffable data source's cell provider — which UIKit detects and
  // treats as a fatal error ("Registrations should be created up front and reused"), because a
  // registration created per dequeue defeats recycling entirely and leaks every cell it makes.
  // Implicitly unwrapped because a subclass has to initialise its own stored properties before
  // `super.init()`, and these need `self` in order to capture it.
  private var listRegistration:
    UICollectionView.CellRegistration<UICollectionViewListCell, RowSpec>!
  private var hostRegistration: UICollectionView.CellRegistration<HostCell, RowSpec>!
  private var switchRegistration: UICollectionView.CellRegistration<SwitchCell, RowSpec>!
  private var sliderRegistration: UICollectionView.CellRegistration<SliderCell, RowSpec>!
  private var textFieldRegistration:
    UICollectionView.CellRegistration<TextFieldCell, RowSpec>!
  private var textAreaRegistration: UICollectionView.CellRegistration<TextAreaCell, RowSpec>!
  private var menuRegistration: UICollectionView.CellRegistration<MenuCell, RowSpec>!
  private var cardRegistration: UICollectionView.CellRegistration<CardCell, RowSpec>!
  private var chipRegistration: UICollectionView.CellRegistration<ChipCell, RowSpec>!
  // Two date registrations, because a compact pill and an inline calendar must not share a reuse
  // pool — see the note in `DatePickerCell`.
  private var compactDateRegistration:
    UICollectionView.CellRegistration<DatePickerCell, RowSpec>!
  private var expandedDateRegistration:
    UICollectionView.CellRegistration<DatePickerCell, RowSpec>!
  private var headerRegistration:
    UICollectionView.SupplementaryRegistration<UICollectionViewListCell>!
  private var footerRegistration:
    UICollectionView.SupplementaryRegistration<UICollectionViewListCell>!

  @objc public override init() {
    collectionView = UICollectionView(
      frame: .zero,
      collectionViewLayout: UICollectionViewFlowLayout()
    )

    super.init()

    listLayout = makeLayout()
    collectionView.setCollectionViewLayout(listLayout, animated: false)

    // Constraints rather than an autoresizing mask: Fabric sets the container's frame (via
    // `RCTViewComponentView`, which assigns `_contentView.frame` from its layout metrics), and
    // pinning to the edges propagates that unambiguously from a zero starting frame.
    collectionView.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(collectionView)
    NSLayoutConstraint.activate([
      collectionView.topAnchor.constraint(equalTo: container.topAnchor),
      collectionView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
      collectionView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
      collectionView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
    ])

    // Lets UIKit fold the navigation bar and tab bar into `adjustedContentInset`. Driven by
    // `contentInsetAdjustmentBehavior`, whose default is `automatic` rather than `ScrollView`'s
    // `never` — see the note on the prop.
    collectionView.contentInsetAdjustmentBehavior = .automatic
    // **`.onDrag`, not `.interactive`, and the difference is the whole of the reported bug.**
    // `.interactive` dismisses only when you drag *the keyboard itself* downward; scrolling the
    // list does nothing. So a field stayed focused through every scroll and every tap on another
    // row, and the only gesture that closed it was the one almost nobody tries. `.onDrag` is also
    // what the Android side has always defaulted to, so the two platforms now agree — a caller can
    // still ask for `interactive` explicitly.
    collectionView.keyboardDismissMode = .onDrag

    // **A tap recogniser rather than the selection callback, and the difference is the point.**
    // `didSelectItemAt` fires only for *selectable* rows, so hanging dismissal off it would leave a
    // tap on a switch row, a disabled row or the gap between cards doing nothing — and a keyboard
    // that ignores half the screen is a worse bug than the one being fixed.
    //
    // `cancelsTouchesInView = false` is what keeps it invisible: the tap still reaches whatever it
    // was going to reach, so row presses, switches and swipes behave exactly as before.
    let dismissTap = UITapGestureRecognizer(target: self, action: #selector(handleDismissTap(_:)))
    dismissTap.cancelsTouchesInView = false
    collectionView.addGestureRecognizer(dismissTap)

    keyboardObserver = KeyboardObserver(scrollView: collectionView)
    keyboardObserver.onChange = { [weak self] overlap, duration, options in
      self?.applyKeyboardOverlap(overlap, duration: duration, options: options)
    }

    // Needed for scroll tracking, and it is what selection and swipe actions hang off.
    collectionView.delegate = self

    // KVO rather than a `UICollectionView` subclass or a check inside `scrollViewDidScroll`: the
    // content size changes when the *layout* resolves, which is neither a scroll nor a moment the
    // delegate is told about. A sheet sizing itself to its content has to hear about it then, not
    // on the first drag afterwards.
    contentSizeObservation = collectionView.observe(\.contentSize, options: [.new]) {
      [weak self] _, change in
      guard let size = change.newValue else { return }
      // UIKit only ever mutates this during layout on the main thread; the assertion documents
      // that rather than paying for a hop that would report the size a frame late.
      MainActor.assumeIsolated {
        self?.contentSizeDidChange(size)
      }
    }

    // A scroll view holds content touches for ~150 ms before delivering them, so that starting a
    // scroll does not flash the row under the finger. The cost is that a *quick* tap can begin and
    // end inside that window, and the highlight never renders a single frame — the row just does not
    // respond, which is what a list in iOS never does. Apple's own settings lists highlight
    // immediately; matching that means opting out.
    collectionView.delaysContentTouches = false

    // Added *after* the collection view, so the collection view keeps subview index 0 and the
    // `subviews[0]` chain react-native-screens walks stays intact.
    sectionIndexBar.isHidden = true
    sectionIndexBar.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(sectionIndexBar)
    sectionIndexTopConstraint = sectionIndexBar.topAnchor.constraint(
      equalTo: container.topAnchor
    )
    sectionIndexBottomConstraint = sectionIndexBar.bottomAnchor.constraint(
      equalTo: container.bottomAnchor
    )
    NSLayoutConstraint.activate([
      sectionIndexBar.trailingAnchor.constraint(equalTo: container.trailingAnchor),
      sectionIndexBar.widthAnchor.constraint(equalToConstant: SectionIndexBar.preferredWidth),
      sectionIndexTopConstraint,
      sectionIndexBottomConstraint,
    ])
    sectionIndexBar.onSelect = { [weak self] section in
      self?.scrollToSection(section)
    }

    // Not `collectionView.refreshControl = refreshControl` — see the property's own note. Only the
    // target is wired here; the control is attached when a caller asks for one.
    refreshControl.addTarget(
      self,
      action: #selector(refreshControlValueChanged),
      for: .valueChanged
    )

    container.onMovedToWindow = { [weak self] in
      self?.adoptAsContentScrollView()
    }

    // Retries a `beginRefreshing()` that UIKit dropped because the control had not been laid out
    // yet. Everything here is a field read except in the one frame where it actually starts.
    container.onDidLayout = { [weak self] in
      guard let self,
        self.refreshEnabled,
        self.desiredRefreshing,
        !self.refreshControl.isRefreshing
      else { return }
      self.beginRefreshingProgrammatically()
    }

    // Only fonts and spacing need this; colours are dynamic `UIColor`s that UIKit re-resolves on
    // its own. See `AppearanceResolver` for why the distinction exists.
    if #available(iOS 17.0, *) {
      container.registerForTraitChanges(
        [UITraitUserInterfaceStyle.self],
        target: self,
        action: #selector(interfaceStyleDidChange)
      )
    } else {
      container.onInterfaceStyleChange = { [weak self] in
        self?.interfaceStyleDidChange()
      }
    }

    listRegistration = makeListRegistration()
    hostRegistration = makeHostRegistration()
    switchRegistration = makeSwitchRegistration()
    sliderRegistration = makeSliderRegistration()
    textFieldRegistration = makeTextFieldRegistration()
    textAreaRegistration = makeTextAreaRegistration()
    menuRegistration = makeMenuRegistration()
    cardRegistration = makeCardRegistration()
    chipRegistration = makeChipRegistration()
    compactDateRegistration = makeDateRegistration(compact: true)
    expandedDateRegistration = makeDateRegistration(compact: false)
    headerRegistration = makeBoundaryRegistration(
      kind: UICollectionView.elementKindSectionHeader
    )
    footerRegistration = makeBoundaryRegistration(
      kind: UICollectionView.elementKindSectionFooter
    )

    configureDataSource()
    applyChrome()
  }

  // MARK: - Navigation bar integration

  /**
   * Tells the owning view controller, explicitly, which scroll view drives its navigation bar.
   *
   * Without this, a large title never collapses and a blurred header never fades in — the whole
   * header just sits at its expanded height while the content scrolls underneath. Both UIKit and
   * react-native-screens otherwise *guess* which scroll view belongs to a screen by walking
   * `subviews[0]` from the controller's view down (see
   * `RNSScrollViewFinder.findScrollViewInFirstDescendantChainFrom:`), and satisfying that walk
   * turns out not to be sufficient — UIKit's own lookup does not accept an arbitrary scroll view
   * nested behind a plain container.
   *
   * `setContentScrollView(_:for:)` is the public API that replaces the guess with an answer. The
   * `subviews[0]` invariant is still maintained on top of it, because react-native-screens uses
   * the same heuristic for the iOS 26 scroll-edge effects and that path has no override.
   */
  private func adoptAsContentScrollView() {
    guard let controller = owningViewController else { return }
    controller.setContentScrollView(collectionView, for: .all)
  }

  private var owningViewController: UIViewController? {
    var responder: UIResponder? = container
    while let current = responder {
      if let controller = current as? UIViewController { return controller }
      responder = current.next
    }
    return nil
  }

  // MARK: - Appearance

  @objc private func interfaceStyleDidChange() {
    // Colours need nothing: they are dynamic and UIKit has already re-resolved them. Fonts and
    // spacing cannot be dynamic, so a caller who themes them per mode needs visible cells
    // rebuilt — and only then, since reconfiguring on every trait change would be wasteful.
    guard resolver.hasStyleDependentNonColorValues else { return }
    applyChrome()
    reconfigureVisibleItems()
  }

  /// The colour of the surface behind the cells, before any theming.
  private var defaultBackgroundColor: UIColor {
    switch listAppearance {
    case .plain: return .systemBackground
    case .grouped, .insetGrouped, .unknown: return .systemGroupedBackground
    }
  }

  private var listConfigurationAppearance: UICollectionLayoutListConfiguration.Appearance {
    switch listAppearance {
    case .grouped: return .grouped
    case .plain: return .plain
    case .insetGrouped, .unknown: return .insetGrouped
    }
  }

  /// View-level styling that is not part of a cell configuration.
  private func applyChrome() {
    collectionView.backgroundColor = resolver.color(
      \.background,
      fallback: defaultBackgroundColor
    )
    applyBackgroundGradient()
    // `nil` restores inheritance from the window's tint rather than pinning a colour.
    collectionView.tintColor = resolver.optionalColor(\.tintColor)

    let configuration = UICollectionViewCompositionalLayoutConfiguration()
    if let spacing = resolver.value({ $0.sectionSpacing }, for: container.traitCollection) {
      configuration.interSectionSpacing = CGFloat(spacing)
    }
    // Section spacing lives on the layout rather than on a section, so it can only be changed by
    // replacing the configuration — which invalidates the layout as a side effect.
    listLayout.configuration = configuration
  }

  /**
   * Installs or removes the gradient behind the content.
   *
   * Colours are resolved against the container's traits rather than handed over as dynamic
   * `UIColor`s, because `CAGradientLayer.colors` takes `CGColor` — which has no notion of a trait
   * collection and would freeze whichever mode was current. `GradientBackgroundView` re-resolves
   * them on a style change for exactly that reason.
   */
  private func applyBackgroundGradient() {
    let spec = resolver.value({ $0.backgroundGradient }, for: container.traitCollection)
    guard let spec, spec.colors.count >= 2 else {
      collectionView.backgroundView = nil
      return
    }

    gradientView.spec = spec
    if collectionView.backgroundView !== gradientView {
      collectionView.backgroundView = gradientView
    }
    gradientView.applySpec()
  }

  // MARK: - Layout

  private func makeLayout() -> UICollectionViewCompositionalLayout {
    // A section provider rather than the `.list(using:)` convenience initialiser. Sections have
    // to be able to differ — headers and footers appear per section, and a horizontally
    // scrolling chip row will sit next to vertical list sections — and the convenience form
    // fixes one configuration for the entire view.
    UICollectionViewCompositionalLayout { [weak self] index, environment in
      guard let self else { return nil }

      let section = self.sections[safe: index]

      // A horizontally scrolling strip *inside* a vertical list — the one thing compositional
      // layout does that a `UITableView` cannot. Returned before any list configuration is built,
      // because a chip section is not a list section at all.
      if section?.layout == .chips {
        return self.makeChipSection(for: section, environment: environment)
      }

      var configuration = UICollectionLayoutListConfiguration(
        appearance: self.listConfigurationAppearance
      )
      configuration.headerMode = section?.header != nil ? .supplementary : .none
      configuration.footerMode = section?.footer != nil ? .supplementary : .none

      // A plain list's headers sit snug against the rows above them, the way Contacts' alphabet
      // headers do. The default adds a gap that reads as a group separator, which is right for a
      // grouped list and wrong for an index.
      if self.listAppearance == .plain {
        configuration.headerTopPadding = 0
      }

      // Transparent so the collection view's own background is what shows through; otherwise
      // this would paint over anything set via `appearance.background`.
      configuration.backgroundColor = .clear

      // Swipe actions are configured through the *layout* rather than a delegate, which is the
      // modern list API: the provider is handed an index path and answers from the row's descriptor.
      configuration.trailingSwipeActionsConfigurationProvider = { [weak self] indexPath in
        self?.swipeActions(at: indexPath, trailing: true)
      }
      configuration.leadingSwipeActionsConfigurationProvider = { [weak self] indexPath in
        self?.swipeActions(at: indexPath, trailing: false)
      }

      if let separator = self.resolver.optionalColor(\.separator) {
        var separatorConfiguration = UIListSeparatorConfiguration(
          listAppearance: self.listConfigurationAppearance
        )
        separatorConfiguration.color = separator
        configuration.separatorConfiguration = separatorConfiguration
      }

      let layoutSection = NSCollectionLayoutSection.list(
        using: configuration,
        layoutEnvironment: environment
      )

      // Sticky alphabet headers — the Contacts behaviour, and the reason the `plain` appearance
      // exists here at all.
      //
      // **Gated on iOS 26, and that gate is the price of swipe actions.** `pinToVisibleBounds` on a
      // `.list(using:)` section does two things on iOS 18: it fails to pin, *and* it takes that
      // section's swipe actions with it. Both symptoms, one cause — proven by the fact that swipe
      // works on 18 in a grouped list, which never reaches this block, and fails in a plain one,
      // which does. Setting it and hoping was costing a working feature to buy a broken one.
      //
      // So below 26 the headers scroll with their rows. That is a visible loss and the honest
      // trade: an alphabet header that does not stick is a cosmetic difference, and a swipe action
      // that never opens is a function the caller asked for and did not get.
      //
      // Ruled out along the way, so nobody re-tries them: appending a pinned item when the loop
      // finds none (no effect — the loop does find the configuration's item), replacing it with a
      // hand-built one (worse — the header stops rendering, so the supplementary provider is bound
      // to the configuration's item rather than to its `elementKind`), writing the array back
      // (irrelevant), and `zIndex` alone (irrelevant).
      if self.listAppearance == .plain, #available(iOS 26.0, *) {
        let items = layoutSection.boundarySupplementaryItems
        for item in items
        where item.elementKind == UICollectionView.elementKindSectionHeader {
          item.pinToVisibleBounds = true
          // Above the cells, or the rows scroll over the pinned header instead of under it.
          item.zIndex = 2
        }
        layoutSection.boundarySupplementaryItems = items
      }

      return layoutSection
    }
  }

  /**
   * A chip strip: one estimated-width item per chip, scrolling continuously.
   *
   * `.estimated` on the *width* is what makes a chip as wide as its own text — the cell self-sizes
   * horizontally against the label's intrinsic width, so nothing here has to guess a constant.
   *
   * Note this section deliberately never pins its header. `pinToVisibleBounds` combined with
   * `orthogonalScrollingBehavior` in one layout is a known-flaky pairing, and the guard that keeps
   * them apart is that pinning is only applied for the `plain` appearance while chips live in
   * grouped lists. Worth keeping that way rather than relying on it by accident.
   */
  private func makeChipSection(
    for section: SectionSpec?,
    environment: NSCollectionLayoutEnvironment
  ) -> NSCollectionLayoutSection {
    let height: CGFloat = 36
    let size = NSCollectionLayoutSize(
      widthDimension: .estimated(90),
      heightDimension: .absolute(height)
    )
    let group = NSCollectionLayoutGroup.horizontal(
      layoutSize: size,
      subitems: [NSCollectionLayoutItem(layoutSize: size)]
    )
    let layoutSection = NSCollectionLayoutSection(group: group)
    layoutSection.orthogonalScrollingBehavior = .continuous
    layoutSection.interGroupSpacing = 8
    layoutSection.contentInsets = NSDirectionalEdgeInsets(
      top: 8, leading: 16, bottom: 8, trailing: 16
    )

    if section?.header != nil {
      layoutSection.boundarySupplementaryItems = [
        NSCollectionLayoutBoundarySupplementaryItem(
          layoutSize: NSCollectionLayoutSize(
            widthDimension: .fractionalWidth(1),
            heightDimension: .estimated(32)
          ),
          elementKind: UICollectionView.elementKindSectionHeader,
          alignment: .top
        )
      ]
    }
    return layoutSection
  }

  // MARK: - Cell registrations

  private func makeListRegistration()
    -> UICollectionView.CellRegistration<UICollectionViewListCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      let traits = cell.traitCollection

      // The three stock presets. `subtitle` and `value` are inferred in JS from the presence of
      // a description or a trailing value, so there is one source of truth.
      var content: UIListContentConfiguration
      switch row.kind {
      case .subtitle: content = .subtitleCell()
      case .value: content = .valueCell()
      default: content = .cell()
      }

      content.text = row.label
      content.secondaryText = row.kind == .value ? row.value : row.secondaryLabel

      if let labelColor = self.resolver.optionalColor(\.labelColor) {
        content.textProperties.color = labelColor
      }
      if let secondaryColor = self.resolver.optionalColor(\.secondaryLabelColor) {
        content.secondaryTextProperties.color = secondaryColor
      }

      // The preset's own font is the fallback, so an unset size or weight inherits whatever that
      // slot is supposed to look like rather than collapsing to a generic body font.
      content.textProperties.font = self.font(
        for: row,
        fallback: content.textProperties.font,
        traits: traits
      )
      content.secondaryTextProperties.font = self.font(
        for: row,
        fallback: content.secondaryTextProperties.font,
        traits: traits
      )

      self.applyImage(row, to: &content)
      self.applySecondaryTint(row, to: &content)

      // A `button` row is a centred, tinted action — not a row that happens to be pressable, and
      // the two look nothing alike. Applied after the fonts so it overrides the label colour the
      // theming just set.
      if row.kind == .button {
        content.textProperties.alignment = .center
        switch row.role ?? .default {
        case .destructive:
          content.textProperties.color = .systemRed
        case .plain:
          content.textProperties.color = self.resolver.optionalColor(\.labelColor) ?? .label
        case .default, .unknown:
          content.textProperties.color = self.rowTint(row) ?? .tintColor
        }
        if row.disabled == true {
          content.textProperties.color = .tertiaryLabel
        }
      } else if row.disabled == true {
        content.textProperties.color = .secondaryLabel
        content.secondaryTextProperties.color = .tertiaryLabel
      }

      cell.contentConfiguration = content

      // Called on *every* configure pass, never only when themed. Cells are reused, and
      // `reconfigureItems` hands back the same instance with whatever configuration it last had —
      // so a row tinted once would stay tinted after the theming was removed. Switching
      // inverted -> grouped left rows carrying the inverted colour while the background correctly
      // reverted, which reads as "everything is tinted".
      self.applyBackground(to: cell, row: row)

      cell.accessories = self.accessories(for: row)
    }
  }

  /**
   * The trailing decoration for a row.
   *
   * Always returns a complete array, including the empty one — a reused cell arrives holding
   * whatever accessories it last had, so anything conditional here leaves a stray checkmark on an
   * unrelated row.
   *
   * `checkbox` and `radio` are drawn as SF Symbols in a `.customView` rather than with
   * `.multiselect()`, because that built-in accessory reflects the *collection view's* selection
   * state and ours comes from the descriptor. Binding the visual to UIKit's selection would mean
   * two sources of truth that disagree the moment a row is reconfigured.
   */
  private func accessories(for row: RowSpec) -> [UICellAccessory] {
    // Unwrapped into a typed local rather than switched on directly: `AccessoryKind` has its own
    // `none` case, so `case .none` against an `AccessoryKind?` would be ambiguous with
    // `Optional.none` — and would silently match the wrong one.
    let accessory: AccessoryKind = row.accessory ?? .none

    // UIKit arranges trailing accessories from the outside in, so whatever goes first sits at the
    // edge. The disclosure belongs there and the badge belongs inside it, which is where Settings
    // puts a count — never the other way round.
    var result: [UICellAccessory] = []

    switch accessory {
    case .disclosure:
      result.append(.disclosureIndicator())
    case .checkmark:
      result.append(.checkmark())
    case .checkbox:
      result.append(symbolAccessory(
        on: row.on == true,
        onName: "checkmark.circle.fill",
        offName: "circle",
        row: row
      ))
    case .radio:
      result.append(symbolAccessory(
        on: row.on == true,
        onName: "largecircle.fill.circle",
        offName: "circle",
        row: row
      ))
    case .spinner:
      let indicator = UIActivityIndicatorView(style: .medium)
      indicator.startAnimating()
      result.append(.customView(
        configuration: .init(customView: indicator, placement: .trailing(displayed: .always))
      ))
    case .none, .unknown:
      break
    }

    if let text = row.badge, !text.isEmpty {
      let badge = BadgeView()
      badge.configure(
        text: text,
        color: row.badgeColor.flatMap { UIColor(rnguiHex: $0) } ?? .systemRed
      )
      result.append(.customView(
        configuration: .init(customView: badge, placement: .trailing(displayed: .always))
      ))
    }

    return result
  }

  private func symbolAccessory(
    on: Bool,
    onName: String,
    offName: String,
    row: RowSpec
  ) -> UICellAccessory {
    let view = UIImageView(image: UIImage(systemName: on ? onName : offName))
    view.tintColor =
      row.disabled == true
      ? .tertiaryLabel
      : (on ? (rowTint(row) ?? .tintColor) : .tertiaryLabel)
    return .customView(
      configuration: .init(customView: view, placement: .trailing(displayed: .always))
    )
  }

  /// A per-row tint override, falling back to the list's own.
  private func rowTint(_ row: RowSpec) -> UIColor? {
    if let hex = row.tintColor, let color = UIColor(rnguiHex: hex) { return color }
    return resolver.optionalColor(\.tintColor)
  }

  /**
   * The font for a row, with the row's own override layered over the list's.
   *
   * Resolved in two stages, and that is what makes the override inherit field by field: the list's
   * spec resolves against `fallback`, then the row's spec resolves against *that* result. So a row
   * asking only for `{ size: 22 }` keeps the weight and design the list established.
   */
  private func font(
    for row: RowSpec,
    fallback: UIFont,
    traits: UITraitCollection
  ) -> UIFont {
    let base = FontResolver.resolve(
      resolver.value({ $0.font }, for: traits),
      fallback: fallback
    )
    guard let spec = row.font else { return base }
    return FontResolver.resolve(spec, fallback: base)
  }

  /// The font a row's primary label uses, so the control cells match the stock ones.
  private func rowFont(_ row: RowSpec, traits: UITraitCollection) -> UIFont {
    font(for: row, fallback: .preferredFont(forTextStyle: .body), traits: traits)
  }

  /**
   * The leading SF Symbol, if the row has one.
   *
   * Always assigns, including the nil case. `content` is rebuilt every pass so there is no reuse
   * hazard here today, but the rule for anything that reaches a recycled view is that it is fully
   * specified — and this function is one refactor away from being handed a reused configuration.
   */
  private func applyImage(_ row: RowSpec, to content: inout UIListContentConfiguration) {
    // A monogram avatar: initials rather than a glyph, in the same container. Checked before the
    // symbol because it wins over one, and it needs a container for the same reason Android says
    // so — two letters floating where an icon belongs read as a label that lost its row.
    if let letters = monogramLetters(row) {
      guard let hex = row.imageBackground, let background = UIColor(rnguiHex: hex) else {
        Self.warnOnce(
          "monogram-without-background",
          "[@rngui/collection-view] a monogram needs `background` to sit in — letters with "
            + "nothing behind them are not an avatar. That row's icon renders nothing."
        )
        content.image = nil
        return
      }
      content.image = IconTile.image(monogram: letters, background: background)
      applyTileMetrics(to: &content)
      return
    }

    guard let name = row.systemImage else {
      content.image = nil
      return
    }

    // The Settings look: a white glyph on a coloured tile. Checked first because it replaces the
    // bare-symbol path outright rather than decorating it — `imageColor` has no meaning here, and
    // the tile is already every colour it needs.
    if let hex = row.imageBackground, let background = UIColor(rnguiHex: hex) {
      let tile = IconTile.image(symbol: name, background: background)
      content.image = tile
      applyTileMetrics(to: &content)
      return
    }

    guard let image = UIImage(systemName: name) else {
      content.image = nil
      return
    }
    content.image = image
    if let size = row.imageSize {
      let side = CGFloat(size)
      content.imageProperties.preferredSymbolConfiguration =
        UIImage.SymbolConfiguration(pointSize: side)
      // **Both**, and neither is enough alone. An SF Symbol's rendered box is wider than its point
      // size, so `maximumSize` is what stops the image claiming that whole box — but the leading
      // margin is still computed against whatever the configuration decides to reserve, and left to
      // `.zero` it reserves the natural size and eats the margin. Measured on the account row: 2pt
      // of leading with neither, 6pt with `maximumSize` alone, and the list's standard 16pt with
      // both. The tile path above has always set the pair, which is why it never showed this.
      content.imageProperties.reservedLayoutSize = CGSize(width: side, height: side)
      content.imageProperties.maximumSize = CGSize(width: side, height: side)
    } else {
      content.imageProperties.preferredSymbolConfiguration = nil
      content.imageProperties.reservedLayoutSize = .zero
      content.imageProperties.maximumSize = .zero
    }
    // Grey by default, not the list tint. A tinted glyph reads as an interactive control, and in a
    // list these are labels for the row — which is why Reminders' calendar and clock are grey while
    // Settings reserves colour for its rounded tiles. `.secondaryLabel` rather than a fixed grey
    // because it is dynamic: it adapts to dark mode with no JavaScript involvement, which a hex
    // handed down from a `color` prop cannot (that value crosses as one static colour).
    content.imageProperties.tintColor =
      row.imageColor.flatMap { UIColor(rnguiHex: $0) } ?? .secondaryLabel
  }

  /**
   * The size a filled container claims, whether it holds a glyph or initials.
   *
   * Reserved so that rows *without* one still align their text with the rows that have it. A
   * Settings section where one row's label starts 29pt further left than its neighbours is the
   * giveaway that the screen was assembled rather than laid out.
   */
  private func applyTileMetrics(to content: inout UIListContentConfiguration) {
    let size = CGSize(width: IconTile.edge, height: IconTile.edge)
    content.imageProperties.reservedLayoutSize = size
    content.imageProperties.maximumSize = size
    content.imageProperties.tintColor = nil
  }

  /**
   * The initials to draw, trimmed and cut to two.
   *
   * `prefix` over `String`, so the two are two *grapheme clusters* — an initial with a combining
   * accent stays one letter rather than being split into a base and a floating diacritic.
   */
  private func monogramLetters(_ row: RowSpec) -> String? {
    guard
      let raw = row.imageMonogram?.trimmingCharacters(in: .whitespacesAndNewlines),
      !raw.isEmpty
    else { return nil }
    return String(raw.prefix(2))
  }

  /**
   * Once per message per process, and debug-only.
   *
   * `applyImage` runs on every cell configure, so printing unconditionally would be one line per
   * row per scroll — thousands of them, for the single condition it exists to make visible.
   * Android's `warnOnce` is the same guard for the same reason.
   */
  private static var warned: Set<String> = []

  private static func warnOnce(_ key: String, _ message: String) {
    #if DEBUG
    guard warned.insert(key).inserted else { return }
    print(message)
    #endif
  }

  /// Tints `secondaryLabel` when the row asked for it — the "Today" / "15:00" under a Date row.
  private func applySecondaryTint(
    _ row: RowSpec,
    to content: inout UIListContentConfiguration
  ) {
    guard row.secondaryLabelTinted == true else { return }
    content.secondaryTextProperties.color = rowTint(row) ?? .tintColor
  }

  // MARK: - Control registrations

  private func makeSwitchRegistration()
    -> UICollectionView.CellRegistration<SwitchCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      let traits = cell.traitCollection

      // The subtitle preset when there is a second line, so the label sits above it rather than the
      // two competing for one baseline. Reminders' Date row is exactly this shape.
      var content: UIListContentConfiguration =
        row.secondaryLabel != nil ? .subtitleCell() : .cell()
      content.text = row.label
      content.secondaryText = row.secondaryLabel
      content.textProperties.font = self.font(
        for: row,
        fallback: content.textProperties.font,
        traits: traits
      )
      content.textProperties.color =
        row.disabled == true
        ? .secondaryLabel
        : (self.resolver.optionalColor(\.labelColor) ?? .label)
      if let secondary = self.resolver.optionalColor(\.secondaryLabelColor) {
        content.secondaryTextProperties.color = secondary
      }
      self.applyImage(row, to: &content)
      self.applySecondaryTint(row, to: &content)
      cell.contentConfiguration = content
      self.applyBackground(to: cell, row: row)

      cell.toggle.isOn = row.on == true
      cell.toggle.isEnabled = row.disabled != true
      cell.toggle.onTintColor = self.rowTint(row)
      cell.accessories = [cell.accessory]

      // Reassigned every pass, capturing this row's id. A reused cell arrives holding the previous
      // row's closure, and reporting a change against the wrong row is a bug whose symptom — some
      // other row changing — points nowhere near recycling.
      let rowId = row.id
      cell.onChange = { [weak self] value in
        self?.onSwitchChange?(rowId, value)
      }
    }
  }

  /**
   * The `UISlider` row.
   *
   * No `contentConfiguration` at all, unlike every other registration here: the control fills the
   * row, so there is no label for the content configuration to lay out and assigning an empty one
   * would reserve its margins for nothing.
   */
  private func makeSliderRegistration()
    -> UICollectionView.CellRegistration<SliderCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      self.applyBackground(to: cell, row: row)

      cell.configure(
        value: Float(row.sliderValue ?? row.sliderMin ?? 0),
        minimum: Float(row.sliderMin ?? 0),
        // Guarded rather than trusted: an inverted range makes `UISlider` unusable rather than
        // throwing, which is harder to diagnose than a crash, and a caller building the bound from
        // data can produce one by accident.
        maximum: Float(max(row.sliderMax ?? 1, (row.sliderMin ?? 0) + .ulpOfOne)),
        step: Float(row.sliderStep ?? 0),
        minimumImage: row.sliderMinImage.flatMap { UIImage(systemName: $0) },
        maximumImage: row.sliderMaxImage.flatMap { UIImage(systemName: $0) },
        tint: self.rowTint(row),
        enabled: row.disabled != true
      )

      // Reassigned every pass, capturing this row's id — a reused cell arrives holding the previous
      // row's closures, and a slider reporting against the wrong row moves a control the user is
      // not touching.
      let rowId = row.id
      cell.onChange = { [weak self] value in
        self?.onSliderChange?(rowId, Double(value))
      }
      cell.onCommit = { [weak self] value in
        self?.onSliderCommit?(rowId, Double(value))
      }
    }
  }

  private func makeTextFieldRegistration()
    -> UICollectionView.CellRegistration<TextFieldCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      cell.configure(
        row: row,
        labelFont: self.rowFont(row, traits: cell.traitCollection),
        labelColor: self.resolver.optionalColor(\.labelColor),
        tint: self.rowTint(row)
      )
      self.applyBackground(to: cell, row: row)
      cell.accessories = self.accessories(for: row)

      let rowId = row.id
      cell.onChange = { [weak self] text in self?.onTextChange?(rowId, text) }
      cell.onFocusChange = { [weak self] focused in
        self?.onFocusChange?(rowId, focused)
        self?.focusDidChange(focused)
      }
    }
  }

  private func makeTextAreaRegistration()
    -> UICollectionView.CellRegistration<TextAreaCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      cell.configure(
        row: row,
        labelFont: self.rowFont(row, traits: cell.traitCollection),
        labelColor: self.resolver.optionalColor(\.labelColor),
        tint: self.rowTint(row)
      )
      self.applyBackground(to: cell, row: row)
      cell.accessories = self.accessories(for: row)

      let rowId = row.id
      cell.onChange = { [weak self] text in self?.onTextChange?(rowId, text) }
      cell.onFocusChange = { [weak self] focused in
        self?.onFocusChange?(rowId, focused)
        self?.focusDidChange(focused)
      }
      // Auto Layout re-measures the cell on its own, but the layout keeps the height it already
      // computed — so without this the text runs past the cell's bottom edge as it grows.
      cell.onHeightChange = { [weak self] in
        self?.invalidateItemHeights()
      }
    }
  }

  private func makeMenuRegistration()
    -> UICollectionView.CellRegistration<MenuCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      let traits = cell.traitCollection
      let font = self.rowFont(row, traits: traits)

      var content: UIListContentConfiguration =
        row.secondaryLabel != nil ? .subtitleCell() : .cell()
      content.text = row.label
      content.secondaryText = row.secondaryLabel
      content.textProperties.font = font
      content.textProperties.color =
        row.disabled == true
        ? .secondaryLabel
        : (self.resolver.optionalColor(\.labelColor) ?? .label)
      self.applyImage(row, to: &content)
      self.applySecondaryTint(row, to: &content)
      cell.contentConfiguration = content
      self.applyBackground(to: cell, row: row)

      cell.configure(row: row, font: font, tint: self.rowTint(row))
      cell.accessories = [cell.accessory]

      let rowId = row.id
      cell.onSelect = { [weak self] itemId in self?.onMenuSelect?(rowId, itemId) }
    }
  }

  private func makeCardRegistration()
    -> UICollectionView.CellRegistration<CardCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      let traits = cell.traitCollection
      let base = self.rowFont(row, traits: traits)
      cell.configure(
        row: row,
        // Derived from the row's resolved font rather than hard-coded, so a card follows the list's
        // typeface and Dynamic Type without three more appearance fields.
        titleFont: .systemFont(ofSize: base.pointSize * 0.82, weight: .semibold),
        valueFont: .systemFont(ofSize: base.pointSize * 1.9, weight: .bold),
        captionFont: .systemFont(ofSize: base.pointSize * 0.82, weight: .regular),
        tint: self.rowTint(row),
        labelColor: self.resolver.optionalColor(\.labelColor)
      )
      self.applyBackground(to: cell, row: row)
      cell.accessories = self.accessories(for: row)
    }
  }

  private func makeChipRegistration()
    -> UICollectionView.CellRegistration<ChipCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      let base = self.rowFont(row, traits: cell.traitCollection)
      cell.configure(
        row: row,
        font: .systemFont(ofSize: base.pointSize * 0.88, weight: .medium),
        tint: self.rowTint(row),
        // The unselected pill sits on the list background, so it needs a fill that reads as raised
        // against it rather than the row background a grouped card would use.
        unselected: self.resolver.optionalColor(\.rowBackground) ?? .secondarySystemFill
      )
    }
  }

  private func makeDateRegistration(
    compact: Bool
  ) -> UICollectionView.CellRegistration<DatePickerCell, RowSpec> {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      cell.install(compact: compact)
      cell.configure(
        row: row,
        font: self.rowFont(row, traits: cell.traitCollection),
        labelColor: self.resolver.optionalColor(\.labelColor),
        tint: self.rowTint(row)
      )
      self.applyBackground(to: cell, row: row)

      let rowId = row.id
      cell.onChange = { [weak self] millis in self?.onDateChange?(rowId, millis) }
    }
  }

  /**
   * Installs a cell's background, preserving the pressed state.
   *
   * **The unthemed case assigns `nil`, and that is the fix rather than an omission.** A
   * `UIBackgroundConfiguration` is a snapshot resolved for one cell state; assigning the default one
   * pins the cell to that state's appearance, so the row stopped greying under a finger. `nil`
   * restores `automaticallyUpdatesBackgroundConfiguration`, and UIKit then re-resolves its own
   * configuration for every state change — the pressed style included, correct in both modes, for
   * free. Nil is still a complete specification, so the reuse rule is satisfied: the assignment
   * always happens, it just sometimes says "you own this".
   *
   * A caller who sets `rowBackground` replaces that configuration and would lose the highlight with
   * it, so for themed rows the state has to be handled explicitly — through
   * `configurationUpdateHandler`, which UIKit calls on every state change, rather than through a
   * value captured once at configure time.
   */
  private func applyBackground(to cell: UICollectionViewCell, row: RowSpec) {

    // **Only ever assigned from inside the update handler, never from the configure pass.**
    //
    // Assigning `backgroundConfiguration` directly here — including assigning `nil` to hand the job
    // back to UIKit — momentarily clears the cell's background before the new one resolves. That is
    // invisible in isolation and very visible in aggregate: a tree update reconfigures *every*
    // surviving row, so one tap made the whole list shimmer grey as each cell flashed through
    // transparent to the list background behind it.
    //
    // Doing it in the handler fixes that by construction, because UIKit calls the handler only when
    // the state actually changes, not on every reconfigure. `defaultBackgroundConfiguration()`
    // resolves whatever is correct for this cell's list appearance — corner radii included — and
    // `updated(for:)` applies the state to it, which together is exactly what
    // `automaticallyUpdatesBackgroundConfiguration` does internally. Reconstructing it by hand is
    // what lets a themed row keep a pressed state at all.
    // **Installed once per cell, never reassigned.** Assigning `configurationUpdateHandler` forces
    // an immediate configuration update, so re-installing it on every configure pass re-ran the
    // handler with whatever state the cell held at that moment. A press that changes state — a
    // checkbox, a radio — triggers a reconfigure a few milliseconds later, which reset the cell's
    // appearance while the finger was still down and cancelled the grey before it rendered. The
    // rows that most obviously *do* something were the ones that looked least responsive.
    //
    // Nothing row-specific is captured, which is what makes installing once correct: the themed
    // colour is a property of the *list*, so it is read from the resolver at call time and a theme
    // change is picked up without the handler ever being replaced.
    guard cell.configurationUpdateHandler == nil else { return }
    cell.configurationUpdateHandler = { [weak self] cell, state in
      guard let self else { return }
      var background = self.defaultBackground(for: cell).updated(for: state)
      if let themed = self.resolver.optionalColor(\.rowBackground) {
        background.backgroundColor =
          state.isHighlighted || state.isSelected
          ? themed.rnguiOverlaid(with: .label, alpha: 0.08)
          : themed
      }
      cell.backgroundConfiguration = background
    }
  }

  /// Whatever UIKit considers correct for the cell's current list appearance, corner radii included.
  /// Only exists by hand below iOS 16, where `defaultBackgroundConfiguration()` is unavailable.
  private func defaultBackground(for cell: UICollectionViewCell) -> UIBackgroundConfiguration {
    if #available(iOS 16.0, *) {
      return cell.defaultBackgroundConfiguration()
    }
    return listAppearance == .plain ? .listPlainCell() : .listGroupedCell()
  }

  /**
   * Re-measures self-sizing cells without touching their contents.
   *
   * An empty invalidation context re-runs sizing only; `reloadItems` would tear the cell down and
   * take the first responder with it, which is exactly the wrong thing while someone is typing into
   * a growing text area.
   */
  private func invalidateItemHeights() {
    collectionView.collectionViewLayout.invalidateLayout(
      with: UICollectionViewLayoutInvalidationContext()
    )
  }

  /**
   * Builds the swipe configuration for one row from its descriptor.
   *
   * `completion(false)` even for a destructive action, deliberately. Passing `true` tells UIKit the
   * row is gone and it plays a delete animation — but removal here is JavaScript's decision, and it
   * arrives in a later commit. Claiming the deletion already happened leaves the layout and the data
   * source disagreeing, which is a crash rather than a glitch. Springing the row back and letting
   * the next snapshot animate it away is both honest and what a declarative list should do.
   */
  private func swipeActions(
    at indexPath: IndexPath,
    trailing: Bool
  ) -> UISwipeActionsConfiguration? {
    guard
      let rowId = dataSource.itemIdentifier(for: indexPath),
      let row = rowsById[rowId]
    else { return nil }

    let specs = trailing ? row.trailingActions : row.leadingActions
    guard let specs, !specs.isEmpty else { return nil }

    let actions = specs.map { spec -> UIContextualAction in
      let action = UIContextualAction(
        style: spec.style == .destructive ? .destructive : .normal,
        title: spec.title
      ) { [weak self] _, _, completion in
        self?.onSwipeAction?(rowId, spec.id)
        completion(false)
      }
      if let name = spec.systemImage {
        action.image = UIImage(systemName: name)
      }
      if let hex = spec.backgroundColor, let color = UIColor(rnguiHex: hex) {
        action.backgroundColor = color
      }
      return action
    }
    return UISwipeActionsConfiguration(actions: actions)
  }

  private func makeHostRegistration()
    -> UICollectionView.CellRegistration<HostCell, RowSpec>
  {
    UICollectionView.CellRegistration { [weak self] cell, _, row in
      guard let self else { return }
      cell.setHeight(CGFloat(row.height ?? 0))

      // The child may not have mounted yet — Fabric can deliver props before it delivers
      // children — so a missing view here is expected rather than an error. `setHostedViews`
      // reconfigures these rows once the children arrive.
      if let index = row.hostIndex, let view = self.hostedViews[safe: index] {
        cell.attach(view, parkingView: self.container)
      } else {
        cell.detach()
      }
    }
  }

  /**
   * The trailing button on a section header — "See All", "Edit".
   *
   * A `UICellAccessory` on the header's own list cell rather than a hand-laid-out subview, which is
   * the whole reason headers are registered as `UICollectionViewListCell`s: the accessory system
   * already knows where the trailing edge is, how far to inset from it, and how to make room in the
   * content next to it.
   *
   * `UIButton.Configuration.plain()` rather than a bare title, so the button keeps its own pressed
   * and disabled states — a header button that does not dim under a finger is the sort of thing
   * nobody names but everybody notices.
   */
  private func headerAccessories(for section: SectionSpec) -> [UICellAccessory] {
    guard let action = section.action, section.header != nil else { return [] }

    var configuration = UIButton.Configuration.plain()
    configuration.title = action.title
    if let name = action.systemImage {
      configuration.image = UIImage(systemName: name)
    }
    // No padding: the accessory placement already supplies the trailing inset, and the button's own
    // would push the label away from the edge every other trailing accessory sits on.
    configuration.contentInsets = .zero
    configuration.baseForegroundColor = resolver.optionalColor(\.tintColor) ?? .tintColor

    let button = UIButton(configuration: configuration)
    button.isEnabled = action.disabled != true

    let sectionId = section.id
    button.addAction(
      UIAction { [weak self] _ in self?.onSectionAction?(sectionId) },
      for: .primaryActionTriggered
    )

    return [.customView(
      configuration: .init(customView: button, placement: .trailing(displayed: .always))
    )]
  }

  private func makeBoundaryRegistration(
    kind: String
  ) -> UICollectionView.SupplementaryRegistration<UICollectionViewListCell> {
    UICollectionView.SupplementaryRegistration(elementKind: kind) {
      [weak self] view, _, indexPath in
      self?.configureBoundary(view, kind: kind, sectionIndex: indexPath.section)
    }
  }

  /**
   * Refreshes the headers and footers already on screen.
   *
   * **Nothing else does.** `reconfigureItems` is items only, and a diffable snapshot carries section
   * *identifiers* rather than section content — so a section whose header text, footer text or
   * action changed while its id stayed the same produces a snapshot UIKit sees as unchanged, and
   * the supplementary view keeps whatever it was last given. That is how a "Show All" button could
   * toggle its rows correctly and never change its own title.
   *
   * Only the visible ones need it: anything off screen is dequeued fresh on its way in and picks up
   * the current spec then. There are at most a handful visible, so this is cheap enough to run on
   * every tree update rather than trying to work out whether it was needed.
   */
  private func refreshVisibleBoundaries() {
    for kind in [
      UICollectionView.elementKindSectionHeader,
      UICollectionView.elementKindSectionFooter,
    ] {
      for indexPath in collectionView.indexPathsForVisibleSupplementaryElements(ofKind: kind) {
        guard
          let view = collectionView.supplementaryView(forElementKind: kind, at: indexPath)
            as? UICollectionViewListCell
        else { continue }
        configureBoundary(view, kind: kind, sectionIndex: indexPath.section)
      }
    }
  }

  private func configureBoundary(
    _ view: UICollectionViewListCell,
    kind: String,
    sectionIndex: Int
  ) {
    guard let section = sections[safe: sectionIndex] else { return }
    let isHeader = kind == UICollectionView.elementKindSectionHeader
    let traits = view.traitCollection
    let isPlain = listAppearance == .plain

    // The plain presets are not merely the grouped ones restyled: a plain header is
    // left-aligned, title-cased and heavier, which is what an alphabet index needs, whereas
    // the grouped header is the small uppercase label a Settings group uses.
    var content: UIListContentConfiguration
    switch (isHeader, isPlain) {
    case (true, true): content = .plainHeader()
    case (true, false): content = .groupedHeader()
    case (false, true): content = .plainFooter()
    case (false, false): content = .groupedFooter()
    }
    content.text = isHeader ? section.header : section.footer

    let colorKeyPath: KeyPath<Appearance, String?> =
      isHeader ? \.headerTextColor : \.footerTextColor
    if let color = self.resolver.optionalColor(colorKeyPath) {
      content.textProperties.color = color
    }

    // Falls back to the root `font` when no header- or footer-specific spec is set, which is
    // what makes "use my typeface everywhere" a one-line change.
    let fontSpec =
      self.resolver.value({ isHeader ? $0.headerFont : $0.footerFont }, for: traits)
      ?? self.resolver.value({ $0.font }, for: traits)
    content.textProperties.font = FontResolver.resolve(
      fontSpec,
      fallback: content.textProperties.font
    )

    view.contentConfiguration = content

    // Assigned unconditionally, including the empty case: this is a reused view, and a header
    // that once had a button would otherwise keep it after scrolling into a section with none.
    view.accessories = isHeader ? self.headerAccessories(for: section) : []

    // A pinned header slides over the rows beneath it, so it has to be opaque or the two sets
    // of text overlap while scrolling. Assigned on every pass, never conditionally — this is a
    // reused view, and a header that was once opaque would otherwise stay opaque after the list
    // switched to a grouped appearance.
    var background: UIBackgroundConfiguration = .listPlainHeaderFooter()
    if isHeader, isPlain {
      // A pinned header slides over the rows beneath it, so by default it must be opaque or the
      // two sets of text overlap. `blurred` is the deliberate exception — Contacts lets its rows
      // stay visible *through* the header, which only works because it is a material rather than
      // a translucent colour: `UIBackgroundConfiguration.visualEffect` is what UIKit provides for
      // exactly this, and it keeps the vibrancy and the automatic dark-mode behaviour.
      switch self.resolver.value({ $0.headerBackgroundStyle }, for: traits) ?? .opaque {
      case .blurred:
        background.backgroundColor = .clear
        background.visualEffect = UIBlurEffect(style: .systemChromeMaterial)
      case .soft:
        background.backgroundColor = .clear
        // Reused when this header already has one. The registration handler runs on dequeue and
        // on every reconfigure, and a blur view allocated per pass would be a new one on every
        // tree update for every visible section.
        background.customView =
          (view.backgroundConfiguration?.customView as? SoftHeaderBackgroundView)
          ?? SoftHeaderBackgroundView()
      case .transparent:
        background.backgroundColor = .clear
      case .opaque, .unknown:
        background.backgroundColor =
          self.resolver.optionalColor(\.background) ?? .secondarySystemBackground
      }
    }
    view.backgroundConfiguration = background
  }

  // MARK: - Data

  private func configureDataSource() {
    dataSource = UICollectionViewDiffableDataSource<String, String>(
      collectionView: collectionView
    ) { [weak self] collectionView, indexPath, rowId in
      guard let self, let row = self.rowsById[rowId] else { return nil }
      switch row.kind {
      case .host:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.hostRegistration, for: indexPath, item: row
        )
      case .slider:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.sliderRegistration, for: indexPath, item: row
        )
      case .switch:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.switchRegistration, for: indexPath, item: row
        )
      case .textField:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.textFieldRegistration, for: indexPath, item: row
        )
      case .textArea:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.textAreaRegistration, for: indexPath, item: row
        )
      case .menu:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.menuRegistration, for: indexPath, item: row
        )
      case .card:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.cardRegistration, for: indexPath, item: row
        )
      case .chip:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.chipRegistration, for: indexPath, item: row
        )
      case .datePicker:
        // The style decides the pool, not just the configuration — a compact pill and an inline
        // calendar have irreconcilable intrinsic sizes.
        let registration =
          DatePickerCell.isExpanded(row.datePickerStyle)
          ? self.expandedDateRegistration!
          : self.compactDateRegistration!
        return collectionView.dequeueConfiguredReusableCell(
          using: registration, for: indexPath, item: row
        )
      // `default`, `value`, `subtitle`, `button` and anything a newer JS bundle knows about that
      // this binary does not all render as a stock list cell. That last case is the point: an
      // unrecognised kind decodes to `.unknown` and degrades to a plain row rather than vanishing.
      default:
        return collectionView.dequeueConfiguredReusableCell(
          using: self.listRegistration, for: indexPath, item: row
        )
      }
    }

    dataSource.supplementaryViewProvider = { [weak self] collectionView, kind, indexPath in
      guard let self else { return nil }
      // Force-unwrapped at the selection site: a ternary over two implicitly unwrapped
      // properties infers an `Optional` result, and by this point both have been assigned in
      // `init`.
      let registration =
        kind == UICollectionView.elementKindSectionHeader
        ? self.headerRegistration!
        : self.footerRegistration!
      return collectionView.dequeueConfiguredReusableSupplementary(
        using: registration, for: indexPath
      )
    }
  }

  // MARK: - Props

  /// Decodes and applies the descriptor tree. Called only when `revision` changed.
  @objc public func applyTreeJSON(_ json: String) {
    guard let data = json.data(using: .utf8), !data.isEmpty else {
      apply(tree: Tree())
      return
    }

    do {
      #if DEBUG
      let startedDecode = CFAbsoluteTimeGetCurrent()
      #endif

      let tree = try JSONDecoder().decode(Tree.self, from: data)

      #if DEBUG
      let decoded = CFAbsoluteTimeGetCurrent()
      #endif

      apply(tree: tree)

      #if DEBUG
      // The transport choice rests on this being cheap — the whole tree crosses as one string
      // precisely because a typed-prop equivalent would mean hundreds of thousands of hash
      // lookups per commit. An assumption that load-bearing should report when it stops holding,
      // rather than being re-measured by hand every few months.
      //
      // The threshold is half a 120Hz frame. Updating the tree is only ever part of a commit, so
      // taking more than that means the list can no longer absorb a content change without
      // dropping a frame.
      let decodeMs = (decoded - startedDecode) * 1000
      let applyMs = (CFAbsoluteTimeGetCurrent() - decoded) * 1000
      if decodeMs + applyMs > 4 {
        let rows = tree.sections.reduce(0) { $0 + $1.rows.count }
        print(
          "[@rngui/collection-view] tree update: decode \(String(format: "%.1f", decodeMs))ms + "
            + "apply \(String(format: "%.1f", applyMs))ms for \(rows) rows in "
            + "\(tree.sections.count) sections (\(data.count / 1024)KB)."
        )
      }
      #endif
    } catch {
      // A decode failure here means the contract drifted, not that the user did something wrong,
      // so it must be loud in development and survivable in production.
      #if DEBUG
      print("[@rngui/collection-view] Failed to decode tree: \(error)")
      #endif
    }
  }

  /**
   * Pins the interface style, or follows the device when `system`.
   *
   * Set on the container so it propagates to the collection view, every cell, and every control
   * inside one. That reach is the point: `UIListContentConfiguration` labels, separators and
   * accessories draw with system colours that otherwise follow the *device*, so an app-level dark
   * theme on a light-mode phone would produce dark rows with black text.
   */
  @objc public func setColorScheme(_ raw: String) {
    let style: UIUserInterfaceStyle
    switch raw {
    case "light": style = .light
    case "dark": style = .dark
    default: style = .unspecified
    }
    guard container.overrideUserInterfaceStyle != style else { return }
    container.overrideUserInterfaceStyle = style
  }

  @objc public func setShowsSectionIndex(_ shows: Bool) {
    guard showsSectionIndex != shows else { return }
    showsSectionIndex = shows
    updateSectionIndex()
  }

  /// `0` means automatic — codegen has no representation of an absent number, so the sentinel is
  /// the boundary's way of saying "unset".
  @objc public func setSectionIndexRowHeight(_ height: CGFloat) {
    sectionIndexBar.rowHeight = height > 0 ? height : nil
  }

  @objc public func setSectionIndexShowsCallout(_ shows: Bool) {
    sectionIndexBar.showsCallout = shows
  }

  // MARK: - Insets

  @objc public func setContentInset(_ inset: UIEdgeInsets) {
    guard baseContentInset != inset else { return }
    baseContentInset = inset
    applyContentInset()
  }

  /// `0`–`3`, in the order the spec's enum declares them. An integer rather than a string because
  /// this one maps directly onto a `UIScrollView` enum with the same shape.
  @objc public func setContentInsetAdjustmentBehavior(_ raw: Int) {
    switch raw {
    case 1: insetBehavior = .scrollableAxes
    case 2: insetBehavior = .never
    case 3: insetBehavior = .always
    default: insetBehavior = .automatic
    }
    applyInsetBehavior()
  }

  @objc public func setAutomaticallyAdjustContentInsets(_ adjusts: Bool) {
    guard adjustsContentInsets != adjusts else { return }
    adjustsContentInsets = adjusts
    applyInsetBehavior()
  }

  @objc public func setAutomaticallyAdjustsScrollIndicatorInsets(_ adjusts: Bool) {
    collectionView.automaticallyAdjustsScrollIndicatorInsets = adjusts
  }

  /// `automaticallyAdjustContentInsets: false` wins, because it is the coarser switch — a caller
  /// who turns adjustment off entirely means it regardless of the finer setting alongside it.
  private func applyInsetBehavior() {
    collectionView.contentInsetAdjustmentBehavior =
      adjustsContentInsets ? insetBehavior : .never
  }

  private func applyContentInset() {
    var inset = baseContentInset
    // Never *shrinks* below what the caller asked for — the keyboard adds room, it does not
    // replace the caller's own bottom inset.
    inset.bottom = max(baseContentInset.bottom, keyboardOverlap)
    collectionView.contentInset = inset

    // Left alone when UIKit is managing them, or the assignment fights `automaticallyAdjusts…`
    // and the indicator ends up inset twice.
    if !collectionView.automaticallyAdjustsScrollIndicatorInsets {
      collectionView.verticalScrollIndicatorInsets.bottom = inset.bottom
    }
  }

  // MARK: - Keyboard

  @objc public func setAutomaticallyAdjustKeyboardInsets(_ adjusts: Bool) {
    adjustsKeyboardInsets = adjusts
  }

  @objc public func setKeyboardAware(_ aware: Bool) {
    keyboardAware = aware
  }

  @objc public func setKeyboardAwareOffset(_ offset: CGFloat) {
    keyboardAwareOffset = offset
  }

  /**
   * `ScrollView`'s prop of the same name, in the order codegen assigns its cases.
   *
   * An `Int` across the boundary because that is what a codegen string enum becomes; the mapping
   * lives here rather than in the `.mm` so there is one table rather than two that must agree.
   */
  enum PersistTaps: Int {
    case never = 0
    case always = 1
    case handled = 2
  }

  private var persistTaps: PersistTaps = .never

  /**
   * Resigns the first responder on a tap, according to `keyboardShouldPersistTaps`.
   *
   * `handled` asks whether the row under the finger would have *done* anything: a row with an
   * `onPress` handled the tap, so the keyboard stays; a decorative row, a switch row with no press,
   * or the background did not, so it goes. That is as close to `ScrollView`'s meaning as a list can
   * get, where "the child" and "the row" are the same object.
   */
  @objc private func handleDismissTap(_ recognizer: UITapGestureRecognizer) {
    guard collectionView.rnguiFindFirstResponder() != nil else { return }

    switch persistTaps {
    case .always:
      return
    case .never:
      collectionView.endEditing(true)
    case .handled:
      let point = recognizer.location(in: collectionView)
      let handled =
        collectionView.indexPathForItem(at: point)
        .flatMap { dataSource.itemIdentifier(for: $0) }
        .flatMap { rowsById[$0]?.selectable } == true
      if !handled { collectionView.endEditing(true) }
    }
  }

  @objc public func setKeyboardShouldPersistTaps(_ raw: Int) {
    persistTaps = PersistTaps(rawValue: raw) ?? .never
  }

  @objc public func setKeyboardDismissMode(_ raw: Int) {
    switch raw {
    case 1: collectionView.keyboardDismissMode = .onDrag
    case 2: collectionView.keyboardDismissMode = .interactive
    default: collectionView.keyboardDismissMode = .none
    }
  }

  private func applyKeyboardOverlap(
    _ overlap: CGFloat,
    duration: TimeInterval,
    options: UIView.AnimationOptions
  ) {
    guard adjustsKeyboardInsets || keyboardAware else { return }
    guard keyboardOverlap != overlap else { return }
    keyboardOverlap = overlap

    UIView.animate(withDuration: duration, delay: 0, options: options) {
      self.applyContentInset()
      // Inside the same animation block on purpose: the inset and the scroll have to move together
      // or the focused row visibly slides twice.
      if self.keyboardAware, overlap > 0 {
        self.scrollFocusedInputIntoView(animated: false)
      }
    }
  }

  /**
   * Scrolls the focused field into view, preferring the **caret** to the row.
   *
   * The difference matters for a text area that has grown: centring the row puts a tall cell's
   * midpoint on screen, which can leave the line actually being typed underneath the keyboard.
   * `UITextInput.caretRect(for:)` is what gives the real target.
   *
   * Also called when focus moves between fields with the keyboard already up — a case
   * `keyboardWillChangeFrame` never fires for, so nothing else would notice.
   */
  /**
   * Re-scrolls when focus moves between fields with the keyboard already up.
   *
   * `keyboardWillChangeFrame` does not fire for that — the keyboard never moves — so without this
   * hook, tabbing from a field near the top to one behind the keyboard leaves the caret hidden.
   * Deferred a turn so the new responder's caret rect is valid; asking during the transition
   * returns the *old* field's geometry.
   */
  private func focusDidChange(_ focused: Bool) {
    guard focused, keyboardAware, keyboardOverlap > 0 else { return }
    Task { @MainActor [weak self] in
      self?.scrollFocusedInputIntoView(animated: true)
    }
  }

  func scrollFocusedInputIntoView(animated: Bool) {
    guard keyboardAware, let responder = collectionView.rnguiFindFirstResponder() else { return }

    var target: CGRect
    if let input = responder as? UITextInput, let range = input.selectedTextRange {
      target = responder.convert(input.caretRect(for: range.end), to: collectionView)
    } else {
      target = responder.convert(responder.bounds, to: collectionView)
    }
    // Guards against a zero-height caret rect on an empty field, which would scroll to a point and
    // leave the row flush against the keyboard.
    target = target.insetBy(dx: 0, dy: -(keyboardAwareOffset + 8))

    collectionView.scrollRectToVisible(target, animated: animated)
  }

  @objc public func setShowsVerticalScrollIndicator(_ shows: Bool) {
    collectionView.showsVerticalScrollIndicator = shows
    // The inset the section index reserves is only meaningful while the indicator is drawn, so
    // recompute it here too rather than leaving a gap the scrubber no longer needs.
    updateSectionIndex()
  }

  @objc public func setScrollEnabled(_ enabled: Bool) {
    collectionView.isScrollEnabled = enabled
  }

  /// Negative is the sentinel for "unset" — see the note on the prop. Any other value is passed
  /// through untouched, including `0`, which is a bottom sheet asking the list to stop dead.
  @objc public func setDecelerationRate(_ rate: Double) {
    collectionView.decelerationRate =
      rate < 0 ? .normal : UIScrollView.DecelerationRate(rawValue: rate)
  }

  @objc public func setTracksScroll(_ tracks: Bool) {
    guard tracksScroll != tracks else { return }
    tracksScroll = tracks
    // Cleared so that turning tracking back on reports the current size rather than staying silent
    // because it happens to match whatever was last sent.
    lastEmittedContentSize = nil
    if tracks { contentSizeDidChange(collectionView.contentSize) }
  }

  // MARK: - Pull to refresh

  /// Attaching the control is what enables the gesture, so this is the on switch rather than a
  /// flag the control consults.
  @objc public func setRefreshEnabled(_ enabled: Bool) {
    guard refreshEnabled != enabled else { return }
    refreshEnabled = enabled
    if enabled {
      collectionView.refreshControl = refreshControl
      if desiredRefreshing { beginRefreshingProgrammatically() }
    } else {
      refreshControl.endRefreshing()
      collectionView.refreshControl = nil
    }
  }

  /**
   * Reached by the `refreshing` prop *and* by the `setNativeRefreshing` command.
   *
   * **The guard reads the control's own state, never a stored copy of the prop, and that is the
   * load-bearing detail.** After a user pull the control is spinning while the prop is still
   * `false`, so the command sends `false` — a prop-value guard would see no change, swallow the
   * correction, and leave the spinner up forever. Which is the exact bug the command exists to
   * fix.
   */
  @objc public func setRefreshing(_ refreshing: Bool) {
    desiredRefreshing = refreshing
    guard refreshControl.isRefreshing != refreshing else { return }
    if refreshing {
      beginRefreshingProgrammatically()
    } else {
      refreshControl.endRefreshing()
    }
  }

  @objc public func setRefreshTintColor(_ color: UIColor?) {
    refreshControl.tintColor = color
  }

  /// Both halves in one call, so a colour change with an unchanged title still repaints.
  @objc public func setRefreshTitle(_ title: String?, color: UIColor?) {
    guard let title, !title.isEmpty else {
      refreshControl.attributedTitle = nil
      return
    }
    var attributes: [NSAttributedString.Key: Any] = [:]
    if let color { attributes[.foregroundColor] = color }
    refreshControl.attributedTitle = NSAttributedString(
      string: title,
      attributes: attributes
    )
  }

  /**
   * Moves what the control *draws* without moving the frame UIKit owns.
   *
   * `bounds.origin.y`, not `frame`. `scrollView.refreshControl` positions the control itself
   * against `adjustedContentInset`, and overwriting the frame fights that — the legacy
   * `RCTRefreshControl` has a long note about exactly this. Shifting the bounds slides the
   * content inside an unchanged frame instead.
   *
   * Normally `0` is what you want here: `contentInsetAdjustmentBehavior` has already put the
   * control below the navigation bar. The prop exists for parity, and for an overlay header UIKit
   * knows nothing about.
   */
  @objc public func setRefreshProgressViewOffset(_ offset: CGFloat) {
    refreshControl.bounds = CGRect(
      x: refreshControl.bounds.origin.x,
      y: -offset,
      width: refreshControl.bounds.width,
      height: refreshControl.bounds.height
    )
  }

  /**
   * Starts the spinner and scrolls it into view, which are two separate things.
   *
   * `beginRefreshing()` does not move the content offset — UIKit parks the control above the top
   * of the content, so at rest it spins off-screen and the list looks frozen. Hence the nudge.
   *
   * Three guards, each earning its place:
   *
   * - **Not before the control is in a window.** `beginRefreshing()` is silently dropped until
   *   then, which is why `ContainerView.onDidLayout` retries. `sizeToFit()` for the same family of
   *   reason: the frame is zero-height until it is asked, and a nudge of zero moves nothing.
   * - **Only from the top.** React Native nudges unconditionally, which yanks a list the user had
   *   scrolled 500pt down. Here a refresh started from further in simply spins where the control
   *   already is, and comes into view if the user scrolls up — which is what a `UIRefreshControl`
   *   does for every refresh it was not the cause of.
   * - **Not while scrolling is off.** That is a bottom sheet holding the list at a fixed offset,
   *   and moving it would fight the sheet's per-frame correction for as long as the drag lasts.
   */
  private func beginRefreshingProgrammatically() {
    guard refreshEnabled, !refreshControl.isRefreshing, collectionView.window != nil else {
      return
    }
    refreshControl.sizeToFit()
    refreshControl.beginRefreshing()

    let top = -collectionView.adjustedContentInset.top
    guard collectionView.isScrollEnabled, collectionView.contentOffset.y <= top else { return }
    collectionView.setContentOffset(
      CGPoint(x: collectionView.contentOffset.x, y: top - refreshControl.frame.height),
      animated: true
    )
  }

  /**
   * No suppression flag, and that is deliberate rather than an omission.
   *
   * `UIRefreshControl` sends `.valueChanged` only for the user's pull; `beginRefreshing()` and
   * `endRefreshing()` send no control events at all. Both of React Native's implementations rely
   * on this and guard nothing. A flag here would be inventing a bug in order to fix it.
   */
  @objc private func refreshControlValueChanged() {
    onRefresh?()
  }

  /**
   * The `scrollTo` command, clamped the way `RCTScrollViewComponentView` clamps it.
   *
   * Clamping is not politeness — an out-of-range `contentOffset` set during a gesture leaves the
   * scroll view outside its own bounds with no rubber-banding to bring it back, so the list sticks
   * until the next touch. `adjustedContentInset` rather than the caller's `contentInset`, because
   * that is what actually bounds this scroll view once UIKit has folded in the surrounding chrome.
   */
  /// The selector is spelled out rather than left to Swift's inference, because the `.mm` has to
  /// name it exactly and `scrollTo(x:y:animated:)` does not obviously become `scrollToX:y:animated:`.
  @objc(scrollToX:y:animated:)
  public func scrollTo(x: Double, y: Double, animated: Bool) {
    let inset = collectionView.adjustedContentInset
    let viewport = collectionView.bounds.size
    let content = collectionView.contentSize

    let minX = min(-inset.left, 0)
    let minY = min(-inset.top, 0)
    let target = CGPoint(
      x: min(max(x, minX), max(content.width - viewport.width + inset.right, minX)),
      y: min(max(y, minY), max(content.height - viewport.height + inset.bottom, minY))
    )

    // The early return is what makes this safe to call from inside `scrollViewDidScroll`, which is
    // exactly where a bottom sheet calls it: setting the offset re-enters the delegate, and without
    // this the second pass would set it again.
    guard target != collectionView.contentOffset else { return }
    collectionView.setContentOffset(target, animated: animated)

    // Unanimated, UIKit runs none of the end-of-scroll workflow: no `scrollViewDidEndScrollingAnimation`,
    // and not reliably a `scrollViewDidScroll` either. `RCTScrollViewComponentView` runs it by hand
    // for exactly this reason (`_handleFinishedScrolling:`), and a bottom sheet is written against
    // that behaviour — it waits for a momentum end to decide the list has settled. Without it the
    // sheet keeps correcting a list that already stopped, which is what makes an overscroll judder.
    if !animated {
      emit(onScroll)
      emit(onMomentumScrollEnd)
    }
  }

  @objc public func setTracksVisibleRange(_ tracks: Bool) {
    guard tracksVisibleRange != tracks else { return }
    tracksVisibleRange = tracks
    // Cleared so that turning tracking back on reports the current range rather than staying
    // silent because it happens to match whatever was last sent.
    lastEmittedRange = nil
    if tracks { scheduleVisibleRangeEmit() }
  }

  // MARK: - Section index

  private func updateSectionIndex() {
    let entries: [SectionIndexBar.Entry] =
      showsSectionIndex
      ? sections.enumerated().compactMap { index, section in
        // A section without an index title is skipped rather than given a blank stop, so a list
        // can mix indexed and unindexed sections — the scrubber's stops and the list's sections
        // are deliberately not the same sequence.
        guard let title = section.indexTitle, !title.isEmpty else { return nil }
        return SectionIndexBar.Entry(title: title, sectionIndex: index)
      }
      : []

    sectionIndexBar.setEntries(entries)
    sectionIndexBar.titleColor = resolver.optionalColor(\.tintColor)
    updateSectionIndexInsets()

    // Moved out from under the bar, exactly as the system does for an indexed table. Without
    // this the indicator draws through the letters.
    let needsInset =
      !sectionIndexBar.isHidden && collectionView.showsVerticalScrollIndicator
    collectionView.verticalScrollIndicatorInsets.right =
      needsInset ? SectionIndexBar.preferredWidth : 0
  }

  /**
   * Aligns a section's header with the top of the visible area.
   *
   * `scrollToItem(at:at:.top)` is not enough: it aligns the section's first *item*, which under a
   * pinned header means the header is covering the row you asked for. So the offset is taken from
   * the header's own layout attributes when there are any, and only falls back to the item.
   *
   * `animated: false` throughout, deliberately. The system scrubber tracks the finger frame for
   * frame; animating each jump means the list is perpetually chasing a position the finger has
   * already left, which reads as lag.
   */
  private func scrollToSection(_ index: Int) {
    guard sections.indices.contains(index), !sections[index].rows.isEmpty else { return }
    let indexPath = IndexPath(item: 0, section: index)

    let headerFrame = collectionView.collectionViewLayout
      .layoutAttributesForSupplementaryView(
        ofKind: UICollectionView.elementKindSectionHeader,
        at: indexPath
      )?.frame

    guard let top = headerFrame?.minY else {
      collectionView.scrollToItem(at: indexPath, at: .top, animated: false)
      return
    }

    // `adjustedContentInset.top` is where the *visible* area starts — under a native stack it is
    // the navigation bar's height — so the content offset that puts a frame at the top edge is
    // that frame's origin minus the inset, not the origin itself.
    let inset = collectionView.adjustedContentInset
    let maxOffset = max(
      -inset.top,
      collectionView.contentSize.height - collectionView.bounds.height + inset.bottom
    )
    let offset = min(max(top - inset.top, -inset.top), maxOffset)
    collectionView.setContentOffset(CGPoint(x: 0, y: offset), animated: false)
  }

  /// Keeps the bar inside the area the list is actually visible in, rather than running under the
  /// navigation bar and the tab bar.
  private func updateSectionIndexInsets() {
    let inset = collectionView.adjustedContentInset
    // A little breathing room so the first and last letters are not flush against the chrome.
    let padding: CGFloat = 8
    sectionIndexTopConstraint.constant = inset.top + padding
    sectionIndexBottomConstraint.constant = -(inset.bottom + padding)
  }

  // MARK: - Visible range

  /**
   * Coalesces to at most one event per run-loop turn.
   *
   * Several things can invalidate the range within a single frame — a scroll, cells appearing,
   * cells disappearing — and each of those firing its own event would put three identical
   * messages on the JavaScript thread per frame. Nothing is scheduled at all when no one is
   * listening, which is the case for almost every list.
   */
  private func scheduleVisibleRangeEmit() {
    guard tracksVisibleRange, onVisibleRangeChange != nil, !visibleRangeEmitScheduled else {
      return
    }
    visibleRangeEmitScheduled = true
    Task { @MainActor [weak self] in
      guard let self else { return }
      self.visibleRangeEmitScheduled = false
      self.emitVisibleRange()
    }
  }

  private func emitVisibleRange() {
    guard tracksVisibleRange, let emit = onVisibleRangeChange else { return }

    var first = Int.max
    var last = Int.min
    for indexPath in collectionView.indexPathsForVisibleItems {
      guard
        let rowId = dataSource.itemIdentifier(for: indexPath),
        let flat = flatIndexByRowId[rowId]
      else { continue }
      first = min(first, flat)
      last = max(last, flat)
    }

    // An empty list is reported rather than skipped: a consumer windowing hosted children has to
    // hear that the range collapsed, or it renders the last range it was told about forever.
    let range = first <= last ? (first: first, last: last) : (first: -1, last: -1)
    // Unwrapped rather than compared with `!=`: a tuple gets `==` from a set of overloads rather
    // than from an `Equatable` conformance, so `Optional` has nothing to forward to.
    if let previous = lastEmittedRange, previous == range { return }
    lastEmittedRange = range
    emit(range.first, range.last)
  }

  /**
   * The sections with duplicate identifiers removed, first occurrence winning.
   *
   * **A crash guard, not tidying.** `NSDiffableDataSourceSnapshot` raises an
   * `NSInternalInconsistencyException` — *"Fatal: supplied item identifiers are not unique"* — the
   * moment `appendItems` is handed an id the snapshot already holds, and `appendSections` behaves
   * the same way. The serializer warns about row collisions, but only under `__DEV__` and only for
   * rows, so a release build that produced one id twice used to take the whole app down.
   *
   * Rows are deduplicated **globally rather than per section**, because global is the scope
   * diffable item identifiers live in: the same id in two different sections is the same crash.
   *
   * Everything downstream derives from what this returns — `rowsById`, `flatIndexByRowId` and the
   * snapshot alike — so the flat indices `onVisibleRangeChange` reports keep addressing rows that
   * exist. Deduplicating at the snapshot alone would leave those three disagreeing about the list.
   *
   * The clean case allocates two sets and returns the original array untouched, which is why the
   * scan is separate from the rebuild: every commit pays for the check, and only a malformed tree
   * pays for the copy.
   */
  private static func deduplicated(_ sections: [SectionSpec]) -> [SectionSpec] {
    var seenSectionIds = Set<String>()
    var seenRowIds = Set<String>()
    var hasDuplicates = false

    scan: for section in sections {
      if !seenSectionIds.insert(section.id).inserted {
        hasDuplicates = true
        break scan
      }
      for row in section.rows where !seenRowIds.insert(row.id).inserted {
        hasDuplicates = true
        break scan
      }
    }

    guard hasDuplicates else { return sections }

    seenSectionIds.removeAll()
    seenRowIds.removeAll()
    var droppedSectionIds: [String] = []
    var droppedRowIds: [String] = []
    var result: [SectionSpec] = []
    result.reserveCapacity(sections.count)

    for section in sections {
      guard seenSectionIds.insert(section.id).inserted else {
        droppedSectionIds.append(section.id)
        continue
      }
      var deduped = section
      deduped.rows = section.rows.filter { row in
        guard seenRowIds.insert(row.id).inserted else {
          droppedRowIds.append(row.id)
          return false
        }
        return true
      }
      result.append(deduped)
    }

    // Dropping rows silently is the behaviour this component's own comments used to *claim* UIKit
    // had. It does not, but it is the right behaviour to fall back to — a list missing a row beats
    // an app that is gone — so the drop is unconditional and only the diagnosis is debug-only.
    // JavaScript reports the same collision under `__DEV__`, where the offending call site is
    // visible; this covers a release bundle running against a debug binary, and section ids, which
    // the serializer does not check.
    #if DEBUG
    var complaint = "[@rngui/collection-view] Duplicate identifiers dropped before they could "
    complaint += "crash UICollectionViewDiffableDataSource."
    if !droppedSectionIds.isEmpty {
      complaint += " Sections: \(droppedSectionIds.joined(separator: ", "))."
    }
    if !droppedRowIds.isEmpty {
      complaint += " Rows: \(droppedRowIds.joined(separator: ", "))."
    }
    print(complaint)
    #endif

    return result
  }

  private func apply(tree: Tree) {
    // Deduplicated *before* anything derives from it, so every structure below agrees about which
    // rows exist. See `deduplicated(_:)`: this is a crash guard, not tidying.
    let normalized = Self.deduplicated(tree.sections)

    sections = normalized
    listAppearance = tree.listAppearance ?? .insetGrouped
    resolver = AppearanceResolver(light: tree.appearance, dark: tree.darkAppearance)

    let allRows = normalized.flatMap(\.rows)
    // `uniquingKeysWith` rather than `uniqueKeysWithValues` even though `deduplicated(_:)` has
    // just guaranteed uniqueness: the latter *traps* on a duplicate, and the entire point of this
    // path is that malformed input must never take the app down. Keeping the first occurrence
    // costs nothing and matches the rule the deduplication itself applied.
    rowsById = Dictionary(
      allRows.map { ($0.id, $0) },
      uniquingKeysWith: { first, _ in first }
    )
    flatIndexByRowId = Dictionary(
      allRows.enumerated().map { ($0.element.id, $0.offset) },
      uniquingKeysWith: { first, _ in first }
    )

    var snapshot = NSDiffableDataSourceSnapshot<String, String>()
    snapshot.appendSections(normalized.map(\.id))
    for section in normalized {
      snapshot.appendItems(section.rows.map(\.id), toSection: section.id)
    }

    // Rows whose identity survived need their *contents* refreshed, and `reconfigureItems` is
    // what does that without tearing the cell down — which is what keeps a text field's first
    // responder status and caret position alive across a re-render.
    // Through a `Set`, and this is not a micro-optimisation.
    //
    // `snapshot.itemIdentifiers` is an **Array**, so `contains` is a linear scan — running it once
    // per existing row made this O(n²). Measured on the 2,000-row Contacts screen: 4 million string
    // comparisons, and `apply` went from 2.5 ms on the first commit (no surviving items, so the
    // filter is trivial) to **330–365 ms on every commit after it**. That is 40 dropped frames for
    // a re-render that changed nothing.
    //
    // It had been here since M2 and was invisible at 35 rows. Being quadratic is exactly the class
    // of bug a 2,000-row harness exists to find, and it is why the decode timing prints total cost
    // rather than decode alone — the transport was never the expensive half.
    let nextIds = Set(snapshot.itemIdentifiers)
    let previous = dataSource.snapshot()
    let surviving = previous.itemIdentifiers.filter { nextIds.contains($0) }

    /**
     * Whether the *shape* of the list changed, as opposed to only its contents.
     *
     * Comparing the identifier arrays catches inserts, removals and moves in one test, since order
     * is part of the array. Everything else — new text in a field, a switch flipping, a menu
     * choosing a different item — leaves both lists identical.
     */
    let structureChanged =
      previous.sectionIdentifiers != snapshot.sectionIdentifiers
      || previous.itemIdentifiers != snapshot.itemIdentifiers
    if !surviving.isEmpty {
      snapshot.reconfigureItems(surviving)
    }

    // Section headers and footers are **not** covered by `reconfigureItems` — that only applies to
    // items — so a theme change restyles every row and leaves the headers looking exactly as they
    // did. Reloading the sections is what recreates the supplementary views.
    //
    // Gated on the appearance actually differing, because reloading is heavier than reconfiguring,
    // and gated on there being surviving items so it never targets sections added in this same
    // snapshot.
    let appearanceChanged = tree.appearance != appliedLight || tree.darkAppearance != appliedDark
    appliedLight = tree.appearance
    appliedDark = tree.darkAppearance
    if appearanceChanged, !surviving.isEmpty {
      snapshot.reloadSections(snapshot.sectionIdentifiers)
    }

    applyChrome()
    updateSectionIndex()

    // The layout reads `sections` for header and footer modes and `listAppearance` for its
    // configuration, so it has to be re-read when either changes — but *only* then. Invalidating on
    // every tree update meant a full re-layout on every keystroke.
    if structureChanged || appearanceChanged {
      collectionView.collectionViewLayout.invalidateLayout()
    }

    /**
     * Animated only when the structure changed, which is what the row kinds made urgent.
     *
     * The previous condition — animate whenever anything survived — animated *every* commit after
     * the first, so typing a character queued an animated batch update per keystroke. Overlapping
     * animated applies visibly fought each other and were part of why typing lost characters. It is
     * also simply wrong to look at: a content-only update that animates reads as every row sliding
     * around for no reason.
     */
    dataSource.apply(snapshot, animatingDifferences: structureChanged)

    // After the apply, so `sections` and the data source agree about what is where. Nothing in a
    // diffable snapshot represents a section's *content*, so this is the only thing that gets new
    // header text, footer text or a changed action onto a header already on screen.
    refreshVisibleBoundaries()

    // The row at a given index almost certainly changed even if the visible *cells* did not, so
    // the range has to be recomputed rather than assumed stable.
    lastEmittedRange = nil
    scheduleVisibleRangeEmit()
  }

  private func reconfigureVisibleItems() {
    var snapshot = dataSource.snapshot()
    let visible = collectionView.indexPathsForVisibleItems.compactMap {
      dataSource.itemIdentifier(for: $0)
    }
    guard !visible.isEmpty else { return }
    snapshot.reconfigureItems(visible)
    dataSource.apply(snapshot, animatingDifferences: false)
  }

  // MARK: - Hosted React children

  /**
   * Called by the component view whenever React mounts or unmounts a child.
   *
   * The array is React's own ordering, which is what `RowSpec.hostIndex` refers to. Host rows are
   * reconfigured afterwards because props and children arrive in separate Fabric transactions: a
   * host row is frequently configured before its child exists.
   */
  @objc public func setHostedViews(_ views: [UIView]) {
    hostedViews = views

    let hostRowIds = sections
      .flatMap(\.rows)
      .filter { $0.kind == .host }
      .map(\.id)
    guard !hostRowIds.isEmpty else { return }

    var snapshot = dataSource.snapshot()
    let present = hostRowIds.filter { snapshot.itemIdentifiers.contains($0) }
    guard !present.isEmpty else { return }
    snapshot.reconfigureItems(present)
    dataSource.apply(snapshot, animatingDifferences: false)
  }

  /// Called before React unmounts a child, so whichever cell holds it lets go first.
  @objc public func releaseHostedView(_ view: UIView) {
    // Only the cell actually displaying this one. Detaching every visible host cell — which is what
    // this did — meant unmounting a single hosted child blanked all its neighbours until something
    // reconfigured them.
    for cell in collectionView.visibleCells {
      guard let host = cell as? HostCell, host.hostedView === view else { continue }
      host.detach()
    }
    if view.superview === container {
      view.removeFromSuperview()
    }
  }
}

// MARK: - Delegate

extension RNGUICollectionViewHost: UICollectionViewDelegate {
  /**
   * Only rows that actually do something respond to a touch.
   *
   * `selectable` is set in JavaScript from whether the row was given an `onPress`, so an
   * informational row — a label with a value, a row whose only control is a switch — never
   * highlights. Without this every row in the list greys out under a finger and then reports
   * nothing, which promises an interaction that does not exist.
   */
  public func collectionView(
    _ collectionView: UICollectionView,
    shouldSelectItemAt indexPath: IndexPath
  ) -> Bool {
    guard let rowId = dataSource.itemIdentifier(for: indexPath) else { return false }
    return rowsById[rowId]?.selectable ?? false
  }

  public func collectionView(
    _ collectionView: UICollectionView,
    shouldHighlightItemAt indexPath: IndexPath
  ) -> Bool {
    self.collectionView(collectionView, shouldSelectItemAt: indexPath)
  }

  /**
   * Selection here is *momentary*, and saying so is the entire job of this method.
   *
   * A `UICollectionView` treats selection as durable state — the grey stays until something
   * clears it — which is right for a grid of photos and wrong for a settings list, where a tap
   * means "push a screen" and the highlight should fade the way it does everywhere in iOS.
   * Deselecting here is the same thing `tableView.deselectRow(at:animated:)` does, and its
   * absence is the bug where a tapped row stays grey until some *other* row is tapped.
   *
   * Rows whose selection genuinely is durable — radio and checkbox — arrive with the rest of the
   * row kinds, and will opt out of this by kind rather than by having no delegate at all.
   */
  public func collectionView(
    _ collectionView: UICollectionView,
    didSelectItemAt indexPath: IndexPath
  ) {
    collectionView.deselectItem(at: indexPath, animated: true)
    guard let rowId = dataSource.itemIdentifier(for: indexPath) else { return }

    onRowPress?(rowId)
  }

  // MARK: Scrolling

  public func scrollViewDidScroll(_ scrollView: UIScrollView) {
    scheduleVisibleRangeEmit()
    emit(onScroll)
  }

  public func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
    emit(onScrollBeginDrag)
  }

  public func scrollViewDidEndDragging(
    _ scrollView: UIScrollView,
    willDecelerate decelerate: Bool
  ) {
    emit(onScrollEndDrag)
  }

  public func scrollViewWillBeginDecelerating(_ scrollView: UIScrollView) {
    emit(onMomentumScrollBegin)
  }

  public func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
    emit(onMomentumScrollEnd)
  }

  /**
   * Also momentum-end, which is what `ScrollView` reports and what a bottom sheet is waiting for.
   *
   * A programmatic `setContentOffset(_:animated: true)` decelerates without ever entering the
   * momentum phase, so `scrollViewDidEndDecelerating` never fires. A listener that only heard that
   * one would keep believing the list is still moving — for a sheet, that means staying locked
   * after the animation it started has already finished.
   */
  public func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
    emit(onMomentumScrollEnd)
  }

  public func scrollViewDidChangeAdjustedContentInset(_ scrollView: UIScrollView) {
    updateSectionIndexInsets()
  }

  private func emit(_ block: ((CGPoint, CGSize, CGSize, UIEdgeInsets) -> Void)?) {
    guard tracksScroll, let block else { return }
    block(
      collectionView.contentOffset,
      collectionView.contentSize,
      collectionView.bounds.size,
      collectionView.adjustedContentInset
    )
  }

  private func contentSizeDidChange(_ size: CGSize) {
    guard tracksScroll, let emit = onContentSizeChange else { return }
    // KVO fires on every assignment, and the layout reassigns an unchanged size on any relayout —
    // a resize that did not resize is not something a listener should have to filter out.
    guard lastEmittedContentSize != size else { return }
    lastEmittedContentSize = size
    emit(size)
  }

  public func collectionView(
    _ collectionView: UICollectionView,
    willDisplay cell: UICollectionViewCell,
    forItemAt indexPath: IndexPath
  ) {
    // Covers the case a scroll event cannot: the first cells appearing after a tree update, when
    // the content offset has not moved at all.
    scheduleVisibleRangeEmit()
  }

  public func collectionView(
    _ collectionView: UICollectionView,
    didEndDisplaying cell: UICollectionViewCell,
    forItemAt indexPath: IndexPath
  ) {
    scheduleVisibleRangeEmit()
  }
}

/**
 * The container the component view installs as its `contentView`.
 *
 * Exists to notice two moments. Entering a window is the first point at which the responder chain
 * reaches the owning `UIViewController`, and therefore the first point at which the collection
 * view can be registered as that controller's content scroll view. An interface-style change
 * matters only on systems without `registerForTraitChanges`.
 */
final class ContainerView: UIView {
  var onMovedToWindow: (() -> Void)?
  var onInterfaceStyleChange: (() -> Void)?
  /**
   * Every layout pass, because `UIRefreshControl` needs one before it will start.
   *
   * `beginRefreshing()` is *silently ignored* until the control has been laid out at least once,
   * which makes `refreshControl={<RefreshControl refreshing />}` on first mount do nothing at all.
   * React Native carries the same workaround in both of its implementations. Three field reads per
   * pass, and only while a refresh is actually pending.
   */
  var onDidLayout: (() -> Void)?

  override func layoutSubviews() {
    super.layoutSubviews()
    onDidLayout?()
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    // Also fires on removal, when `window` is nil and there is nothing to attach to.
    guard window != nil else { return }
    onMovedToWindow?()
  }

  override func traitCollectionDidChange(_ previous: UITraitCollection?) {
    super.traitCollectionDidChange(previous)
    // On iOS 17 and up the host uses `registerForTraitChanges`, which is not deprecated and does
    // not fire for unrelated traits. Without this guard both paths would run.
    if #unavailable(iOS 17.0) {
      guard traitCollection.userInterfaceStyle != previous?.userInterfaceStyle else { return }
      onInterfaceStyleChange?()
    }
  }
}

extension Array {
  /// Bounds-checked lookup. Section and row indices arrive from UIKit mid-update, when the layout
  /// can briefly be asking about a section the data no longer has.
  fileprivate subscript(safe index: Int) -> Element? {
    indices.contains(index) ? self[index] : nil
  }
}

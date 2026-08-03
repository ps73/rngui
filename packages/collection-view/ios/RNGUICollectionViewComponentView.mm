#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

#import <react/renderer/components/RNGUICollectionView/ComponentDescriptors.h>
#import <react/renderer/components/RNGUICollectionView/EventEmitters.h>
#import <react/renderer/components/RNGUICollectionView/Props.h>
#import <react/renderer/components/RNGUICollectionView/RCTComponentViewHelpers.h>

#import <React/RCTConversions.h>

// The Swift half, imported here and *only* here. The `__has_include` dance covers both
// linkage modes: a static library resolves the quoted form, a dynamic framework the
// angle-bracket one.
#if __has_include(<RNGUICollectionView/RNGUICollectionView-Swift.h>)
#import <RNGUICollectionView/RNGUICollectionView-Swift.h>
#else
#import "RNGUICollectionView-Swift.h"
#endif

using namespace facebook::react;

/**
 * Codegen emits a scoped C++ `enum class` for a string-union prop, so it has to be mapped by hand.
 * A string rather than an int across the boundary: Swift enums are invisible to Objective-C, and
 * one `switch` on a readable value beats two parallel integer tables that can drift apart.
 */
static NSString *RNGUIColorSchemeString(RNGUICollectionViewColorScheme scheme)
{
  switch (scheme) {
    case RNGUICollectionViewColorScheme::Light:
      return @"light";
    case RNGUICollectionViewColorScheme::Dark:
      return @"dark";
    case RNGUICollectionViewColorScheme::System:
      return @"system";
  }
}

/**
 * The Fabric component view backing `<CollectionView.Root>`.
 *
 * Hand-written rather than generated, and that is the reason this library exists in this
 * form: owning `mountChildComponentView:` / `unmountChildComponentView:` is what allows a
 * React child to be reparented *into* a `UICollectionViewCell` rather than floated over it.
 *
 * **Declared here rather than in a header, deliberately.** There is no
 * `RNGUICollectionViewComponentView.h`, because a public header would be pulled into the
 * pod's generated umbrella header:
 *
 *     #ifdef __OBJC__
 *     #import "RNGUICollectionViewComponentView.h"
 *
 * Anything this class needs in order to declare itself — `RCTViewComponentView` —
 * transitively includes Fabric's C++ headers, and the umbrella is parsed by plain
 * Objective-C translation units too. The result is `'atomic' file not found` from
 * `react/renderer/core/EventBeat.h`, reported against an unrelated pod's `.m` file, with
 * nothing pointing back to here. Keeping every C++-tainted declaration inside this one `.mm`
 * is what keeps the module safe to import.
 *
 * Nothing needs the header anyway: codegen's `ios.componentProvider` registers the class by
 * *name*, via `NSClassFromString(@"RNGUICollectionViewComponentView")` in the generated
 * `RCTThirdPartyComponentsProvider.mm`.
 */
@interface RNGUICollectionViewComponentView : RCTViewComponentView <RCTRNGUICollectionViewViewProtocol>
@end

@implementation RNGUICollectionViewComponentView {
  RNGUICollectionViewHost *_host;
  /**
   * Mounted React children, in React's own order.
   *
   * This array — not the UIKit view hierarchy — is what React's mount indices address, and
   * maintaining it is what buys the freedom to put the actual views inside cells. It is the
   * same trick `RNSScreenContainer` uses to park children inside child view controllers.
   */
  NSMutableArray<UIView *> *_hostedViews;
}

/**
 * Nothing in the app references this class symbolically — registration goes through
 * `NSClassFromString` — so in a static library the linker is free to drop it, and then the
 * component silently does not exist at runtime. A class implementing `+load` is always kept
 * and is realized at image load, before any lookup by name can happen.
 */
+ (void)load
{
}

/**
 * Opts out of component-view recycling.
 *
 * Not declared in `RCTComponentViewProtocol`; `RCTComponentViewFactory` probes for it with
 * `respondsToSelector:` (see `RCTComponentViewFactory.mm`). Recycling a component view whose
 * children have been reparented into cells is a genuine source of subtle bugs — a pooled
 * instance would come back holding a stale `_hostedViews` and a collection view full of
 * detached cells — and the cost of declining is one view allocation per mount.
 */
+ (BOOL)shouldBeRecycled
{
  return NO;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<RNGUICollectionViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const RNGUICollectionViewProps>();
    _props = defaultProps;

    _hostedViews = [NSMutableArray new];
    _host = [RNGUICollectionViewHost new];

    // Weakly, because `_host` is owned by `self` — capturing `self` strongly here would be a
    // retain cycle that keeps the whole collection view alive after React unmounts it.
    //
    // `_eventEmitter` is read *inside* the block rather than captured: Fabric replaces the
    // emitter over a component view's lifetime (`updateEventEmitter:`), so a captured one goes
    // stale and the events quietly stop arriving.
    __weak __typeof(self) weakSelf = self;

    // Every block follows the same three steps — resolve the weak self, cast the *current* event
    // emitter, emit — so the lookup is factored into a macro rather than written out seven times.
    // A macro rather than a helper method because the emitter's concrete type is C++ and returning
    // it from an ObjC method would put a folly-adjacent type in a signature.
#define RNGUI_EMITTER(nameVar)                                                                  \
  __typeof(self) strongSelf = weakSelf;                                                         \
  if (strongSelf == nil) {                                                                      \
    return;                                                                                     \
  }                                                                                             \
  const auto nameVar =                                                                          \
      std::static_pointer_cast<const RNGUICollectionViewEventEmitter>(strongSelf->_eventEmitter); \
  if (nameVar == nullptr) {                                                                     \
    return;                                                                                     \
  }

    _host.onVisibleRangeChange = ^(NSInteger firstIndex, NSInteger lastIndex) {
      RNGUI_EMITTER(emitter)
      emitter->onVisibleRangeChange({
          .firstIndex = static_cast<int>(firstIndex),
          .lastIndex = static_cast<int>(lastIndex),
      });
    };

    _host.onRowPress = ^(NSString *rowId) {
      RNGUI_EMITTER(emitter)
      emitter->onRowPress({.rowId = RCTStringFromNSString(rowId)});
    };

    _host.onSwitchChange = ^(NSString *rowId, BOOL value) {
      RNGUI_EMITTER(emitter)
      emitter->onSwitchChange({
          .rowId = RCTStringFromNSString(rowId),
          .value = static_cast<bool>(value),
      });
    };

    _host.onTextChange = ^(NSString *rowId, NSString *value) {
      RNGUI_EMITTER(emitter)
      emitter->onTextChange({
          .rowId = RCTStringFromNSString(rowId),
          .value = RCTStringFromNSString(value),
      });
    };

    _host.onFocusChange = ^(NSString *rowId, BOOL focused) {
      RNGUI_EMITTER(emitter)
      emitter->onFocusChange({
          .rowId = RCTStringFromNSString(rowId),
          .focused = static_cast<bool>(focused),
      });
    };

    _host.onDateChange = ^(NSString *rowId, double millis) {
      RNGUI_EMITTER(emitter)
      emitter->onDateChange({.rowId = RCTStringFromNSString(rowId), .millis = millis});
    };

    _host.onSliderChange = ^(NSString *rowId, double value) {
      RNGUI_EMITTER(emitter)
      emitter->onSliderChange({.rowId = RCTStringFromNSString(rowId), .value = value});
    };

    _host.onSliderCommit = ^(NSString *rowId, double value) {
      RNGUI_EMITTER(emitter)
      emitter->onSliderCommit({.rowId = RCTStringFromNSString(rowId), .value = value});
    };

    _host.onMenuSelect = ^(NSString *rowId, NSString *itemId) {
      RNGUI_EMITTER(emitter)
      emitter->onMenuSelect({
          .rowId = RCTStringFromNSString(rowId),
          .itemId = RCTStringFromNSString(itemId),
      });
    };

    _host.onSwipeAction = ^(NSString *rowId, NSString *actionId) {
      RNGUI_EMITTER(emitter)
      emitter->onSwipeAction({
          .rowId = RCTStringFromNSString(rowId),
          .actionId = RCTStringFromNSString(actionId),
      });
    };

    _host.onSectionAction = ^(NSString *sectionId) {
      RNGUI_EMITTER(emitter)
      emitter->onSectionAction({.sectionId = RCTStringFromNSString(sectionId)});
    };

    /**
     * The five scroll events.
     *
     * Codegen gives every event its own set of nested payload structs — `OnScrollContentOffset`,
     * `OnScrollBeginDragContentOffset`, and so on — with no shared type between them even though
     * the five shapes are identical. None of those names appear below only because C++20 deduces
     * a nested aggregate's type from the field it initialises; without that this would be five
     * near-copies of the same fifteen lines. The alternative — one Swift block with a kind
     * discriminator — would put an integer table on each side of this boundary to drift apart.
     */
#define RNGUI_SCROLL_EMITTER(eventName)                                                       \
  ^(CGPoint offset, CGSize contentSize, CGSize viewport, UIEdgeInsets inset) {                \
    RNGUI_EMITTER(emitter)                                                                    \
    emitter->eventName({                                                                      \
        .contentOffset = {.x = offset.x, .y = offset.y},                                      \
        .contentSize = {.width = contentSize.width, .height = contentSize.height},            \
        .layoutMeasurement = {.width = viewport.width, .height = viewport.height},            \
        .contentInset =                                                                       \
            {.top = inset.top, .left = inset.left, .bottom = inset.bottom, .right = inset.right}, \
        /* A collection view never zooms, so this is the only value it could ever have. */    \
        .zoomScale = 1,                                                                       \
    });                                                                                       \
  }

    _host.onScroll = RNGUI_SCROLL_EMITTER(onScroll);
    _host.onScrollBeginDrag = RNGUI_SCROLL_EMITTER(onScrollBeginDrag);
    _host.onScrollEndDrag = RNGUI_SCROLL_EMITTER(onScrollEndDrag);
    _host.onMomentumScrollBegin = RNGUI_SCROLL_EMITTER(onMomentumScrollBegin);
    _host.onMomentumScrollEnd = RNGUI_SCROLL_EMITTER(onMomentumScrollEnd);

#undef RNGUI_SCROLL_EMITTER

    _host.onContentSizeChange = ^(CGSize size) {
      RNGUI_EMITTER(emitter)
      emitter->onContentSizeChange({.width = size.width, .height = size.height});
    };

#undef RNGUI_EMITTER

    // The collection view has to be reachable by walking `subviews[0]`, and has to stay
    // reachable that way.
    //
    // react-native-screens locates "the screen's scroll view" with exactly that walk — see
    // `RNSScrollViewFinder.findScrollViewInFirstDescendantChainFrom:`, which follows
    // `subviews[0]` and nothing else — and that single lookup is what drives large-title
    // collapse, the blurred-header transition, the iOS 26 scroll-edge effects (the "soft"
    // and "hard" header backgrounds) and tab-bar scroll-to-top.
    //
    // Assigning it as `contentView` puts the host's container at index 0. Mounted React
    // children are parked inside that container rather than here, so they land at index 1 and
    // above of a view that is itself index 0 — the chain stays intact. Note the inherited
    // `mountChildComponentView:` would have inserted them into *this* view at the index React
    // chose, frequently 0, silently breaking every one of those behaviours.
    self.contentView = _host.containerView;
  }
  return self;
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps
{
  const auto &oldViewProps = *std::static_pointer_cast<const RNGUICollectionViewProps>(_props);
  const auto &newViewProps = *std::static_pointer_cast<const RNGUICollectionViewProps>(props);

  // Gated on `revision`, never on the tree itself: the string can be megabytes, so comparing
  // it would be a pointless memcmp on every commit and decoding it unconditionally would be
  // far worse. JS bumps the revision when it re-serializes.
  if (oldViewProps.revision != newViewProps.revision) {
    [_host applyTreeJSON:RCTNSStringFromString(newViewProps.tree)];
  }

  // A separate, typed prop rather than part of the tree: flipping a theme should not re-encode
  // every row, and this is the one appearance input that has to reach `overrideUserInterfaceStyle`
  // rather than a cell configuration.
  if (oldViewProps.colorScheme != newViewProps.colorScheme) {
    [_host setColorScheme:RNGUIColorSchemeString(newViewProps.colorScheme)];
  }

  if (oldViewProps.showsSectionIndex != newViewProps.showsSectionIndex) {
    [_host setShowsSectionIndex:newViewProps.showsSectionIndex];
  }

  if (oldViewProps.sectionIndexRowHeight != newViewProps.sectionIndexRowHeight) {
    [_host setSectionIndexRowHeight:newViewProps.sectionIndexRowHeight];
  }

  if (oldViewProps.sectionIndexShowsCallout != newViewProps.sectionIndexShowsCallout) {
    [_host setSectionIndexShowsCallout:newViewProps.sectionIndexShowsCallout];
  }

  if (oldViewProps.showsVerticalScrollIndicator != newViewProps.showsVerticalScrollIndicator) {
    [_host setShowsVerticalScrollIndicator:newViewProps.showsVerticalScrollIndicator];
  }

  if (oldViewProps.contentInsetTop != newViewProps.contentInsetTop ||
      oldViewProps.contentInsetLeft != newViewProps.contentInsetLeft ||
      oldViewProps.contentInsetBottom != newViewProps.contentInsetBottom ||
      oldViewProps.contentInsetRight != newViewProps.contentInsetRight) {
    [_host setContentInset:UIEdgeInsetsMake(
                               newViewProps.contentInsetTop,
                               newViewProps.contentInsetLeft,
                               newViewProps.contentInsetBottom,
                               newViewProps.contentInsetRight)];
  }

  // The generated enums are declared in the order the spec lists them, so the raw value carries
  // across as an integer rather than needing a string table on both sides — unlike `colorScheme`,
  // where the Swift side switches on a readable name because there is no matching UIKit enum.
  if (oldViewProps.contentInsetAdjustmentBehavior != newViewProps.contentInsetAdjustmentBehavior) {
    [_host setContentInsetAdjustmentBehavior:(NSInteger)newViewProps.contentInsetAdjustmentBehavior];
  }

  if (oldViewProps.automaticallyAdjustContentInsets != newViewProps.automaticallyAdjustContentInsets) {
    [_host setAutomaticallyAdjustContentInsets:newViewProps.automaticallyAdjustContentInsets];
  }

  if (oldViewProps.automaticallyAdjustsScrollIndicatorInsets !=
      newViewProps.automaticallyAdjustsScrollIndicatorInsets) {
    [_host setAutomaticallyAdjustsScrollIndicatorInsets:
               newViewProps.automaticallyAdjustsScrollIndicatorInsets];
  }

  if (oldViewProps.automaticallyAdjustKeyboardInsets != newViewProps.automaticallyAdjustKeyboardInsets) {
    [_host setAutomaticallyAdjustKeyboardInsets:newViewProps.automaticallyAdjustKeyboardInsets];
  }

  if (oldViewProps.keyboardAware != newViewProps.keyboardAware) {
    [_host setKeyboardAware:newViewProps.keyboardAware];
  }

  if (oldViewProps.keyboardAwareOffset != newViewProps.keyboardAwareOffset) {
    [_host setKeyboardAwareOffset:newViewProps.keyboardAwareOffset];
  }

  if (oldViewProps.keyboardDismissMode != newViewProps.keyboardDismissMode) {
    [_host setKeyboardDismissMode:(NSInteger)newViewProps.keyboardDismissMode];
  }

  // JS sends this from whether an `onVisibleRangeChange` callback was passed. Fabric always
  // installs an event emitter and there is no way to ask whether anything is listening, so
  // without being told, the list would post an event on every run-loop turn of every scroll for
  // the overwhelming majority of lists that never listen.
  if (oldViewProps.scrollEnabled != newViewProps.scrollEnabled) {
    [_host setScrollEnabled:newViewProps.scrollEnabled];
  }

  if (oldViewProps.decelerationRate != newViewProps.decelerationRate) {
    [_host setDecelerationRate:newViewProps.decelerationRate];
  }

  if (oldViewProps.tracksVisibleRange != newViewProps.tracksVisibleRange) {
    [_host setTracksVisibleRange:newViewProps.tracksVisibleRange];
  }

  // The same gate as above, and needed for a second reason: a reanimated scroll handler attaches
  // by view tag rather than by passing a prop, so there is nothing here to infer a listener from.
  if (oldViewProps.tracksScroll != newViewProps.tracksScroll) {
    [_host setTracksScroll:newViewProps.tracksScroll];
  }

  [super updateProps:props oldProps:oldProps];
}

#pragma mark - Commands

/**
 * The one entry point that is not a prop, and it exists for reanimated.
 *
 * `scrollTo` inside a worklet compiles to `dispatchCommand(ref, 'scrollTo', …)`, which arrives
 * here synchronously on the UI thread — mid-gesture, before the frame is drawn. That timing is the
 * whole point: a bottom sheet pins its list at the top *while* the finger is moving, and a prop
 * would put a React commit in the middle of that and land one frame late every time.
 */
- (void)handleCommand:(const NSString *)commandName args:(const NSArray *)args
{
  RCTRNGUICollectionViewHandleCommand(self, commandName, args);
}

- (void)scrollTo:(double)x y:(double)y animated:(BOOL)animated
{
  [_host scrollToX:x y:y animated:animated];
}

#pragma mark - React children

/**
 * Takes ownership of a mounted child instead of letting the superclass place it.
 *
 * **Deliberately does not call `super`, and that single fact is what this whole library rests
 * on.** `RCTViewComponentView`'s implementation asserts that a child's `superview` is the
 * container it inserted it into and that its index still matches — reasonable for a plain
 * view, fatal for anything that wants to hand a child to a cell. Overriding both halves of
 * the pair and tracking the children ourselves means those assertions never run, which is
 * exactly how `react-native-screens` moves children into view controllers and navigation
 * bars.
 *
 * The child is parked in the container, hidden, until a cell claims it.
 */
- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView
                          index:(NSInteger)index
{
  NSInteger clamped = MIN(MAX(index, (NSInteger)0), (NSInteger)_hostedViews.count);
  [_hostedViews insertObject:childComponentView atIndex:(NSUInteger)clamped];

  childComponentView.hidden = YES;
  [_host.containerView addSubview:childComponentView];

  [_host setHostedViews:_hostedViews];
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView
                            index:(NSInteger)index
{
  // The cell lets go first. Otherwise a recycled cell keeps a reference to a view React has
  // already torn down.
  [_host releaseHostedView:childComponentView];

  // Located by identity rather than by React's index. The two agree today, but a cell may
  // have moved the view in the meantime, and removing the wrong element here would desync
  // `hostIndex` for every row after it — a failure that shows up as rows displaying each
  // other's content, far from the cause.
  NSUInteger found = [_hostedViews indexOfObjectIdenticalTo:childComponentView];
  if (found != NSNotFound) {
    [_hostedViews removeObjectAtIndex:found];
  }

  [childComponentView removeFromSuperview];
  [_host setHostedViews:_hostedViews];
}

/**
 * Hands the collection view to anyone who asks for it by name.
 *
 * `RNSScrollViewFinder` checks `respondsToSelector:` for this rather than protocol
 * conformance, so implementing it gives react-native-screens an authoritative,
 * order-independent answer with no compile-time dependency on react-native-screens at all.
 *
 * Insurance rather than the active path today: react-native-screens 4.26 has no callers of
 * this yet and still relies on the `subviews[0]` heuristic above. Six lines is a cheap hedge
 * against that heuristic changing.
 */
- (UIScrollView *)findContentScrollView
{
  return _host.collectionView;
}

@end

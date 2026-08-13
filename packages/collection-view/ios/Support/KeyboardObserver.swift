import UIKit

/**
 * Watches the keyboard and reports how much of a given scroll view it covers.
 *
 * Four details separate this from the version everyone writes first, and each one is a real bug
 * avoided rather than a refinement:
 *
 * 1. **The overlap is computed from the scroll view's own geometry, never from the screen height.**
 *    `screenHeight - keyboardHeight` is correct only when the list runs to the bottom of the
 *    screen. Inside a form sheet, a page sheet or a bottom sheet it is wrong by exactly the gap
 *    beneath the sheet, and the list ends up scrolled by that much too far.
 *
 * 2. **The animation curve is shifted, not switched on.** iOS sends curve `7` for keyboard
 *    animations. There is no `UIView.AnimationCurve` case for it — it is private — so any `switch`
 *    over the known curves silently falls back to ease-in-out and the inset visibly lags the
 *    keyboard. What works is that `UIView.AnimationCurve.linear.rawValue << 16` equals
 *    `UIView.AnimationOptions.curveLinear.rawValue`, so shifting the raw value carries private
 *    curves through intact. React Native's own `RCTScrollViewComponentView` does exactly this;
 *    there is no public API for it.
 *
 * 3. **`keyboardWillChangeFrame` rather than `willShow` / `willHide`.** It is the only one that
 *    fires for an interactive dismissal, a hardware-keyboard toggle, or the height change when a
 *    prediction bar appears.
 *
 * 4. **Reduce Motion + Prefer Cross-Fade reports a zero-height end frame** instead of animating
 *    one off-screen, so that combination has to be read as "hidden" explicitly or the inset stays
 *    behind forever for the users who need it most.
 */
@MainActor
final class KeyboardObserver: NSObject {
  /// Called with the overlap in the scroll view's own coordinate space, plus the animation to
  /// match. Not called at all while there is no window — there is no geometry to measure against.
  var onChange: ((CGFloat, TimeInterval, UIView.AnimationOptions) -> Void)?

  private weak var scrollView: UIScrollView?

  init(scrollView: UIScrollView) {
    self.scrollView = scrollView
    super.init()

    NotificationCenter.default.addObserver(
      self,
      selector: #selector(keyboardWillChangeFrame),
      name: UIResponder.keyboardWillChangeFrameNotification,
      object: nil
    )
  }

  /**
   * Unregisters, without waiting to be deallocated.
   *
   * `NotificationCenter` has zeroed its references since iOS 9, so the registration does go away on
   * its own — this exists so that "stopped" is a thing the caller can *do* at a chosen moment rather
   * than a consequence of the last reference dropping somewhere it cannot see.
   */
  func stop() {
    onChange = nil
    NotificationCenter.default.removeObserver(self)
  }

  @objc private func keyboardWillChangeFrame(_ notification: Notification) {
    guard
      let scrollView,
      let window = scrollView.window,
      let info = notification.userInfo,
      let endFrame = (info[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue
    else { return }

    let duration =
      (info[UIResponder.keyboardAnimationDurationUserInfoKey] as? TimeInterval) ?? 0.25
    // Defaulted to 7, the private curve iOS actually sends, rather than to a public case — the
    // fallback should look like the common path, not like the exception.
    let curve = (info[UIResponder.keyboardAnimationCurveUserInfoKey] as? Int) ?? 7
    let options = UIView.AnimationOptions(rawValue: UInt(curve) << 16)

    onChange?(overlap(of: endFrame, in: scrollView, window: window), duration, options)
  }

  /**
   * How far the keyboard's end frame reaches into the scroll view.
   *
   * Two conversions, and both are needed: the notification reports the frame in *screen*
   * coordinates, so it goes screen → window → view. Skipping the first is the bug that only shows
   * up on an iPad in a split-screen scene, where the window is not the screen.
   */
  private func overlap(of endFrame: CGRect, in scrollView: UIScrollView, window: UIWindow) -> CGFloat {
    // A zero-height end frame means dismissed. `UIAccessibilityPrefersCrossFadeTransitions` reports
    // exactly that instead of an off-screen frame, so the check has to be on the height rather than
    // on the position.
    guard endFrame.height > 0 else { return 0 }

    let inWindow = window.convert(endFrame, from: nil)
    let inView = scrollView.convert(inWindow, from: window)
    return max(0, scrollView.bounds.maxY - inView.minY)
  }
}

extension UIView {
  /// The first responder within this view's subtree, if any.
  ///
  /// Walked rather than obtained from `UIApplication`: the responder we care about is the one
  /// *inside this list*, and a global lookup would happily return a text field belonging to some
  /// other view — a search bar in the navigation item, say — and scroll the list to chase it.
  func rnguiFindFirstResponder() -> UIView? {
    if isFirstResponder { return self }
    for subview in subviews {
      if let found = subview.rnguiFindFirstResponder() { return found }
    }
    return nil
  }
}

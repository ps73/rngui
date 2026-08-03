import UIKit

/**
 * A row filled by a `UISlider`, optionally flanked by a small and a large glyph.
 *
 * ```
 *  ┌──────────────────────────────────────────────┐
 *  │ ☀︎ ──────────●─────────────────────────  ☀  │
 *  └──────────────────────────────────────────────┘
 * ```
 *
 * **`minimumValueImage` and `maximumValueImage` are UISlider's own slots**, not two image views
 * this cell lays out — which is why the track shortens to make room for them rather than being
 * overlapped. Android has no equivalent property and puts icon views either side of the Material
 * slider to the same effect; that asymmetry is in the implementations, not in the API.
 *
 * The slider is owned by the cell and reused with it. A fresh one per configure pass would restart
 * its animation and drop the thumb mid-drag — the same reason `SwitchCell` keeps its `UISwitch`.
 */
final class SliderCell: UICollectionViewListCell {
  let slider = UISlider()

  /// Fires continuously while the thumb moves.
  var onChange: ((Float) -> Void)?

  /// Fires once, when the finger lifts.
  var onCommit: ((Float) -> Void)?

  /**
   * True between touch-down and touch-up on the thumb.
   *
   * **Incoming values are ignored while it is true**, and that is not an optimisation. Every drag
   * frame goes to JavaScript, comes back as a new tree, and reaches `configure` — by which time
   * the thumb has moved on. Assigning the stale value would drag the thumb backwards under the
   * finger, sixty times a second. The numeric form of the echo rule `TextFieldCell` follows.
   */
  private var isDragging = false

  override init(frame: CGRect) {
    super.init(frame: frame)
    slider.addTarget(self, action: #selector(valueChanged), for: .valueChanged)
    slider.addTarget(self, action: #selector(began), for: [.touchDown])
    slider.addTarget(
      self,
      action: #selector(ended),
      for: [.touchUpInside, .touchUpOutside, .touchCancel]
    )

    slider.translatesAutoresizingMaskIntoConstraints = false
    contentView.addSubview(slider)
    NSLayoutConstraint.activate([
      slider.leadingAnchor.constraint(equalTo: contentView.layoutMarginsGuide.leadingAnchor),
      slider.trailingAnchor.constraint(equalTo: contentView.layoutMarginsGuide.trailingAnchor),
      slider.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 8),
      slider.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -8),
      // A slider is shorter than a list row's minimum, so without this the cell collapses around
      // the control and the row reads as half-height beside its neighbours.
      slider.heightAnchor.constraint(greaterThanOrEqualToConstant: 28),
    ])
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not used — cells are only created by a CellRegistration")
  }

  func configure(
    value: Float,
    minimum: Float,
    maximum: Float,
    step: Float,
    minimumImage: UIImage?,
    maximumImage: UIImage?,
    tint: UIColor?,
    enabled: Bool
  ) {
    slider.minimumValue = minimum
    slider.maximumValue = maximum
    slider.minimumValueImage = minimumImage
    slider.maximumValueImage = maximumImage
    slider.minimumTrackTintColor = tint
    slider.isEnabled = enabled
    self.step = step

    guard !isDragging else { return }
    slider.setValue(min(max(value, minimum), maximum), animated: false)
  }

  /**
   * Quantisation, applied here because `UISlider` has none.
   *
   * Android gets tick marks and snapping from `Slider.stepSize`; iOS has never had either, so the
   * value is rounded on the way out and the thumb follows it. The *marks* are still missing, which
   * is documented on `RowSpec.sliderStep` rather than faked — drawing our own would produce a
   * control no iOS user has seen.
   */
  private var step: Float = 0

  private func quantised(_ raw: Float) -> Float {
    guard step > 0 else { return raw }
    let steps = (raw - slider.minimumValue) / step
    return min(slider.minimumValue + steps.rounded() * step, slider.maximumValue)
  }

  @objc private func began() {
    isDragging = true
  }

  @objc private func valueChanged() {
    let value = quantised(slider.value)
    if value != slider.value { slider.setValue(value, animated: false) }
    onChange?(value)
  }

  @objc private func ended() {
    isDragging = false
    onCommit?(quantised(slider.value))
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    // Dropped explicitly rather than left to the next configure pass. Between reuse and
    // reconfiguration the cell is briefly live, and a stray callback in that window would report
    // against whichever row used it last.
    onChange = nil
    onCommit = nil
    isDragging = false
  }
}

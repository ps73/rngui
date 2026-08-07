package com.rngui.collectionview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the refresh indicator rests.
 *
 * Three inputs decide it and two of them are easy to lose. The **resolved content inset** is one:
 * the refresh wrapper's own origin is the top of the window, while the list's content starts below
 * the status bar and toolbar, so an indicator positioned from the wrapper alone settles underneath
 * the chrome. The **circle diameter** is the other, and it is the one that actually broke: `size`
 * changes it, Fabric writes props in no guaranteed order, and a `refreshSize` landing after
 * `refreshProgressViewOffset` left the offsets computed against the previous diameter.
 *
 * **What the last case pins is that the diameter changes the answer at all** — which is the reason
 * the size setter has to re-resolve. It does not, and on the JVM cannot, catch someone deleting
 * the `setSize` override: that is a call-site fact about a `View`, and this module has no
 * Robolectric. The instrumented spikes are where that would live.
 */
class RefreshOffsetsTest {
  /** `SwipeRefreshLayout`'s own two sizes, in dips. */
  private val defaultDiameter = 40
  private val largeDiameter = 56

  @Test
  fun `with no inset and no offset, the circle hides exactly one diameter above the origin`() {
    val offsets =
      RefreshOffsets.resolve(
        offsetDip = 0f,
        topInsetPx = 0,
        diameterPx = defaultDiameter,
        density = 1f,
      )
    assertEquals(-40, offsets.start)
    assertEquals(24, offsets.end)
  }

  @Test
  fun `the resolved inset moves both ends down by the same amount`() {
    val bare =
      RefreshOffsets.resolve(0f, topInsetPx = 0, diameterPx = defaultDiameter, density = 1f)
    // A status bar and a toolbar, which is what an edge-to-edge list starts under.
    val inset =
      RefreshOffsets.resolve(0f, topInsetPx = 140, diameterPx = defaultDiameter, density = 1f)

    assertEquals(bare.start + 140, inset.start)
    assertEquals(bare.end + 140, inset.end)
  }

  @Test
  fun `the caller's offset is in points and scales with density`() {
    val offsets =
      RefreshOffsets.resolve(
        offsetDip = 10f,
        topInsetPx = 0,
        diameterPx = defaultDiameter,
        density = 3f,
      )
    // 10dp at 3x is 30px, and the 64dp travel becomes 192px.
    assertEquals(30 - 40, offsets.start)
    assertEquals(30 + 192 - 40, offsets.end)
  }

  @Test
  fun `the travel between the two ends is the same whatever else changes`() {
    val travel = { o: RefreshOffsets.Offsets -> o.end - o.start }
    val target = (RefreshOffsets.CIRCLE_TARGET_DIP * 2f).toInt()

    assertEquals(
      target,
      travel(RefreshOffsets.resolve(0f, 0, defaultDiameter, 2f)),
    )
    assertEquals(
      target,
      travel(RefreshOffsets.resolve(24f, 300, largeDiameter, 2f)),
    )
  }

  /**
   * The regression. A larger circle has to move *both* ends further up, or the indicator rests
   * 16dp below where it should — which is what a stale diameter looks like on screen.
   */
  @Test
  fun `a larger circle shifts the resting position up by the difference in diameter`() {
    val small = RefreshOffsets.resolve(0f, topInsetPx = 100, diameterPx = defaultDiameter, density = 1f)
    val large = RefreshOffsets.resolve(0f, topInsetPx = 100, diameterPx = largeDiameter, density = 1f)

    assertEquals(small.start - (largeDiameter - defaultDiameter), large.start)
    assertEquals(small.end - (largeDiameter - defaultDiameter), large.end)
  }
}

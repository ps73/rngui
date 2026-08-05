package com.rngui.collectionview.spike

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rngui.collectionview.AppearanceResolver
import com.rngui.collectionview.ColorScheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What counts as a mode change, and — more usefully — what does not.
 *
 * `RNGUICollectionViewView.onAttachedToWindow` restyles when the configuration it is rejoining
 * resolves to a different mode than the one it was holding. That guard exists because attach happens
 * for reasons that are not a theme flip — a rotation, a window resize, a screen coming back from the
 * background — and restyling on those would rebind every visible row for nothing.
 *
 * So the guard is only as good as [AppearanceResolver.isDark] being blind to everything except the
 * night bit. Comparing whole `Configuration`s is the obvious-looking edit that would break it, and
 * the symptom would be a performance regression on rotation rather than anything visible, which is
 * the kind that survives review.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceModeTest {
  @Test
  fun onlyTheNightBitDecidesTheMode() {
    val light = configuration(night = Configuration.UI_MODE_NIGHT_NO)
    val dark = configuration(night = Configuration.UI_MODE_NIGHT_YES)

    val rotated =
      Configuration(light).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
    val onTelevision =
      Configuration(light).apply {
        uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
          Configuration.UI_MODE_TYPE_TELEVISION
      }

    assertFalse("a light configuration read as dark", AppearanceResolver.isDark(light, SYSTEM))
    assertTrue("a dark configuration read as light", AppearanceResolver.isDark(dark, SYSTEM))
    assertFalse("a rotation changed the mode", AppearanceResolver.isDark(rotated, SYSTEM))
    assertFalse(
      "a ui-mode *type* change changed the mode",
      AppearanceResolver.isDark(onTelevision, SYSTEM),
    )
  }

  /** A pinned scheme is the caller's answer, and the device's does not get a say in it. */
  @Test
  fun aPinnedSchemeIgnoresTheConfigurationEntirely() {
    val light = configuration(night = Configuration.UI_MODE_NIGHT_NO)
    val dark = configuration(night = Configuration.UI_MODE_NIGHT_YES)

    assertTrue(AppearanceResolver.isDark(light, ColorScheme.dark))
    assertFalse(AppearanceResolver.isDark(dark, ColorScheme.light))
  }

  private fun configuration(night: Int) =
    Configuration().apply {
      orientation = Configuration.ORIENTATION_PORTRAIT
      uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    }

  private companion object {
    val SYSTEM = ColorScheme.system
  }
}

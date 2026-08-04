package com.rngui.example

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.rngui.collectionview.RNGUICollectionViewView

/**
 * Finding the real component inside the real app.
 *
 * Everything here exists because React mounts asynchronously and there is no callback to wait on
 * from outside: the activity is resumed long before the surface has rendered, so every lookup is a
 * poll with a deadline rather than a read.
 */
object LiveApp {
  private const val TIMEOUT_MS = 30_000L
  private const val POLL_MS = 100L

  /** The first collection view in the activity's hierarchy, once React has mounted one. */
  fun awaitCollectionView(activity: Activity): RNGUICollectionViewView =
    await("no RNGUICollectionViewView was ever mounted") {
      var found: RNGUICollectionViewView? = null
      onMain { found = activity.window.decorView.firstCollectionView() }
      found
    }

  /**
   * Switches tabs by their label.
   *
   * By text rather than by coordinate: the tab bar is native and its geometry is a function of the
   * device, so a tap at a measured point is a test that passes on one screen size.
   */
  fun openTab(label: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val tab = device.wait(Until.findObject(By.text(label)), TIMEOUT_MS)
    requireNotNull(tab) { "no tab labelled '$label' — the tab bar never rendered" }
    tab.click()
    device.waitForIdle()
  }

  /** Polls `produce` until it returns non-null, or fails with [message]. */
  fun <T : Any> await(message: String, produce: () -> T?): T {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      produce()?.let { return it }
      Thread.sleep(POLL_MS)
    }
    throw AssertionError(message)
  }

  /** Runs `body` on the main thread and waits for it, so a caller can read view state safely. */
  fun onMain(body: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(body)
  }

  /** Lets the main thread drain — a mount, a layout pass, an inset dispatch. */
  fun settle() {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  /** Runs a shell command as the instrumentation, and waits for it to finish. */
  fun shell(command: String) {
    InstrumentationRegistry.getInstrumentation()
      .uiAutomation
      .executeShellCommand(command)
      .use { it.close() }
  }

  private fun View.firstCollectionView(): RNGUICollectionViewView? {
    if (this is RNGUICollectionViewView) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
      getChildAt(index).firstCollectionView()?.let { return it }
    }
    return null
  }

  /** The first focusable text field inside a view, whatever it is nested in. */
  fun View.firstEditText(): android.widget.EditText? {
    if (this is android.widget.EditText) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
      getChildAt(index).firstEditText()?.let { return it }
    }
    return null
  }
}

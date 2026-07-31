package com.rngui.collectionview.spike

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The activity the spike scrolls a list in. Test-only; see src/androidTest/AndroidManifest.xml. */
class SpikeActivity : ComponentActivity()

/**
 * M1 Decision 1, measured rather than argued: what should draw a stock row?
 *
 * `docs/android-plan.md` proposes a hybrid — a `RecyclerView` shell with a `ComposeView` per
 * stock-row holder — and names the condition under which the fallback ships instead: *"if per-item
 * composition costs more than ~2 ms of the 16 ms budget on a mid-tier device"*, `default` /
 * `value` / `subtitle` drop to hand-built Views and Compose is kept only for the control-bearing
 * kinds.
 *
 * So this builds both backends over the same 2,000 rows, scrolls each by the same deterministic
 * amount, and reads frame durations off `FrameMetrics`. The two adapters render the same content
 * with the same paddings and text sizes; the only difference is what draws it.
 *
 * **Deterministic scrolling, not a fling.** The plan says "programmatic fling", but a fling is
 * physics: its distance depends on the platform's fling friction and its frame count on how long
 * the scroller takes to settle, so the two backends would not be bound the same number of rows.
 * Driving a fixed `scrollBy` from `Choreographer` instead means both runs bind an identical number
 * of rows over an identical number of frames — which is what makes the *difference* between them
 * attributable to per-row cost rather than to how far each happened to travel.
 *
 * It also measures per-row cost directly, with drawing excluded, because the frame numbers alone
 * cannot answer the plan's question: they include rasterization, and on a software-rendered
 * emulator that penalises Compose's draw path on *every* frame rather than only on the ~17% that
 * bind a row. Dividing the frame-time difference by the row count would attribute a per-frame cost
 * to per-row work and overstate it by two orders of magnitude — which is exactly the mistake the
 * first version of this spike made, and reported as a confident verdict.
 *
 * Read the result as a comparison, never as an absolute, and check the baseline before reading it
 * at all: if the Views run alone cannot hold 60fps, there is no 16 ms budget on this hardware to
 * measure a 2 ms cost against, and the run says so instead of returning a number.
 */
@RunWith(AndroidJUnit4::class)
class RowBackendSpike {
  @get:Rule val activityRule = ActivityScenarioRule(SpikeActivity::class.java)

  @Test
  fun compareRowBackends() {
    // The number the decision actually turns on. Isolated from drawing on purpose — see
    // `perRowCost` for why the frame-time run below cannot answer this by itself.
    val viewCost = perRowCost("views") { ViewRowAdapter() }
    val composeCost = perRowCost("compose") { ComposeRowAdapter() }

    val views = frames("views") { ViewRowAdapter() }
    val compose = frames("compose") { ComposeRowAdapter() }

    val firstCompositionDeltaMs = composeCost.firstBindMs - viewCost.firstBindMs
    val baselineIsSound = views.p50Ms <= 16.7

    println(
      """
      |
      |=== M1 Decision 1 — row backend =============================================
      |device: ${Build.MODEL} (API ${Build.VERSION.SDK_INT}), density ${density()}
      |
      |-- per-row cost, bind + measure + layout, drawing excluded --
      |$viewCost
      |$composeCost
      |
      |first composition, Compose − Views: ${"%.3f".format(firstCompositionDeltaMs)} ms
      |  plan's threshold:                  2.000 ms
      |
      |-- frame times while scrolling, 2,000 rows --
      |$views
      |$compose
      |rows bound during the measured window: ${compose.rowsBound} of $MEASURED_FRAMES frames
      |
      |-- reading these numbers --
      |${if (baselineIsSound) "" else """
INCONCLUSIVE ON THIS DEVICE. The Views baseline itself misses 60fps (p50 ${"%.1f".format(views.p50Ms)}ms,
${views.jankFrames}/${views.frames} frames janky) drawing nothing but two TextViews per row. There is no
16ms budget here to measure a 2ms cost against, and the frame comparison is further skewed by
software rasterization, which penalises Compose's draw path far more than a TextView's. Run
this on the mid-tier phone the plan names before treating the frame numbers as a verdict.
      |""".trim()}
      |
      |`rebind` is NOT a Compose steady-state cost. Writing a `mutableStateOf` schedules a
      |recomposition on the Recomposer, which dispatches on the *next* frame — so a measure() call
      |on the same stack observes the old composition and the write looks free. The Views number on
      |that line is real; the Compose one is a floor, not a cost. Steady-state Compose cost is only
      |visible in the frame data above, which is why the baseline check matters.
      |
      |`first bind` IS synchronous on both: a ComposeView composes inside its first onMeasure. It is
      |paid once per *holder* rather than once per row, so a pool of ~${POOL_ESTIMATE} holders per view type
      |pays it ~${POOL_ESTIMATE} times — a hitch when a list first scrolls, not a per-row tax.
      |=============================================================================
      """
        .trimMargin()
    )
  }

  // -------------------------------------------------------------------------------------------
  // Per-row cost
  // -------------------------------------------------------------------------------------------

  private data class RowCost(
    val name: String,
    val createMs: Double,
    val firstBindMs: Double,
    val rebindMs: Double,
  ) {
    override fun toString(): String =
      "%-8s create=%.3fms  first bind=%.3fms  rebind=%.3fms".format(
        name,
        createMs,
        firstBindMs,
        rebindMs,
      )
  }

  /**
   * Times creating, binding and laying out a row, with drawing deliberately left out.
   *
   * `measure` + `layout` are included because that is where a `ComposeView` actually composes —
   * `setContent` only schedules it, so timing `onBindViewHolder` alone would report Compose as
   * free and be exactly wrong.
   *
   * The three numbers answer different questions, and only two of them mean the same thing on
   * both backends:
   *
   * - `create` is paid once per holder and amortised across the pool.
   * - `first bind` includes the initial composition, and *is* synchronous on both — a ComposeView
   *   composes inside its first `onMeasure`.
   * - `rebind` is honest for Views and a floor for Compose. Writing a `mutableStateOf` schedules a
   *   recomposition on the Recomposer, which dispatches on the next frame, so a `measure()` on the
   *   same stack still sees the old composition. Do not read the Compose number on that line as a
   *   steady-state cost; the frame-time run is the only place that cost is visible.
   */
  private fun perRowCost(name: String, make: () -> RecyclerView.Adapter<*>): RowCost {
    var create = 0L
    var firstBind = 0L
    var rebind = 0L

    activityRule.scenario.onActivity { activity ->
      @Suppress("UNCHECKED_CAST")
      val adapter = make() as RecyclerView.Adapter<RecyclerView.ViewHolder>
      val parent = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }

      // A `ComposeView` refuses to compose while detached — "Cannot locate windowRecomposer" —
      // so the rows have to be in a window even though this is not measuring drawing. Attaching
      // them to the content view does that: `addView` on an already-attached parent dispatches
      // `onAttachedToWindow` synchronously, so a recomposer is in place the moment a row is added.
      //
      // Everything below then stays on the main thread without yielding, which is what keeps the
      // container's own layout traversal from running between phases and composing the rows
      // before `firstBind` gets to time it.
      val container = FrameLayout(activity)
      activity.setContentView(container)

      val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
      val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

      fun layOut(holder: RecyclerView.ViewHolder) {
        holder.itemView.measure(widthSpec, heightSpec)
        holder.itemView.layout(0, 0, holder.itemView.measuredWidth, holder.itemView.measuredHeight)
      }

      // Warm up: the first holders of either backend pay for class loading and, for Compose, for
      // spinning up a Recomposer. Neither is a per-row cost.
      repeat(WARMUP_ROWS) {
        val holder = adapter.onCreateViewHolder(parent, 0)
        container.addView(holder.itemView)
        adapter.onBindViewHolder(holder, it)
        layOut(holder)
      }

      val holders = ArrayList<RecyclerView.ViewHolder>(SAMPLE_ROWS)
      create = time(SAMPLE_ROWS) { holders += adapter.onCreateViewHolder(parent, 0) }
      // Untimed on purpose: nothing in production adds a holder to a container by hand, and
      // charging it to one backend or the other would be charging the harness.
      holders.forEach { container.addView(it.itemView) }

      firstBind =
        time(SAMPLE_ROWS) { adapter.onBindViewHolder(holders[it], it); layOut(holders[it]) }
      // Same holders, new content — a recycled row, which is the steady state.
      rebind =
        time(SAMPLE_ROWS) {
          adapter.onBindViewHolder(holders[it], it + SAMPLE_ROWS)
          layOut(holders[it])
        }

      container.removeAllViews()
    }

    val per = { total: Long -> total / SAMPLE_ROWS / 1e6 }
    return RowCost(name, per(create), per(firstBind), per(rebind))
  }

  private inline fun time(count: Int, body: (Int) -> Unit): Long {
    val start = System.nanoTime()
    for (i in 0 until count) body(i)
    return System.nanoTime() - start
  }

  private fun density() =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

  // -------------------------------------------------------------------------------------------
  // Harness
  // -------------------------------------------------------------------------------------------

  private data class Result(
    val name: String,
    val frames: Int,
    val rowsBound: Int,
    val totalDurationMs: Double,
    val p50Ms: Double,
    val p90Ms: Double,
    val p99Ms: Double,
    val jankFrames: Int,
  ) {
    override fun toString(): String =
      "%-8s frames=%d  total=%.1fms  p50=%.2f  p90=%.2f  p99=%.2f  jank(>16.7ms)=%d"
        .format(name, frames, totalDurationMs, p50Ms, p90Ms, p99Ms, jankFrames)
  }

  private fun frames(name: String, adapter: () -> RecyclerView.Adapter<*>): Result {
    lateinit var list: RecyclerView
    lateinit var built: RecyclerView.Adapter<*>

    activityRule.scenario.onActivity { activity ->
      built = adapter()
      list = attach(activity, built)
    }

    // A cold first layout is a different question from steady-state scrolling, and including it
    // would let whichever backend warmed up more slowly dominate the comparison.
    drive(list, frames = WARMUP_FRAMES, collect = null)

    val durations = mutableListOf<Long>()
    val boundBefore = (built as CountingAdapter).bindCount
    drive(list, frames = MEASURED_FRAMES, collect = durations)
    val rowsBound = (built as CountingAdapter).bindCount - boundBefore

    activityRule.scenario.onActivity { it.setContentView(View(it)) }

    val sorted = durations.sorted()
    fun percentile(p: Double) = sorted[(sorted.size * p).toInt().coerceAtMost(sorted.size - 1)] / 1e6
    return Result(
      name = name,
      frames = sorted.size,
      rowsBound = rowsBound,
      totalDurationMs = sorted.sum() / 1e6,
      p50Ms = percentile(0.50),
      p90Ms = percentile(0.90),
      p99Ms = percentile(0.99),
      jankFrames = sorted.count { it / 1e6 > 16.7 },
    )
  }

  private fun attach(activity: Activity, adapter: RecyclerView.Adapter<*>): RecyclerView {
    val list =
      RecyclerView(activity).apply {
        layoutManager = LinearLayoutManager(activity)
        this.adapter = adapter
        // The library's own list will not use a fixed size, but the spike is comparing row cost
        // and a relayout of the RecyclerView itself is noise both backends would share unevenly.
        setHasFixedSize(true)
      }
    activity.setContentView(
      FrameLayout(activity).apply {
        addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
      }
    )
    return list
  }

  /**
   * Scrolls a fixed distance per frame, and collects that frame's total duration.
   *
   * `OnFrameMetricsAvailableListener` reports on a background thread *after* the frame is done,
   * so the latch counts scrolls rather than reports and the metrics list is drained afterwards —
   * the last frame or two of a run would otherwise arrive after the assertions.
   */
  private fun drive(list: RecyclerView, frames: Int, collect: MutableList<Long>?) {
    val reported = mutableListOf<Long>()
    var listener: android.view.Window.OnFrameMetricsAvailableListener? = null
    val metricsThread = HandlerThread("frame-metrics").apply { start() }

    if (collect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      activityRule.scenario.onActivity { activity ->
        listener =
          android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            synchronized(reported) {
              reported += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
          }
        activity.window.addOnFrameMetricsAvailableListener(listener!!, Handler(metricsThread.looper))
      }
    }

    val done = CountDownLatch(1)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      var remaining = frames
      Choreographer.getInstance().postFrameCallback(
        object : Choreographer.FrameCallback {
          override fun doFrame(frameTimeNanos: Long) {
            // Wrap rather than stop: 2,000 rows at SCROLL_PER_FRAME do not fill the run, and a
            // list that reached the end would stop binding and flatter the slower backend.
            if (!list.canScrollVertically(1)) list.scrollToPosition(0)
            list.scrollBy(0, SCROLL_PER_FRAME)
            if (--remaining > 0) Choreographer.getInstance().postFrameCallback(this)
            else done.countDown()
          }
        }
      )
    }
    check(done.await(60, TimeUnit.SECONDS)) { "the scroll driver never finished" }

    // Let the last frames' metrics land before tearing the listener down.
    Thread.sleep(250)
    listener?.let { l -> activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(l) } }
    metricsThread.quitSafely()
    synchronized(reported) { collect?.addAll(reported) }
  }

  private companion object {
    const val ROW_COUNT = 2_000
    const val WARMUP_FRAMES = 90
    const val MEASURED_FRAMES = 400

    /** Enough holders to amortise class loading and Compose's Recomposer startup. */
    const val WARMUP_ROWS = 20
    const val SAMPLE_ROWS = 200

    /** RecyclerView's default cache per view type, for reading the first-composition cost. */
    const val POOL_ESTIMATE = 5 + 2

    /** Roughly one 56dp row every three frames at mdpi — enough to keep binds continuous. */
    const val SCROLL_PER_FRAME = 24

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun labelFor(position: Int) = "Row $position"

    fun valueFor(position: Int) = "Value ${position % 97}"
  }

  /** Lets the harness report how many rows each run actually bound. */
  private interface CountingAdapter {
    val bindCount: Int
  }

  // -------------------------------------------------------------------------------------------
  // The two backends. Same content, same metrics, different renderer.
  // -------------------------------------------------------------------------------------------

  private class ViewRowAdapter :
    RecyclerView.Adapter<ViewRowAdapter.Holder>(), CountingAdapter {
    override var bindCount = 0
      private set

    class Holder(val root: LinearLayout, val label: TextView, val value: TextView) :
      RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val density = parent.resources.displayMetrics.density
      val label =
        TextView(parent.context).apply {
          textSize = 16f
          layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
      val value =
        TextView(parent.context).apply {
          textSize = 16f
          alpha = 0.6f
          layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        }
      val root =
        LinearLayout(parent.context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          val h = (16 * density).toInt()
          val v = (14 * density).toInt()
          setPadding(h, v, h, v)
          layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
          addView(label)
          addView(value)
        }
      return Holder(root, label, value)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      bindCount++
      holder.label.text = labelFor(position)
      holder.value.text = valueFor(position)
    }

    override fun getItemCount() = ROW_COUNT
  }

  private class ComposeRowAdapter :
    RecyclerView.Adapter<ComposeRowAdapter.Holder>(), CountingAdapter {
    override var bindCount = 0
      private set

    class Holder(val view: ComposeView) : RecyclerView.ViewHolder(view) {
      var label by mutableStateOf("")
      var value by mutableStateOf("")

      init {
        // The strategy that exists for exactly this pattern: dispose when the holder leaves the
        // window *or* when RecyclerView drops it from the pool, rather than leaking a composition
        // per recycled holder.
        view.setViewCompositionStrategy(
          ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )
        view.setContent {
          MaterialTheme {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(label, style = MaterialTheme.typography.bodyLarge)
              Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
      Holder(
        ComposeView(parent.context).apply {
          layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
        }
      )

    override fun onBindViewHolder(holder: Holder, position: Int) {
      bindCount++
      holder.label = labelFor(position)
      holder.value = valueFor(position)
    }

    override fun getItemCount() = ROW_COUNT
  }
}

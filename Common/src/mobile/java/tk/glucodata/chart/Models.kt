/*
 * Phase 2 — Data Layer Abstraction
 * Pure-Kotlin data models for the Vico chart migration.
 * No JNI / Android dependencies — safe to unit-test on the JVM.
 */
package tk.glucodata.chart

/**
 * Glucose measurement unit shown on the Y-axis.
 *
 *  MGDL   – milligrams per decilitre (integer display, e.g. "120")
 *  MMOL   – millimoles per litre (one decimal, e.g. "6.7")
 *
 * Maps to [Natives.getunit()]: 0 → MGDL, anything else → MMOL.
 */
enum class GlucoseUnit { MGDL, MMOL }

/**
 * A single glucose reading as consumed by the Compose chart layer.
 *
 * @param timestampMs  Wall-clock time of the reading in UTC milliseconds.
 * @param valueMgdL    Raw sensor value in mg/dL (conversion to mmol is done in the ViewModel).
 * @param sensorIndex  Zero-based index identifying which sensor produced this reading;
 *                     drives per-sensor colour coding in the chart.
 */
data class GlucoseEntry(
    val timestampMs: Long,
    val valueMgdL: Float,
    val sensorIndex: Int,
)

/**
 * The visible time/value window of the chart.
 *
 * @param startMs  Timestamp of the left edge (UTC ms).
 * @param endMs    Timestamp of the right edge (UTC ms).
 * @param minY     Bottom of the visible Y range in mg/dL.
 * @param maxY     Top of the visible Y range in mg/dL.
 */
data class ChartViewport(
    val startMs: Long,
    val endMs: Long,
    val minY: Float,
    val maxY: Float,
)

/**
 * Chart rendering settings sourced from native preferences.
 *
 * @param targetLow    Lower bound of the target glucose band (mg/dL). From [Natives.targetlow()].
 * @param targetHigh   Upper bound of the target glucose band (mg/dL). From [Natives.targethigh()].
 * @param invertColors True when the user has enabled inverted / dark mode in the app's own toggle.
 *                     From [Natives.getInvertColors()].
 * @param unit         Display unit for the Y-axis. Derived from [Natives.getunit()].
 * @param isRtl        True when the current locale is right-to-left (Arabic, Hebrew, …).
 *                     Derived from [MainActivity.rtl]; used to mirror surrounding stat panels
 *                     while keeping the time axis forced LTR.
 */
data class ChartSettings(
    val targetLow: Float,
    val targetHigh: Float,
    val invertColors: Boolean,
    val unit: GlucoseUnit,
    val isRtl: Boolean,
)

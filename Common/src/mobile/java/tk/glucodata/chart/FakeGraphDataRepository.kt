/*
 * Phase 2 — Data Layer Abstraction
 *
 * Test double for [GraphDataRepository].
 * Provides deterministic, in-memory data for unit tests and Compose previews.
 *
 * Visibility: kept in the mobile production source set so it is available to
 * both local (JVM) unit tests and instrumented (Android) tests without needing
 * a separate testFixtures configuration.  Mark usages with @VisibleForTesting.
 */
package tk.glucodata.chart

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [GraphDataRepository] whose state can be updated programmatically
 * via the [emitEntries], [emitViewport], and [emitSettings] methods.
 *
 * Default state mirrors a typical 3-hour glucose run at ~120 mg/dL so that
 * snapshot tests have something to render out of the box.
 */
class FakeGraphDataRepository(
    initialEntries:  List<GlucoseEntry>  = defaultEntries(),
    initialViewport: ChartViewport       = defaultViewport(),
    initialSettings: ChartSettings       = defaultSettings(),
) : GraphDataRepository {

    private val _entries  = MutableStateFlow(initialEntries)
    private val _viewport = MutableStateFlow(initialViewport)
    private val _settings = MutableStateFlow(initialSettings)

    // ── GraphDataRepository ───────────────────────────────────────────────────

    override fun observeGlucoseEntries(): Flow<List<GlucoseEntry>> = _entries.asStateFlow()
    override fun observeViewport():       Flow<ChartViewport>      = _viewport.asStateFlow()
    override fun observeSettings():       Flow<ChartSettings>      = _settings.asStateFlow()

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun emitEntries(entries: List<GlucoseEntry>)   { _entries.value  = entries  }
    fun emitViewport(viewport: ChartViewport)       { _viewport.value = viewport }
    fun emitSettings(settings: ChartSettings)       { _settings.value = settings }

    // ── Companion defaults ────────────────────────────────────────────────────

    companion object {
        /** 36 readings spaced 5 minutes apart (3 h), rising from 80 to 188 mg/dL. */
        fun defaultEntries(
            startMs: Long   = BASE_TIME_MS,
            count: Int      = 36,
            stepMs: Long    = 5 * 60 * 1_000L,
            sensorIndex: Int = 0,
        ): List<GlucoseEntry> = (0 until count).map { i ->
            GlucoseEntry(
                timestampMs  = startMs + i * stepMs,
                valueMgdL    = 80f + i * 3f,   // 80 → 185 mg/dL
                sensorIndex  = sensorIndex,
            )
        }

        fun defaultViewport(startMs: Long = BASE_TIME_MS): ChartViewport = ChartViewport(
            startMs = startMs,
            endMs   = startMs + 3 * 60 * 60 * 1_000L,   // 3 hours
            minY    = 40f,
            maxY    = 400f,
        )

        fun defaultSettings(): ChartSettings = ChartSettings(
            targetLow    = 70f,
            targetHigh   = 180f,
            invertColors = false,
            unit         = GlucoseUnit.MGDL,
            isRtl        = false,
        )

        /** Arbitrary fixed base time: 2024-01-01 00:00:00 UTC in milliseconds. */
        const val BASE_TIME_MS: Long = 1_704_067_200_000L
    }
}

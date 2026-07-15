/*
 * Phase 2 — Data Layer Abstraction
 * Pure-Kotlin repository interface — no JNI / Android imports.
 */
package tk.glucodata.chart

import kotlinx.coroutines.flow.Flow

/**
 * Contract between the data layer and the Compose chart UI.
 *
 * Implementations:
 *  - [NativeGraphDataRepository]  — reads from the C++ layer via JNI (transition period).
 *  - [FakeGraphDataRepository]    — deterministic test double.
 *  - PureKotlinGraphDataRepository — future full replacement (Phase 4+).
 *
 * All flows are **cold** by default; the ViewModel collects them inside a
 * `viewModelScope` and converts them to hot `StateFlow`s.
 */
interface GraphDataRepository {

    /**
     * Emits the full list of glucose entries whenever new data arrives from the sensor
     * or when the user scrolls/zooms the time window (i.e. the underlying native buffer
     * fills or the viewport changes).
     *
     * The list is ordered ascending by [GlucoseEntry.timestampMs].
     */
    fun observeGlucoseEntries(): Flow<List<GlucoseEntry>>

    /**
     * Emits the current chart viewport whenever it changes (user pan/zoom, day navigation,
     * or an automatic scroll to the latest reading).
     */
    fun observeViewport(): Flow<ChartViewport>

    /**
     * Emits chart rendering settings whenever the user changes a relevant preference
     * (target range, colour inversion, display unit, or locale).
     */
    fun observeSettings(): Flow<ChartSettings>
}

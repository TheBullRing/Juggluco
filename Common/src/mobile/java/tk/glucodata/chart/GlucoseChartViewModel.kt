/*
 * Phase 2 — Data Layer Abstraction
 *
 * ViewModel that sits between [GraphDataRepository] and the Compose UI layer.
 *
 * Current state (Phase 2):
 *   Exposes StateFlow<List<GlucoseEntry>>, StateFlow<ChartViewport>, and
 *   StateFlow<ChartSettings> as plain Kotlin types — no Vico dependency yet.
 *
 * Phase 3 update:
 *   A mapping extension will be added here to convert List<GlucoseEntry> into a
 *   Vico CartesianChartModel before exposing it to GlucoseChartScreen.
 */
package tk.glucodata.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Chart ViewModel.
 *
 * Construct via [GlucoseChartViewModel.Factory] or, once Hilt/Koin is introduced, via DI.
 *
 * @param repository Source of all glucose / settings data.
 */
class GlucoseChartViewModel(
    private val repository: GraphDataRepository,
) : ViewModel() {

    /**
     * Live list of glucose entries for the current chart viewport, ordered ascending
     * by [GlucoseEntry.timestampMs].  Starts as an empty list and updates whenever
     * the repository emits new data.
     *
     * Phase 3: this will be mapped to a Vico CartesianChartModel.
     */
    val glucoseEntries: StateFlow<List<GlucoseEntry>> =
        repository.observeGlucoseEntries()
            .stateIn(
                scope          = viewModelScope,
                started        = SharingStarted.WhileSubscribed(5_000L),
                initialValue   = emptyList(),
            )

    /**
     * Current chart viewport (time range + Y range).  Drives visible window in the UI.
     */
    val viewport: StateFlow<ChartViewport> =
        repository.observeViewport()
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ChartViewport(0L, 0L, 0f, 400f),
            )

    /**
     * Rendering settings (target band, colour mode, unit, RTL flag).
     */
    val settings: StateFlow<ChartSettings> =
        repository.observeSettings()
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ChartSettings(
                    targetLow    = 70f,
                    targetHigh   = 180f,
                    invertColors = false,
                    unit         = GlucoseUnit.MGDL,
                    isRtl        = false,
                ),
            )

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Simple [ViewModelProvider.Factory] for use before Hilt/Koin is wired up.
     *
     * Usage (in Activity / Fragment):
     * ```kotlin
     * val vm: GlucoseChartViewModel by viewModels {
     *     GlucoseChartViewModel.Factory(NativeGraphDataRepository.instance)
     * }
     * ```
     */
    class Factory(
        private val repository: GraphDataRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == GlucoseChartViewModel::class.java) {
                "Factory can only create GlucoseChartViewModel"
            }
            return GlucoseChartViewModel(repository) as T
        }
    }
}

/*
 * Phase 2 — Data Layer Abstraction
 * Transitional implementation that reads glucose data from the C++ layer via JNI.
 *
 * This class does NOT modify any native code.  It reads the same data structures
 * that the existing NanoVG renderer already reads, using only native methods that
 * are already declared in Natives.java.
 *
 * Threading contract
 * ──────────────────
 * All Flow emissions happen on [Dispatchers.IO] via [flowOn].  Callers (the
 * ViewModel) always collect on [Dispatchers.Main] via [stateIn].
 *
 * Lifecycle of data refresh
 * ──────────────────────────
 * Data is refreshed on every emission of [dataRefreshBus].  During Phase 2
 * (before the Compose screen is wired up) the bus is driven by a 1-second
 * heartbeat so the repository is testable end-to-end.  In Phase 3, GlucoseCurve
 * will additionally push to [notifyDataChanged] from its summaryready() callback.
 */
package tk.glucodata.chart

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import tk.glucodata.Natives
import tk.glucodata.MainActivity

/**
 * [GraphDataRepository] implementation backed by the existing JNI/C++ data layer.
 *
 * Obtain the singleton instance via [NativeGraphDataRepository.instance].
 */
class NativeGraphDataRepository private constructor() : GraphDataRepository {

    // ── Companion / singleton ─────────────────────────────────────────────────

    companion object {
        /** Singleton — one per process.  Thread-safe via double-checked locking. */
        @Volatile private var _instance: NativeGraphDataRepository? = null
        @JvmStatic
        val instance: NativeGraphDataRepository
            get() = _instance ?: synchronized(this) {
                _instance ?: NativeGraphDataRepository().also { _instance = it }
            }

        /**
         * Push-to-refresh bus.
         *
         * Call this from [tk.glucodata.GlucoseCurve.summaryready] (Phase 3) to trigger
         * an immediate data snapshot without waiting for the next heartbeat tick.
         *
         * ```java
         * // In GlucoseCurve.summaryready() — Phase 3 addition:
         * NativeGraphDataRepository.Companion.notifyDataChanged();
         * ```
         */
        private val _dataRefreshBus = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Public surface so Java callers (GlucoseCurve) can emit without reflection. */
        @JvmStatic
        fun notifyDataChanged() {
            _dataRefreshBus.tryEmit(Unit)
        }

        const val TAG = "VicoRepo"
    }

    // ── Heartbeat tick (1 s) ─────────────────────────────────────────────────

    /** Emits a Unit every second so the chart refreshes even without a native push. */
    private val heartbeat: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    /** Combined trigger: either a heartbeat or an explicit native push. */
    private val refreshTrigger: Flow<Unit> =
        merge(heartbeat, _dataRefreshBus.asSharedFlow())

    // ── GraphDataRepository ───────────────────────────────────────────────────

    override fun observeGlucoseEntries(): Flow<List<GlucoseEntry>> = flow {
        refreshTrigger.collect {
            emit(snapshotGlucoseEntries())
        }
    }.flowOn(Dispatchers.IO)

    override fun observeViewport(): Flow<ChartViewport> = flow {
        refreshTrigger.collect {
            emit(snapshotViewport())
        }
    }.flowOn(Dispatchers.IO)

    override fun observeSettings(): Flow<ChartSettings> = flow {
        refreshTrigger.collect {
            emit(snapshotSettings())
        }
    }.flowOn(Dispatchers.IO)

    // ── Private snapshot helpers ─────────────────────────────────────────────

    /**
     * Walks all active sensors via [Natives.activeSensorPtrs] and produces a
     * flat, time-sorted list of [GlucoseEntry] objects.
     *
     * streamfromSensorptr(ptr, pos) packed-long layout (from g.cpp source):
     *   bits  0-31  → Unix time in seconds (UTC) of the found entry
     *   bits 32-47  → glucose value in mg/dL (calibrated)
     *   bits 48-63  → ABSOLUTE index of the next entry to query
     *
     * When no more valid entries exist at or after [pos], bits 0-31 are 0 and
     * bits 48-63 hold the total pollcount (i.e. the end boundary).
     *
     * Iteration: start at 0, advance via nextPos in bits 48-63, stop when
     * bits 0-31 are 0 (no valid entry found from pos onward).
     *
     * NOTE: healthConnectfromSensorptr is NOT used here — it tracks the Health
     * Connect export cursor, which starts at 0 when Health Connect is disabled,
     * giving startIdx==endIdx==0 and reading nothing.
     */
    private fun snapshotGlucoseEntries(): List<GlucoseEntry> {
        val sensorPtrs: LongArray = Natives.activeSensorPtrs() ?: return emptyList()
        Log.d(TAG, "snapshotGlucoseEntries: activeSensorPtrs len=${sensorPtrs.size}")
        if (sensorPtrs.isEmpty()) return emptyList()

        val result = mutableListOf<GlucoseEntry>()

        sensorPtrs.forEachIndexed { sensorIndex, sensorPtr ->
            if (sensorPtr == 0L) return@forEachIndexed
            val name = Natives.namefromSensorptr(sensorPtr) ?: "sensor$sensorIndex"
            Log.d(TAG, "  sensor[$sensorIndex] ptr=$sensorPtr name=$name")

            var pos   = 0
            var count = 0
            while (true) {
                val packed  = Natives.streamfromSensorptr(sensorPtr, pos)
                val timeSec = packed and 0xFFFFFFFFL
                val nextPos = ((packed shr 48) and 0xFFFFL).toInt()

                if (timeSec == 0L) break   // no valid entry at or after pos

                val mgdL = ((packed shr 32) and 0xFFFFL).toFloat()
                if (mgdL > 0f) {
                    result += GlucoseEntry(timeSec * 1_000L, mgdL, sensorIndex)
                    count++
                }

                if (nextPos <= pos) break  // safety: no forward progress
                pos = nextPos
            }
            Log.d(TAG, "    entries collected=$count")
        }

        Log.d(TAG, "snapshotGlucoseEntries: total=${result.size}")
        return result.sortedBy { it.timestampMs }
    }

    /**
     * Reads the current chart viewport from the C++ renderer state.
     *
     * [Natives.getstarttime] / [Natives.getendtime] return Unix seconds (not ms).
     * [Natives.graphlow] / [Natives.graphhigh] return mg/dL float values.
     */
    private fun snapshotViewport(): ChartViewport = ChartViewport(
        startMs = Natives.getstarttime() * 1_000L,
        endMs   = Natives.getendtime()   * 1_000L,
        minY    = Natives.graphlow(),
        maxY    = Natives.graphhigh(),
    )

    /**
     * Reads per-render settings that affect chart appearance.
     * All Natives calls are fast synchronous reads from a shared native struct.
     */
    private fun snapshotSettings(): ChartSettings {
        // Natives.getunit() encoding (from Settings.java / javasettings.cpp):
        //   0 = not configured yet
        //   1 = mmol/L
        //   2 = mg/dL
        val ismmol = Natives.getunit() == 1

        // targetlow() / targethigh() call gconvert() in C++ which converts from
        // internal mg/L storage to the display unit (mmol/L or mg/dL).
        // Store them in DISPLAY units so no further conversion is needed in the UI.
        // GlucoseChart.yValue() must NOT divide again for these — they are passed
        // directly as threshold Y positions.
        return ChartSettings(
            targetLow    = Natives.targetlow(),
            targetHigh   = Natives.targethigh(),
            invertColors = Natives.getInvertColors(),
            unit         = if (ismmol) GlucoseUnit.MMOL else GlucoseUnit.MGDL,
            isRtl        = MainActivity.rtl,
        )
    }
}

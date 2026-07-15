/*
 * Unit tests for [GlucoseChartViewModel].
 *
 * Verifies:
 *   1. Initial StateFlow values match repository initial state.
 *   2. StateFlow updates when the repository emits new data.
 *   3. StateFlow remains consistent for multiple concurrent collectors.
 *
 * Uses [FakeGraphDataRepository] + kotlinx-coroutines-test [StandardTestDispatcher].
 */
package tk.glucodata.chart

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseChartViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope      = TestScope(testDispatcher)

    private lateinit var repo: FakeGraphDataRepository
    private lateinit var vm:   GlucoseChartViewModel

    @Before
    fun setUp() {
        // viewModelScope uses Dispatchers.Main; replace with the test dispatcher so
        // stateIn / coroutines run synchronously under test control.
        Dispatchers.setMain(testDispatcher)
        repo = FakeGraphDataRepository()
        vm   = GlucoseChartViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── glucoseEntries ────────────────────────────────────────────────────────

    @Test
    fun `glucoseEntries starts empty before first collection`() = runTest {
        // Before any subscriber, initialValue is emptyList.
        assertEquals(emptyList<GlucoseEntry>(), vm.glucoseEntries.value)
    }

    @Test
    fun `glucoseEntries collects from repository`() = testScope.runTest {
        vm.glucoseEntries.test {
            // First emission: stateIn initialValue (emptyList) before upstream starts.
            awaitItem() // emptyList

            // Pump the dispatcher so the stateIn coroutine starts collecting the repo.
            // FakeGraphDataRepository is a StateFlow — it emits its current value (36 entries)
            // as soon as the upstream coroutine runs.
            this@runTest.advanceUntilIdle()
            val entries = awaitItem()
            assertTrue(entries.isNotEmpty())
            assertEquals(36, entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `glucoseEntries updates when repository emits new data`() = testScope.runTest {
        vm.glucoseEntries.test {
            awaitItem()                      // stateIn initialValue: emptyList
            this@runTest.advanceUntilIdle()  // let stateIn start and emit the repo's current 36 entries
            awaitItem()                      // 36 default entries

            val extra = listOf(GlucoseEntry(9_999_999L, 200f, 0))
            repo.emitEntries(extra)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals(200f, updated[0].valueMgdL)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── viewport ──────────────────────────────────────────────────────────────

    @Test
    fun `viewport starts with safe default before collection`() {
        val vp = vm.viewport.value
        assertEquals(0L, vp.startMs)
        assertEquals(0L, vp.endMs)
        assertEquals(400f, vp.maxY)
    }

    @Test
    fun `viewport updates from repository`() = testScope.runTest {
        vm.viewport.test {
            awaitItem()                      // stateIn initialValue: (0L, 0L, 0f, 400f)
            this@runTest.advanceUntilIdle()  // pump stateIn upstream; repo emits (BASE_TIME_MS, …)
            val vp = awaitItem()
            assertEquals(FakeGraphDataRepository.BASE_TIME_MS, vp.startMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── settings ──────────────────────────────────────────────────────────────

    @Test
    fun `settings starts with safe default before collection`() {
        val s = vm.settings.value
        assertEquals(70f,  s.targetLow)
        assertEquals(180f, s.targetHigh)
        assertEquals(GlucoseUnit.MGDL, s.unit)
        assertEquals(false, s.isRtl)
    }

    @Test
    fun `settings reflects invertColors toggle`() = testScope.runTest {
        vm.settings.test {
            // stateIn initialValue == FakeGraphDataRepository.defaultSettings() — same values.
            // StateFlow deduplicates equal emissions, so only ONE item arrives before any change.
            awaitItem() // the single merged emission (initialValue = repo default, values equal)
            this@runTest.advanceUntilIdle()

            repo.emitSettings(FakeGraphDataRepository.defaultSettings().copy(invertColors = true))
            val updated = awaitItem()
            assertEquals(true, updated.invertColors)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    @Test
    fun `Factory creates GlucoseChartViewModel`() {
        val factory = GlucoseChartViewModel.Factory(repo)
        val created = factory.create(GlucoseChartViewModel::class.java)
        assertTrue(created is GlucoseChartViewModel)
    }
}

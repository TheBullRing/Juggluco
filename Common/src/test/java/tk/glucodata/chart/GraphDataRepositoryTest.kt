/*
 * Unit tests for [GraphDataRepository] contract.
 *
 * Tests run on the JVM (no Android runtime needed) using FakeGraphDataRepository
 * as the system under test.  The contract states:
 *   1. Each flow starts with the initial value.
 *   2. Emitting new state causes exactly one new emission.
 *   3. Multiple subscribers receive the same values.
 */
package tk.glucodata.chart

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GraphDataRepositoryTest {

    private lateinit var repo: FakeGraphDataRepository

    @Before
    fun setUp() {
        repo = FakeGraphDataRepository()
    }

    // ── observeGlucoseEntries ─────────────────────────────────────────────────

    @Test
    fun `observeGlucoseEntries emits initial default entries`() = runTest {
        repo.observeGlucoseEntries().test {
            val first = awaitItem()
            assertEquals(36, first.size)
            assertEquals(FakeGraphDataRepository.BASE_TIME_MS, first.first().timestampMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeGlucoseEntries emits new list after emitEntries`() = runTest {
        repo.observeGlucoseEntries().test {
            awaitItem() // consume initial

            val newEntries = listOf(
                GlucoseEntry(1_000L, 100f, 0),
                GlucoseEntry(2_000L, 110f, 0),
            )
            repo.emitEntries(newEntries)

            val updated = awaitItem()
            assertEquals(2, updated.size)
            assertEquals(100f, updated[0].valueMgdL)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeGlucoseEntries emits empty list`() = runTest {
        repo.emitEntries(emptyList())
        repo.observeGlucoseEntries().test {
            val item = awaitItem()
            assertTrue(item.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── observeViewport ───────────────────────────────────────────────────────

    @Test
    fun `observeViewport emits initial viewport`() = runTest {
        repo.observeViewport().test {
            val vp = awaitItem()
            assertEquals(FakeGraphDataRepository.BASE_TIME_MS, vp.startMs)
            assertTrue(vp.endMs > vp.startMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeViewport emits updated viewport`() = runTest {
        repo.observeViewport().test {
            awaitItem() // consume initial

            val newVp = ChartViewport(1_000L, 5_000L, 50f, 250f)
            repo.emitViewport(newVp)

            val updated = awaitItem()
            assertEquals(1_000L, updated.startMs)
            assertEquals(250f,   updated.maxY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── observeSettings ──────────────────────────────────────────────────────

    @Test
    fun `observeSettings emits default settings`() = runTest {
        repo.observeSettings().test {
            val s = awaitItem()
            assertEquals(70f,  s.targetLow)
            assertEquals(180f, s.targetHigh)
            assertEquals(GlucoseUnit.MGDL, s.unit)
            assertEquals(false, s.invertColors)
            assertEquals(false, s.isRtl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSettings reflects RTL state change`() = runTest {
        repo.observeSettings().test {
            awaitItem() // consume initial

            repo.emitSettings(FakeGraphDataRepository.defaultSettings().copy(isRtl = true))
            val updated = awaitItem()
            assertEquals(true, updated.isRtl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSettings reflects MMOL unit change`() = runTest {
        repo.observeSettings().test {
            awaitItem() // consume initial

            repo.emitSettings(FakeGraphDataRepository.defaultSettings().copy(unit = GlucoseUnit.MMOL))
            val updated = awaitItem()
            assertEquals(GlucoseUnit.MMOL, updated.unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GlucoseEntry data class ───────────────────────────────────────────────

    @Test
    fun `GlucoseEntry equality is structural`() {
        val a = GlucoseEntry(1_000L, 120f, 0)
        val b = GlucoseEntry(1_000L, 120f, 0)
        assertEquals(a, b)
    }

    @Test
    fun `defaultEntries produces ascending timestamps`() {
        val entries = FakeGraphDataRepository.defaultEntries()
        for (i in 1 until entries.size) {
            assertTrue(entries[i].timestampMs > entries[i - 1].timestampMs)
        }
    }
}

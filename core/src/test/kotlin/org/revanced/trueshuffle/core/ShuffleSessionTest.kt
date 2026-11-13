package org.revanced.trueshuffle.core

import java.security.SecureRandom
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShuffleSessionTest {
    private fun entries(size: Int) = List(size) { PlaylistEntry("entry-$it", "video-${it / 2}") }

    @Test fun `each required size preserves every distinct occurrence`() {
        for (size in listOf(0, 1, 2, 10, 100, 500, 1_000, 2_000, 5_000, 10_000)) {
            val source = entries(size)
            val session = ShuffleSession.create("playlist", source, Random(42))
            val result = session.snapshot().entries
            assertEquals(size, result.size)
            assertEquals(source.toSet(), result.toSet())
            assertEquals(size, result.map { it.occurrenceId }.toSet().size)
            assertEquals(source.groupingBy { it.videoId }.eachCount(), result.groupingBy { it.videoId }.eachCount())
            assertEquals(if (size == 0) -1 else 0, session.position)
        }
    }

    @Test fun `next and previous traverse the same order without wrapping`() {
        val session = ShuffleSession.create("playlist", entries(100), Random(1))
        val order = session.snapshot().entries
        assertEquals(order.first(), session.current)
        assertNull(session.previous())
        for (i in 1 until order.size) assertEquals(order[i], session.next())
        assertNull(session.next())
        assertNull(session.next())
        for (i in order.lastIndex - 1 downTo 0) assertEquals(order[i], session.previous())
        assertNull(session.previous())
        for (i in 1 until order.size) assertEquals(order[i], session.next())
    }

    @Test fun `empty and single element sessions do not wrap`() {
        for (size in 0..1) {
            val session = ShuffleSession.create("playlist", entries(size), Random(1))
            assertNull(session.next())
            assertNull(session.previous())
            assertEquals(entries(size).firstOrNull(), session.current)
        }
    }

    @Test fun `restore preserves cursor selection and unavailable occurrences`() {
        val session = ShuffleSession.create("playlist", entries(10), Random(1))
        val order = session.snapshot().entries
        assertEquals(order[3], session.select(order[3].occurrenceId))
        assertTrue(session.markUnavailable(order[4].occurrenceId))
        assertFalse(session.markUnavailable("missing"))
        val restored = ShuffleSession.restore(session.snapshot())
        assertEquals(order[3], restored.current)
        assertEquals(order[5], restored.next())
        assertEquals(order[3], restored.previous())
        assertNull(restored.select(order[4].occurrenceId))
        assertNull(restored.select("missing"))
        assertEquals(3, restored.position)
    }

    @Test fun `unavailable current occurrence requires explicit navigation or end handling`() {
        val session = ShuffleSession.create("p", entries(3), Random(9))
        val order = session.snapshot().entries
        assertTrue(session.markUnavailable(order[0].occurrenceId))
        assertNull(session.current)
        assertEquals(order[1], session.next())
        assertNull(session.previous())
        assertEquals(order[2], session.next())
        assertTrue(session.markUnavailable(order[2].occurrenceId))
        assertNull(session.current)
        assertNull(session.next())
        assertEquals(order[1], session.previous())
    }

    @Test fun `restore rejects malformed snapshots`() {
        val first = PlaylistEntry("one", "video")
        assertFailsWith<IllegalArgumentException> { ShuffleSession.restore(ShuffleSnapshot("", listOf(first), 0)) }
        assertFailsWith<IllegalArgumentException> { ShuffleSession.restore(ShuffleSnapshot("p", listOf(first, first), 0)) }
        assertFailsWith<IllegalArgumentException> { ShuffleSession.restore(ShuffleSnapshot("p", listOf(first), -1)) }
        assertFailsWith<IllegalArgumentException> { ShuffleSession.restore(ShuffleSnapshot("p", emptyList(), 0)) }
        assertFailsWith<IllegalArgumentException> { ShuffleSession.restore(ShuffleSnapshot("p", listOf(first), 0, setOf("missing"))) }
        assertFailsWith<IllegalArgumentException> { PlaylistEntry(" ", "video") }
    }

    @Test fun `snapshots do not expose mutable internal state`() {
        val input = entries(5).toMutableList()
        val session = ShuffleSession.create("p", input, Random(1))
        val snapshot = session.snapshot()
        input.clear()
        (snapshot.entries as MutableList).clear()
        assertEquals(5, session.snapshot().entries.size)
    }

    @Test fun `seeded shuffle is reproducible and explicit reshuffle creates a new order`() {
        val source = entries(100)
        val first = ShuffleSession.create("p", source, Random(123)).snapshot().entries
        assertEquals(first, ShuffleSession.create("p", source, Random(123)).snapshot().entries)
        assertNotEquals(first, ShuffleSession.create("p", source, Random(124)).snapshot().entries)
    }

    @Test fun `fixed seeded trials show no obvious position bias`() {
        checkPositionDistribution(Random(5326), 30_000)
    }

    @Test fun `independent random seeded trials show no obvious position bias`() {
        val seeds = SecureRandom()
        val counts = Array(5) { IntArray(5) }
        repeat(10_000) { countPositions(counts, Random(seeds.nextLong())) }
        assertDistribution(counts, 10_000)
    }

    private fun checkPositionDistribution(random: Random, trials: Int) {
        val counts = Array(5) { IntArray(5) }
        repeat(trials) { countPositions(counts, random) }
        assertDistribution(counts, trials)
    }

    private fun countPositions(counts: Array<IntArray>, random: Random) {
        val order = ShuffleSession.create("p", entries(5), random).snapshot().entries
        order.forEachIndexed { position, entry ->
            val item = entry.occurrenceId.removePrefix("entry-").toInt()
            counts[item][position]++
        }
    }

    private fun assertDistribution(counts: Array<IntArray>, trials: Int) {
        val expected = trials / 5.0
        // Conservative six-sigma per cell bound; 25 correlated cells. Detects large bias,
        // not a proof of uniformity, and has an extremely low false-positive rate.
        val tolerance = 6.0 * kotlin.math.sqrt(trials * 0.2 * 0.8)
        for (row in counts) for (count in row) {
            assertTrue(kotlin.math.abs(count - expected) < tolerance, "count=$count expected=$expected tolerance=$tolerance")
        }
    }
}

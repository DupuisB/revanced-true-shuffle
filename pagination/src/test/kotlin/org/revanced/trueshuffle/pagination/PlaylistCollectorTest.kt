package org.revanced.trueshuffle.pagination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaylistCollectorTest {
    private fun entry(i: Int) = PlaylistEntry("entry-$i", "video-${i % 3}")

    @Test fun `collects single and empty terminal pages`() {
        for (size in listOf(0, 1, 10)) {
            val entries = List(size, ::entry)
            val collector = PlaylistCollector(PageSource { PlaylistPage("r1", entries, Continuation.Complete, size) })
            assertEquals(entries, collector.collect())
        }
    }

    @Test fun `collects 10000 occurrences across pages without collapsing repeated video ids`() {
        val entries = List(10_000, ::entry)
        var requests = 0
        val progress = mutableListOf<Int>()
        val collector = PlaylistCollector(PageSource { token ->
            requests++
            val start = token?.toInt() ?: 0
            val end = (start + 50).coerceAtMost(entries.size)
            PlaylistPage("stable", entries.subList(start, end),
                if (end == entries.size) Continuation.Complete else Continuation.More(end.toString()), entries.size)
        })
        assertEquals(entries, collector.collect { progress.add(it) })
        assertEquals(200, requests)
        assertEquals(10_000, progress.last())
    }

    @Test fun `rejects repeated continuation and duplicated occurrence`() {
        val loop = PlaylistCollector(PageSource { PlaylistPage("r", emptyList(), Continuation.More("x")) })
        assertFailsWith<IncompletePlaylistException> { loop.collect() }
        val duplicate = PlaylistCollector(PageSource { token ->
            PlaylistPage("r", listOf(entry(1)), if (token == null) Continuation.More("x") else Continuation.Complete)
        })
        assertFailsWith<IncompletePlaylistException> { duplicate.collect() }
    }

    @Test fun `rejects changed snapshot and mismatched counts`() {
        val changed = PlaylistCollector(PageSource { token ->
            PlaylistPage(if (token == null) "r1" else "r2", emptyList(),
                if (token == null) Continuation.More("x") else Continuation.Complete)
        })
        assertFailsWith<IncompletePlaylistException> { changed.collect() }
        val missing = PlaylistCollector(PageSource { PlaylistPage("r", listOf(entry(1)), Continuation.Complete, 2) })
        assertFailsWith<IncompletePlaylistException> { missing.collect() }
        val countsChange = PlaylistCollector(PageSource { token ->
            PlaylistPage("r", emptyList(), if (token == null) Continuation.More("x") else Continuation.Complete,
                if (token == null) 1 else 2)
        })
        assertFailsWith<IncompletePlaylistException> { countsChange.collect() }
    }

    @Test fun `rejects invalid source data and limits`() {
        val cases = listOf(
            PlaylistPage("", emptyList(), Continuation.Complete),
            PlaylistPage("r", listOf(PlaylistEntry("", "v")), Continuation.Complete),
            PlaylistPage("r", listOf(PlaylistEntry("e", "")), Continuation.Complete),
            PlaylistPage("r", emptyList(), Continuation.More("")),
            PlaylistPage("r", emptyList(), Continuation.Complete, -1),
        )
        for (page in cases) assertFailsWith<IncompletePlaylistException> { PlaylistCollector(PageSource { page }).collect() }
        assertFailsWith<IncompletePlaylistException> {
            PlaylistCollector(PageSource { PlaylistPage("r", listOf(entry(1), entry(2)), Continuation.Complete) },
                CollectionLimits(maxEntries = 1)).collect()
        }
        assertFailsWith<IncompletePlaylistException> {
            PlaylistCollector(PageSource { PlaylistPage("r", emptyList(), Continuation.More("next")) },
                CollectionLimits(maxPages = 1)).collect()
        }
        assertFailsWith<IncompletePlaylistException> {
            PlaylistCollector(PageSource { PlaylistPage("r", listOf(entry(1)), Continuation.Complete) },
                CollectionLimits(maxRetainedIdentifierChars = 5)).collect()
        }
        assertFailsWith<IncompletePlaylistException> {
            PlaylistCollector(PageSource { PlaylistPage("long", emptyList(), Continuation.Complete) },
                CollectionLimits(maxRetainedIdentifierChars = 1)).collect()
        }
    }

    @Test fun `retries only explicitly transient failures with bounded exponential backoff`() {
        var requests = 0
        val delays = mutableListOf<Long>()
        val collector = PlaylistCollector(PageSource {
            requests++
            if (requests < 3) throw TransientPageException("rate limited")
            PlaylistPage("r", emptyList(), Continuation.Complete, 0)
        }, sleep = { delays.add(it) })
        assertTrue(collector.collect().isEmpty())
        assertEquals(3, requests)
        assertEquals(listOf(250L, 500L), delays)

        requests = 0
        val failing = PlaylistCollector(PageSource { requests++; throw TransientPageException("network") },
            sleep = {})
        assertFailsWith<TransientPageException> { failing.collect() }
        assertEquals(3, requests)
        assertFailsWith<IllegalStateException> {
            PlaylistCollector(PageSource { throw IllegalStateException("malformed response") }, sleep = {}).collect()
        }
    }

    @Test fun `propagates cancellation from source and backoff`() {
        assertFailsWith<InterruptedException> {
            PlaylistCollector(PageSource { throw InterruptedException("cancelled") }).collect()
        }
        var requests = 0
        assertFailsWith<InterruptedException> {
            PlaylistCollector(PageSource { requests++; throw TransientPageException("retry") },
                sleep = { throw InterruptedException("cancelled") }).collect()
        }
        assertEquals(1, requests)
    }

    @Test fun `cancellation from progress prevents another page request`() {
        var requests = 0
        try {
            assertFailsWith<InterruptedException> {
                PlaylistCollector(PageSource {
                    requests++
                    PlaylistPage("r", listOf(entry(1)), Continuation.More("next"))
                }).collect { Thread.currentThread().interrupt() }
            }
            assertEquals(1, requests)
        } finally {
            Thread.interrupted()
        }
    }
}

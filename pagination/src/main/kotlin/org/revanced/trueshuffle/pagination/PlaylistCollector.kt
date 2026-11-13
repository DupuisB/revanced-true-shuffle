package org.revanced.trueshuffle.pagination

/**
 * A distinct playlist occurrence, not a distinct video. Two occurrences may have the same videoId.
 * Adapters must obtain a stable, unique occurrence ID from the source, not synthesize one from
 * the video ID. Inaccessible entries must be excluded according to the adapter's documented policy.
 */
data class PlaylistEntry(val occurrenceId: String, val videoId: String)

/** Explicit terminal marker: absence of a continuation in an unrecognized response is not enough. */
sealed interface Continuation {
    data class More(val token: String) : Continuation
    data object Complete : Continuation
}

/**
 * [snapshotId] is a source-backed snapshot/revision identifier. It must remain stable across
 * pages and change if the accessible sequence changes. A source which cannot provide this
 * guarantee must not invent an identifier or claim that this collector proves completeness.
 * [expectedAccessibleEntries], when supplied, must count accessible occurrences, not videos.
 */
data class PlaylistPage(
    val snapshotId: String,
    val entries: List<PlaylistEntry>,
    val continuation: Continuation,
    val expectedAccessibleEntries: Int? = null,
)

/** Implementations must honor interruption/cancellation of in-flight network work. */
fun interface PageSource {
    @Throws(TransientPageException::class, InterruptedException::class)
    fun load(token: String?): PlaylistPage
}

/** Only failures explicitly classified as transient by the adapter are retried. */
class TransientPageException(message: String, cause: Throwable? = null) : Exception(message, cause)

class IncompletePlaylistException(message: String) : Exception(message)

data class CollectionLimits(
    val maxPages: Int = 1_000,
    val maxEntries: Int = 100_000,
    val maxTokenLength: Int = 16_384,
    val maxIdentifierLength: Int = 4_096,
    val maxRetainedIdentifierChars: Long = 8_000_000,
    val retriesPerPage: Int = 2,
    val initialRetryDelayMillis: Long = 250,
) {
    init {
        require(maxPages > 0 && maxEntries > 0 && maxTokenLength > 0 && maxIdentifierLength > 0 && maxRetainedIdentifierChars > 0)
        require(retriesPerPage in 0..10 && initialRetryDelayMillis in 0..60_000)
    }
}

/**
 * Collects sequential pages off the UI thread. A result is returned only after an explicit
 * terminal page passes all checks. Exceptions never expose a partial list as a complete result.
 * The source must supply a real snapshot guarantee and distinguish terminal and malformed pages.
 */
class PlaylistCollector(
    private val source: PageSource,
    private val limits: CollectionLimits = CollectionLimits(),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    @Throws(IncompletePlaylistException::class, TransientPageException::class, InterruptedException::class)
    fun collect(onProgress: (Int) -> Unit = {}): List<PlaylistEntry> {
        val result = ArrayList<PlaylistEntry>()
        val seenEntries = HashSet<String>()
        val seenTokens = HashSet<String>()
        var token: String? = null
        var revision: String? = null
        var expectedCount: Int? = null
        var retainedChars = 0L

        repeat(limits.maxPages) {
            checkInterrupted()
            val page = loadWithRetry(token)
            checkInterrupted()
            if (page.snapshotId.isBlank() || page.snapshotId.length > limits.maxIdentifierLength) {
                throw IncompletePlaylistException("Missing or invalid snapshot identifier")
            }
            if (revision == null) {
                revision = page.snapshotId
                retainedChars += page.snapshotId.length
                if (retainedChars > limits.maxRetainedIdentifierChars) throw IncompletePlaylistException("Identifier budget exceeded")
            }
            if (revision != page.snapshotId) throw IncompletePlaylistException("Playlist snapshot changed")
            page.expectedAccessibleEntries?.let { count ->
                if (count < 0 || count > limits.maxEntries) throw IncompletePlaylistException("Invalid accessible entry count")
                if (expectedCount != null && expectedCount != count) throw IncompletePlaylistException("Accessible entry count changed")
                expectedCount = count
            }
            if (page.entries.size > limits.maxEntries - result.size) throw IncompletePlaylistException("Entry limit exceeded")
            for (entry in page.entries) {
                checkInterrupted()
                if (entry.occurrenceId.isBlank() || entry.videoId.isBlank() ||
                    entry.occurrenceId.length > limits.maxIdentifierLength || entry.videoId.length > limits.maxIdentifierLength
                ) throw IncompletePlaylistException("Invalid entry identifier")
                retainedChars += entry.occurrenceId.length.toLong() + entry.videoId.length
                if (retainedChars > limits.maxRetainedIdentifierChars) throw IncompletePlaylistException("Identifier budget exceeded")
                if (!seenEntries.add(entry.occurrenceId)) throw IncompletePlaylistException("Repeated playlist occurrence")
                result.add(entry)
            }
            if (expectedCount != null && result.size > expectedCount!!) throw IncompletePlaylistException("Entry count exceeded")
            onProgress(result.size)
            checkInterrupted()
            when (val next = page.continuation) {
                Continuation.Complete -> {
                    if (expectedCount != null && result.size != expectedCount) throw IncompletePlaylistException("Entry count incomplete")
                    return result.toList()
                }
                is Continuation.More -> {
                    if (next.token.isBlank() || next.token.length > limits.maxTokenLength || !seenTokens.add(next.token)) {
                        throw IncompletePlaylistException("Invalid or repeated continuation")
                    }
                    retainedChars += next.token.length
                    if (retainedChars > limits.maxRetainedIdentifierChars) throw IncompletePlaylistException("Identifier budget exceeded")
                    token = next.token
                }
            }
        }
        throw IncompletePlaylistException("Page limit exceeded")
    }

    private fun loadWithRetry(token: String?): PlaylistPage {
        var failures = 0
        while (true) {
            checkInterrupted()
            try {
                return source.load(token)
            } catch (failure: TransientPageException) {
                if (failures >= limits.retriesPerPage) throw failure
                val delay = (limits.initialRetryDelayMillis * (1L shl failures)).coerceAtMost(60_000L)
                failures++
                sleep(delay)
            }
        }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Playlist collection cancelled")
    }
}

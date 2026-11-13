package org.revanced.trueshuffle.core

import java.security.SecureRandom
import java.util.Random

/** A playlist occurrence, not merely a video: duplicate video IDs are permitted. */
data class PlaylistEntry(val occurrenceId: String, val videoId: String) {
    init {
        require(occurrenceId.isNotBlank())
        require(videoId.isNotBlank())
    }
}

/** Serializable by an adapter. No playlist data is fetched or persisted by this module. */
data class ShuffleSnapshot(
    val playlistId: String,
    val entries: List<PlaylistEntry>,
    val position: Int,
    val unavailable: Set<String> = emptySet(),
)

/**
 * A shuffled traversal of a *complete, externally verified* playlist snapshot.
 * Callers are responsible for enumeration, playback, persistence and synchronization.
 * Explicit selection and backwards navigation can revisit entries by design.
 */
class ShuffleSession private constructor(snapshot: ShuffleSnapshot) {
    val playlistId: String = snapshot.playlistId
    private val order: List<PlaylistEntry> = snapshot.entries.toList()
    private val indices: Map<String, Int> = order.mapIndexed { i, e -> e.occurrenceId to i }.toMap()
    private val unavailable: MutableSet<String> = snapshot.unavailable.toMutableSet()
    var position: Int = snapshot.position
        private set

    init {
        require(playlistId.isNotBlank())
        require(indices.size == order.size) { "Playlist occurrence IDs must be unique" }
        require(if (order.isEmpty()) position == -1 else position in order.indices)
        require(unavailable.all(indices::containsKey)) { "Unknown unavailable occurrence" }
    }

    val current: PlaylistEntry? get() = order.getOrNull(position)?.takeUnless { it.occurrenceId in unavailable }

    /** Returns null at the end without wrapping or changing the cursor. */
    fun next(): PlaylistEntry? = move(1)

    /** Returns null at the beginning without wrapping or changing the cursor. */
    fun previous(): PlaylistEntry? = move(-1)

    private fun move(direction: Int): PlaylistEntry? {
        var candidate = position + direction
        while (candidate in order.indices) {
            val entry = order[candidate]
            if (entry.occurrenceId !in unavailable) {
                position = candidate
                return entry
            }
            candidate += direction
        }
        return null
    }

    /** Move to a particular occurrence, as requested explicitly by the user. */
    fun select(occurrenceId: String): PlaylistEntry? {
        val target = indices[occurrenceId] ?: return null
        if (occurrenceId in unavailable) return null
        position = target
        return order[target]
    }

    /**
     * Skip this occurrence on future navigation; does not remove duplicate video IDs.
     * Marking the current occurrence leaves the cursor in place and [current] null;
     * the caller must explicitly navigate or handle the end of the traversal.
     */
    fun markUnavailable(occurrenceId: String): Boolean {
        if (occurrenceId !in indices) return false
        unavailable.add(occurrenceId)
        return true
    }

    fun snapshot(): ShuffleSnapshot = ShuffleSnapshot(playlistId, order.toList(), position, unavailable.toSet())

    companion object {
        /** Fisher–Yates uses a bounded uniform random integer for each position. */
        fun create(playlistId: String, entries: List<PlaylistEntry>, random: Random = SecureRandom()): ShuffleSession {
            val shuffled = entries.toMutableList()
            for (i in shuffled.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = shuffled[i]
                shuffled[i] = shuffled[j]
                shuffled[j] = tmp
            }
            return restore(ShuffleSnapshot(playlistId, shuffled, if (shuffled.isEmpty()) -1 else 0))
        }

        fun restore(snapshot: ShuffleSnapshot): ShuffleSession = ShuffleSession(snapshot)
    }
}

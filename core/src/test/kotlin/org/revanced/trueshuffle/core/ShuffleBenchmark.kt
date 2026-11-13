package org.revanced.trueshuffle.core

import java.lang.management.ManagementFactory
import java.util.Random

/** Manual benchmark: JVM, 10,000 preconstructed entries, seeded non-cryptographic RNG. */
fun main() {
    val source = List(10_000) { PlaylistEntry("entry-$it", "video-$it") }
    repeat(50) { ShuffleSession.create("p", source, Random(it.toLong())) }
    val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    val threadId = Thread.currentThread().threadId()
    val canMeasureAllocations = bean?.isThreadAllocatedMemorySupported == true
    if (canMeasureAllocations) bean!!.isThreadAllocatedMemoryEnabled = true
    val beforeBytes = if (canMeasureAllocations) bean!!.getThreadAllocatedBytes(threadId) else -1L
    val times = LongArray(200)
    repeat(times.size) {
        val start = System.nanoTime()
        val session = ShuffleSession.create("p", source, Random(it.toLong()))
        check(session.current != null)
        times[it] = System.nanoTime() - start
    }
    val afterBytes = if (canMeasureAllocations) bean!!.getThreadAllocatedBytes(threadId) else -1L
    times.sort()
    println("N=10000 warmup=50 samples=${times.size} median_ms=${times[times.size / 2] / 1e6} p95_ms=${times[times.size * 95 / 100] / 1e6} allocated_bytes_per_run=${if (canMeasureAllocations) (afterBytes - beforeBytes) / times.size else "unavailable"}")
}

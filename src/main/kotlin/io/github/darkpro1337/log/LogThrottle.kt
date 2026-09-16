package io.github.darkpro1337.log

import kotlin.time.Duration

/**
 * Allows at most one emit per [key] per [interval].
 * Callers decide what to log; this only answers "should we log now?".
 */
class LogThrottle(
    private val interval: Duration,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    data class Emit(val suppressedCount: Int)

    private val lock = Any()
    private val lastEmitAtMs = mutableMapOf<String, Long>()
    private val suppressed = mutableMapOf<String, Int>()

    fun tryEmit(key: String): Emit? = synchronized(lock) {
        val now = clockMs()
        val last = lastEmitAtMs[key]
        if (last == null || now - last >= interval.inWholeMilliseconds) {
            val count = suppressed[key] ?: 0
            lastEmitAtMs[key] = now
            suppressed[key] = 0
            Emit(count)
        } else {
            suppressed[key] = (suppressed[key] ?: 0) + 1
            null
        }
    }
}

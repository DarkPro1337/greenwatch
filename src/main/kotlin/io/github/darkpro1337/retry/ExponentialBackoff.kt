package io.github.darkpro1337.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Pure exponential delay: attempt 1 → [initial], then ×2 until [max]. */
class ExponentialBackoff(
    private val initial: Duration,
    private val max: Duration,
) {
    init {
        require(initial.isPositive()) { "initial backoff must be positive" }
        require(max >= initial) { "max backoff must be >= initial" }
    }

    fun delay(attempt: Int): Duration {
        require(attempt >= 1) { "attempt must be >= 1" }
        val shift = (attempt - 1).coerceAtMost(20)
        val millis = initial.inWholeMilliseconds.timesSaturating(1L shl shift)
        return minOf(max, millis.milliseconds)
    }
}

private fun Long.timesSaturating(multiplier: Long): Long =
    try {
        Math.multiplyExact(this, multiplier)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

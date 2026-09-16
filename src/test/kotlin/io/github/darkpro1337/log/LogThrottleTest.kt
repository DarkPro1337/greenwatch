package io.github.darkpro1337.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class LogThrottleTest {
    @Test
    fun `emits first event then suppresses until interval`() {
        var now = 0L
        val throttle = LogThrottle(interval = 5.seconds, clockMs = { now })

        val first = throttle.tryEmit("k")
        assertNotNull(first)
        assertEquals(0, first.suppressedCount)
        assertNull(throttle.tryEmit("k"))
        assertNull(throttle.tryEmit("k"))

        now = 5_000
        val later = throttle.tryEmit("k")
        assertNotNull(later)
        assertEquals(2, later.suppressedCount)
    }

    @Test
    fun `keys are independent`() {
        val throttle = LogThrottle(interval = 5.seconds, clockMs = { 0L })
        assertNotNull(throttle.tryEmit("a"))
        assertNotNull(throttle.tryEmit("b"))
        assertNull(throttle.tryEmit("a"))
    }
}

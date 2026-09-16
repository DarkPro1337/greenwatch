package io.github.darkpro1337.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ExponentialBackoffTest {
    private val backoff = ExponentialBackoff(initial = 1.seconds, max = 30.seconds)

    @Test
    fun `doubles until max`() {
        assertEquals(1.seconds, backoff.delay(1))
        assertEquals(16.seconds, backoff.delay(5))
        assertEquals(30.seconds, backoff.delay(10))
    }
}

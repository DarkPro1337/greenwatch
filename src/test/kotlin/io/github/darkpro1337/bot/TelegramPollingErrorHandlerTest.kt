package io.github.darkpro1337.bot

import com.github.kotlintelegrambot.errors.RetrieveUpdatesError
import io.github.darkpro1337.log.LogThrottle
import io.github.darkpro1337.retry.ExponentialBackoff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TelegramPollingErrorHandlerTest {
    @Test
    fun `throttles logs and backs off without inspecting the message`() {
        var now = 0L
        val warns = mutableListOf<String>()
        val sleeps = mutableListOf<Duration>()
        val handler = TelegramPollingErrorHandler(
            logThrottle = LogThrottle(interval = 1.seconds, clockMs = { now }),
            backoff = ExponentialBackoff(initial = 1.seconds, max = 30.seconds),
            warn = { warns += it },
            sleep = { sleeps += it },
            clockMs = { now },
            idleReset = 2.seconds,
        )

        repeat(5) { handler.onError(RetrieveUpdatesError("api.telegram.org")) }
        assertEquals(listOf("api.telegram.org"), warns)
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds, 8.seconds, 16.seconds), sleeps)

        now = 1_000
        handler.onError(RetrieveUpdatesError("502 Bad Gateway"))
        assertEquals(2, warns.size)
        assertEquals("502 Bad Gateway (repeated 4x since last log)", warns[1])
        assertEquals(30.seconds, sleeps.last())
    }

    @Test
    fun `idle gap resets backoff`() {
        var now = 0L
        val sleeps = mutableListOf<Duration>()
        val handler = TelegramPollingErrorHandler(
            logThrottle = LogThrottle(interval = 1.seconds, clockMs = { now }),
            backoff = ExponentialBackoff(initial = 1.seconds, max = 30.seconds),
            warn = {},
            sleep = { sleeps += it },
            clockMs = { now },
            idleReset = 2.seconds,
        )

        handler.onError(RetrieveUpdatesError("timeout"))
        now = 2_000
        handler.onError(RetrieveUpdatesError("timeout"))
        assertEquals(listOf(1.seconds, 1.seconds), sleeps)
    }
}

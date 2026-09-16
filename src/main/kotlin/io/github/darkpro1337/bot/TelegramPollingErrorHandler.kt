package io.github.darkpro1337.bot

import com.github.kotlintelegrambot.errors.TelegramError
import io.github.darkpro1337.log.LogThrottle
import io.github.darkpro1337.retry.ExponentialBackoff
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * kotlin-telegram-bot flattens getUpdates failures ([com.github.kotlintelegrambot.types.TelegramBotResult.Error])
 * into [TelegramError] with only a message string. This hook is already scoped to polling,
 * so every event is treated as a retryable retrieve-updates failure — no message parsing.
 *
 * Sleeps on the updater thread because the library retries immediately with no delay.
 */
class TelegramPollingErrorHandler(
    private val logThrottle: LogThrottle,
    private val backoff: ExponentialBackoff,
    private val warn: (String) -> Unit,
    private val sleep: (Duration) -> Unit,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val idleReset: Duration = 2.minutes,
) {
    private val lock = Any()
    private var consecutive = 0
    private var lastFailureAtMs: Long? = null

    fun onError(error: TelegramError) {
        val attempt: Int
        val emit: LogThrottle.Emit?
        synchronized(lock) {
            val now = clockMs()
            val last = lastFailureAtMs
            if (last != null && now - last >= idleReset.inWholeMilliseconds) {
                consecutive = 0
            }
            consecutive += 1
            lastFailureAtMs = now
            attempt = consecutive
            emit = logThrottle.tryEmit(LOG_KEY)
        }

        emit?.let { decision ->
            val suffix = if (decision.suppressedCount > 0) {
                " (repeated ${decision.suppressedCount}x since last log)"
            } else {
                ""
            }
            warn(error.getErrorMessage() + suffix)
        }
        sleep(backoff.delay(attempt))
    }

    companion object {
        const val LOG_KEY = "telegram.retrieve_updates"
    }
}

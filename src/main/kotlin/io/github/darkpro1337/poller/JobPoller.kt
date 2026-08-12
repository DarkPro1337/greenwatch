package io.github.darkpro1337.poller

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.LinkPreviewOptions
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.darkpro1337.bot.JobFormatter
import io.github.darkpro1337.db.WatchRecord
import io.github.darkpro1337.db.WatchRepository
import io.github.darkpro1337.greenhouse.GreenhouseClient
import io.github.darkpro1337.greenhouse.JobMatcher
import io.github.darkpro1337.greenhouse.dto.GreenhouseJob
import io.github.darkpro1337.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JobPoller(
    private val greenhouseClient: GreenhouseClient,
    private val watchRepository: WatchRepository,
    private val bot: Bot,
    private val scope: CoroutineScope,
    private val interval: Duration,
) {
    private val log = logger()

    fun start() {
        scope.launch {
            delay(5_000.milliseconds)
            while (isActive) {
                runCatching { pollOnce() }
                    .onFailure { log.error("JobPoller error: {}", it.message, it) }

                delay(interval)
            }
        }
    }

    suspend fun pollOnce() {
        val watches = watchRepository.listActiveWatches()
        if (watches.isEmpty()) return

        val byBoard = watches.groupBy { it.boardToken }
        byBoard.forEach { (boardToken, boardWatches) ->
            val jobs = try {
                greenhouseClient.listJobs(boardToken, content = true)
            } catch (e: Exception) {
                log.error("Failed to fetch board {}: {}", boardToken, e.message, e)
                return@forEach
            }

            boardWatches.forEach { watch -> notifyNewJobs(watch, jobs) }
        }
    }

    suspend fun checkWatch(watch: WatchRecord): Int {
        val jobs = greenhouseClient.listJobs(watch.boardToken, content = true)
        val matching = JobMatcher.filter(jobs, watch.toFilters())
        val seen = watchRepository.seenJobIds(watch.id)
        val fresh = matching.filter { it.id !in seen }
        fresh.forEach { job ->
            bot.sendMessage(
                chatId = ChatId.fromId(watch.userId),
                text = JobFormatter.formatNewJob(watch, job),
                parseMode = ParseMode.MARKDOWN,
                linkPreviewOptions = LinkPreviewOptions(isDisabled = true),
            )
        }

        watchRepository.markSeen(watch.id, matching.map { it.id })
        return fresh.size
    }

    private fun notifyNewJobs(watch: WatchRecord, jobs: List<GreenhouseJob>) {
        val matching = JobMatcher.filter(jobs, watch.toFilters())
        val seen = watchRepository.seenJobIds(watch.id)
        val fresh = matching.filter { it.id !in seen }
        if (fresh.isEmpty()) {
            watchRepository.markSeen(watch.id, matching.map { it.id })
            return
        }

        fresh.forEach { job ->
            bot.sendMessage(
                chatId = ChatId.fromId(watch.userId),
                text = JobFormatter.formatNewJob(watch, job),
                parseMode = ParseMode.MARKDOWN,
                linkPreviewOptions = LinkPreviewOptions(isDisabled = true),
            )
        }

        watchRepository.markSeen(watch.id, matching.map { it.id })
    }
}

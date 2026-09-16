package io.github.darkpro1337.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.telegramError
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.darkpro1337.bot.keyboards.Callbacks
import io.github.darkpro1337.bot.keyboards.WatchKeyboards
import io.github.darkpro1337.bot.wizard.WizardSessionStore
import io.github.darkpro1337.db.WatchRepository
import io.github.darkpro1337.greenhouse.GreenhouseClient
import io.github.darkpro1337.greenhouse.JobMatcher
import io.github.darkpro1337.logger
import io.github.darkpro1337.log.LogThrottle
import io.github.darkpro1337.poller.JobPoller
import io.github.darkpro1337.retry.ExponentialBackoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BotFactory(
    private val botToken: String,
    private val greenhouseClient: GreenhouseClient,
    private val watchRepository: WatchRepository,
    private val scope: CoroutineScope,
    private val pollInterval: Duration,
) {
    private val log = logger()
    private val sessions = WizardSessionStore()
    private val wizard = AddWatchWizard(greenhouseClient, watchRepository, sessions, scope)
    private val pollingErrors = TelegramPollingErrorHandler(
        logThrottle = LogThrottle(5.minutes),
        backoff = ExponentialBackoff(initial = 1.seconds, max = 30.seconds),
        warn = { log.warn("Telegram polling error: {}", it) },
        sleep = { Thread.sleep(it.inWholeMilliseconds) },
    )

    fun create(): Pair<com.github.kotlintelegrambot.Bot, JobPoller> {
        lateinit var poller: JobPoller
        val telegramBot = bot {
            token = botToken
            timeout = 30
            logLevel = LogLevel.Error

            dispatch {
                command("start") {
                    watchRepository.ensureUser(message.from?.id ?: message.chat.id)
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = """
                            *Greenwatch* monitors Greenhouse job boards and notifies you about new openings.

                            Commands:
                            /add — create a watch (interactive filters)
                            /list — your watches
                            /remove — delete a watch
                            /check — check watches now
                            /cancel — abort the current wizard
                        """.trimIndent(),
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                command("add") {
                    val userId = message.from?.id ?: message.chat.id
                    watchRepository.ensureUser(userId)
                    wizard.begin(bot, message.chat.id, userId)
                }

                command("cancel") {
                    val userId = message.from?.id ?: message.chat.id
                    sessions.clear(userId)
                    bot.sendMessage(ChatId.fromId(message.chat.id), "Cancelled.")
                }

                command("list") {
                    val userId = message.from?.id ?: message.chat.id
                    val watches = watchRepository.listWatches(userId)
                    if (watches.isEmpty()) {
                        bot.sendMessage(ChatId.fromId(message.chat.id), "No watches yet. Use /add")
                    } else {
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = watches.joinToString("\n\n") { it.summary() },
                            parseMode = ParseMode.MARKDOWN,
                        )
                    }
                }

                command("remove") {
                    val userId = message.from?.id ?: message.chat.id
                    val watches = watchRepository.listWatches(userId)
                    if (watches.isEmpty()) {
                        bot.sendMessage(ChatId.fromId(message.chat.id), "Nothing to remove.")
                    } else {
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = "Pick a watch to remove:",
                            replyMarkup = WatchKeyboards.removeList(watches.map { it.id }),
                        )
                    }
                }

                command("check") {
                    val userId = message.from?.id ?: message.chat.id
                    val watches = watchRepository.listWatches(userId)
                    if (watches.isEmpty()) {
                        bot.sendMessage(ChatId.fromId(message.chat.id), "No watches yet. Use /add")
                        return@command
                    }

                    bot.sendMessage(ChatId.fromId(message.chat.id), "Checking ${watches.size} watch(es)…")
                    scope.launch {
                        watches.forEach { watch ->
                            try {
                                val jobs = greenhouseClient.listJobs(watch.boardToken, content = true)
                                val matching = JobMatcher.filter(jobs, watch.toFilters())
                                bot.sendMessage(
                                    chatId = ChatId.fromId(message.chat.id),
                                    text = JobFormatter.formatCheckResult(watch, matching),
                                )
                                poller.checkWatch(watch)
                            } catch (e: Exception) {
                                bot.sendMessage(
                                    ChatId.fromId(message.chat.id),
                                    "Watch #${watch.id} failed: ${e.message}",
                                )
                            }
                        }
                    }
                }

                callbackQuery {
                    val data = callbackQuery.data
                    val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                    val messageId = callbackQuery.message?.messageId ?: return@callbackQuery
                    val userId = callbackQuery.from.id

                    bot.answerCallbackQuery(callbackQueryId = callbackQuery.id)

                    if (data.startsWith(Callbacks.PREFIX_REMOVE)) {
                        val watchId = data.removePrefix(Callbacks.PREFIX_REMOVE).toLongOrNull()
                        if (watchId != null && watchRepository.deactivateWatch(userId, watchId)) {
                            bot.editMessageText(
                                chatId = ChatId.fromId(chatId),
                                messageId = messageId,
                                text = "Removed watch #$watchId",
                            )
                        } else {
                            bot.sendMessage(ChatId.fromId(chatId), "Could not remove watch.")
                        }
                        return@callbackQuery
                    }

                    wizard.handleCallback(bot, chatId, userId, messageId, data)
                }

                text {
                    if (text.startsWith("/")) return@text
                    val userId = message.from?.id ?: message.chat.id
                    wizard.handleText(bot, message.chat.id, userId, text)
                }

                telegramError {
                    pollingErrors.onError(error)
                }
            }
        }

        poller = JobPoller(
            greenhouseClient = greenhouseClient,
            watchRepository = watchRepository,
            bot = telegramBot,
            scope = scope,
            interval = pollInterval,
        )

        return telegramBot to poller
    }
}

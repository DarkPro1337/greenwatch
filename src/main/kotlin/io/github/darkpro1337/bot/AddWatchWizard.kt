package io.github.darkpro1337.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.darkpro1337.bot.keyboards.Callbacks
import io.github.darkpro1337.bot.keyboards.WatchKeyboards
import io.github.darkpro1337.bot.wizard.WatchDraft
import io.github.darkpro1337.bot.wizard.WizardSessionStore
import io.github.darkpro1337.bot.wizard.WizardStep
import io.github.darkpro1337.db.WatchRecord
import io.github.darkpro1337.db.WatchRepository
import io.github.darkpro1337.greenhouse.BoardNotFoundException
import io.github.darkpro1337.greenhouse.FilterCatalog
import io.github.darkpro1337.greenhouse.GreenhouseClient
import io.github.darkpro1337.greenhouse.JobMatcher
import io.github.darkpro1337.greenhouse.dto.GreenhouseJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AddWatchWizard(
    private val greenhouseClient: GreenhouseClient,
    private val watchRepository: WatchRepository,
    private val sessions: WizardSessionStore,
    private val scope: CoroutineScope,
) {
    fun begin(bot: Bot, chatId: Long, userId: Long) {
        sessions.start(userId)
        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = """
                Send a Greenhouse board token, for example:
                `jetbrains`

                You can also paste a board URL.
                Empty office/category selection means “any”.
            """.trimIndent(),
            parseMode = ParseMode.MARKDOWN,
        )
    }

    fun handleText(bot: Bot, chatId: Long, userId: Long, text: String): Boolean {
        val draft = sessions.get(userId) ?: return false
        return when (draft.step) {
            WizardStep.AwaitingBoardToken -> {
                loadBoard(bot, chatId, userId, text)
                true
            }
            WizardStep.AwaitingName -> {
                finish(bot, chatId, userId, draft, text.trim().ifBlank { draft.defaultName() })
                true
            }
            else -> {
                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "Use the buttons below, or /cancel to abort.",
                )
                true
            }
        }
    }

    fun handleCallback(bot: Bot, chatId: Long, userId: Long, messageId: Long, data: String): Boolean {
        if (data == Callbacks.CANCEL) {
            sessions.clear(userId)
            bot.editMessageText(ChatId.fromId(chatId), messageId, text = "Cancelled.")
            return true
        }

        if (data.startsWith(Callbacks.PREFIX_REMOVE))
            return false

        val draft = sessions.get(userId) ?: return false
        when {
            data.startsWith(Callbacks.PREFIX_OFFICE) -> {
                val officeId = data.removePrefix(Callbacks.PREFIX_OFFICE).toLongOrNull() ?: return true
                val selected = draft.selectedOfficeIds.toMutableSet()
                if (!selected.add(officeId))
                    selected.remove(officeId)

                val updated = draft.copy(selectedOfficeIds = selected).touch()
                sessions.put(updated)
                bot.editMessageReplyMarkup(
                    chatId = ChatId.fromId(chatId),
                    messageId = messageId,
                    replyMarkup = WatchKeyboards.offices(updated),
                )
            }
            data == Callbacks.OFFICES_CLEAR -> {
                val updated = draft.copy(selectedOfficeIds = emptySet()).touch()
                sessions.put(updated)
                bot.editMessageReplyMarkup(
                    chatId = ChatId.fromId(chatId),
                    messageId = messageId,
                    replyMarkup = WatchKeyboards.offices(updated),
                )
            }
            data == Callbacks.OFFICES_DONE -> {
                advanceFromOffices(bot, chatId, messageId, draft)
            }
            data.startsWith(Callbacks.PREFIX_META) -> {
                val index = data.removePrefix(Callbacks.PREFIX_META).toIntOrNull() ?: return true
                toggleMetadata(bot, chatId, messageId, draft, index)
            }
            data == Callbacks.META_SKIP -> {
                skipMetadataField(bot, chatId, messageId, draft)
            }
            data == Callbacks.META_DONE -> {
                advanceMetadata(bot, chatId, messageId, draft)
            }
            else -> return false
        }
        return true
    }

    private fun loadBoard(bot: Bot, chatId: Long, userId: Long, rawToken: String) {
        scope.launch {
            bot.sendMessage(ChatId.fromId(chatId), "Loading board filters…")
            try {
                val token = GreenhouseClient.normalizeToken(rawToken)
                val jobs = greenhouseClient.listJobs(token, content = true)
                val catalog = FilterCatalog.from(offices = emptyList(), jobs = jobs)
                if (catalog.offices.isEmpty() && jobs.isEmpty()) {
                    bot.sendMessage(ChatId.fromId(chatId), "Board `$token` has no published jobs.", parseMode = ParseMode.MARKDOWN)
                    return@launch
                }

                val draft = WatchDraft(
                    userId = userId,
                    step = WizardStep.SelectingOffices,
                    boardToken = token,
                    catalog = catalog,
                    jobs = jobs,
                )
                sessions.put(draft)

                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "Board `$token`\nSelect offices (multi-select). Empty = any office.",
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = WatchKeyboards.offices(draft),
                )
            } catch (e: BoardNotFoundException) {
                bot.sendMessage(ChatId.fromId(chatId), "Board not found: `${e.boardToken}`", parseMode = ParseMode.MARKDOWN)
            } catch (_: IllegalArgumentException) {
                bot.sendMessage(ChatId.fromId(chatId), "Invalid token. Example: `jetbrains`", parseMode = ParseMode.MARKDOWN)
            } catch (e: Exception) {
                bot.sendMessage(ChatId.fromId(chatId), "Failed to load board: ${e.message}")
            }
        }
    }

    private fun advanceFromOffices(bot: Bot, chatId: Long, messageId: Long, draft: WatchDraft) {
        val catalog = draft.catalog ?: return
        if (catalog.metadataFields.isEmpty()) {
            askName(bot, chatId, messageId, draft)
            return
        }

        val updated = draft.copy(
            step = WizardStep.SelectingMetadata,
            metadataFieldIndex = 0,
        ).touch()
        sessions.put(updated)
        showMetadataField(bot, chatId, messageId, updated)
    }

    private fun toggleMetadata(bot: Bot, chatId: Long, messageId: Long, draft: WatchDraft, valueIndex: Int) {
        val field = draft.currentMetadataField() ?: return
        val value = field.values.getOrNull(valueIndex) ?: return
        val byField = draft.selectedMetadata.toMutableMap()
        val current = byField[field.fieldId].orEmpty().toMutableSet()
        if (!current.add(value))
            current.remove(value)

        if (current.isEmpty())
            byField.remove(field.fieldId)
        else
            byField[field.fieldId] = current

        val updated = draft.copy(selectedMetadata = byField).touch()
        sessions.put(updated)

        bot.editMessageReplyMarkup(
            chatId = ChatId.fromId(chatId),
            messageId = messageId,
            replyMarkup = WatchKeyboards.metadata(updated, field),
        )
    }

    private fun skipMetadataField(bot: Bot, chatId: Long, messageId: Long, draft: WatchDraft) {
        val field = draft.currentMetadataField() ?: return
        val byField = draft.selectedMetadata.toMutableMap()
        byField.remove(field.fieldId)
        val cleared = draft.copy(selectedMetadata = byField).touch()
        sessions.put(cleared)
        advanceMetadata(bot, chatId, messageId, cleared)
    }

    private fun advanceMetadata(bot: Bot, chatId: Long, messageId: Long, draft: WatchDraft) {
        val catalog = draft.catalog ?: return
        val nextIndex = draft.metadataFieldIndex + 1
        if (nextIndex >= catalog.metadataFields.size) {
            askName(bot, chatId, messageId, draft)
            return
        }

        val updated = draft.copy(metadataFieldIndex = nextIndex).touch()
        sessions.put(updated)
        showMetadataField(bot, chatId, messageId, updated)
    }

    private fun showMetadataField(bot: Bot, chatId: Long, messageId: Long, draft: WatchDraft) {
        val field = draft.currentMetadataField() ?: return
        val text = "Select *${field.fieldName}* (multi-select). Empty / Skip = any."
        bot.editMessageText(
            chatId = ChatId.fromId(chatId),
            messageId = messageId,
            text = text,
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = WatchKeyboards.metadata(draft, field),
        )
    }

    private fun askName(bot: Bot, chatId: Long, messageId: Long, draft: WatchDraft) {
        val updated = draft.copy(step = WizardStep.AwaitingName).touch()
        sessions.put(updated)
        bot.editMessageText(
            chatId = ChatId.fromId(chatId),
            messageId = messageId,
            text = "Optional: send a name for this watch.\nOr send `-` to use: `${updated.defaultName()}`",
            parseMode = ParseMode.MARKDOWN,
        )
    }

    private fun finish(bot: Bot, chatId: Long, userId: Long, draft: WatchDraft, name: String) {
        val token = draft.boardToken ?: return
        val watchName = if (name == "-") draft.defaultName() else name.take(256)
        scope.launch {
            try {
                val watch = watchRepository.createWatch(
                    userId = userId,
                    boardToken = token,
                    name = watchName,
                    offices = draft.officeFilters(),
                    metadata = draft.metadataFilters(),
                )

                val matching = JobMatcher.filter(draft.jobs, watch.toFilters())
                watchRepository.markSeen(watch.id, matching.map { it.id })
                sessions.clear(userId)
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = buildString {
                        appendLine("Watch saved.")
                        appendLine(watch.summary())
                        appendLine()
                        appendLine("Baseline: ${matching.size} existing job(s) marked as seen.")
                        appendLine("You'll get alerts for new matches only.")
                    },
                    parseMode = ParseMode.MARKDOWN,
                )
            } catch (e: Exception) {
                bot.sendMessage(ChatId.fromId(chatId), "Failed to save watch: ${e.message}")
            }
        }
    }
}

object JobFormatter {
    fun formatNewJob(watch: WatchRecord, job: GreenhouseJob): String {
        val offices = job.offices.joinToString { it.name }.ifBlank { job.location?.name ?: "—" }
        val categories = job.metadata
            .orEmpty()
            .flatMap { meta -> meta.valueAsStrings().map { "${meta.name}: $it" } }
            .joinToString("\n")
            .ifBlank { "—" }

        return buildString {
            appendLine("New job for *${watch.name}* (`${watch.boardToken}`)")
            appendLine()
            appendLine("*${escapeMarkdown(job.title)}*")
            appendLine("Offices: ${escapeMarkdown(offices)}")
            appendLine(escapeMarkdown(categories))
            appendLine()
            append(job.absoluteUrl)
        }
    }

    fun formatCheckResult(watch: WatchRecord, jobs: List<GreenhouseJob>): String {
        if (jobs.isEmpty()) {
            return "No matching jobs right now for watch #${watch.id}."
        }

        return buildString {
            appendLine("Matching jobs for #${watch.id} (${jobs.size}):")
            jobs.take(15).forEach { job ->
                appendLine("• ${job.title}")
                appendLine("  ${job.absoluteUrl}")
            }

            if (jobs.size > 15)
                appendLine("…and ${jobs.size - 15} more")
        }
    }

    private fun escapeMarkdown(text: String): String =
        text.replace("*", "\\*").replace("_", "\\_").replace("`", "\\`")
}

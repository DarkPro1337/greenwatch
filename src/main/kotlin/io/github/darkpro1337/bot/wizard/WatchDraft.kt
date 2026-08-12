package io.github.darkpro1337.bot.wizard

import io.github.darkpro1337.db.MetadataFilterRecord
import io.github.darkpro1337.db.OfficeFilterRecord
import io.github.darkpro1337.greenhouse.FilterCatalog
import io.github.darkpro1337.greenhouse.dto.GreenhouseJob
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class WizardStep {
    AwaitingBoardToken,
    SelectingOffices,
    SelectingMetadata,
    AwaitingName,
}

data class WatchDraft(
    val userId: Long,
    val step: WizardStep = WizardStep.AwaitingBoardToken,
    val boardToken: String? = null,
    val catalog: FilterCatalog? = null,
    val jobs: List<GreenhouseJob> = emptyList(),
    val selectedOfficeIds: Set<Long> = emptySet(),
    val selectedMetadata: Map<Long, Set<String>> = emptyMap(),
    val metadataFieldIndex: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun touch(): WatchDraft = copy(updatedAtMillis = System.currentTimeMillis())

    fun currentMetadataField() = catalog?.metadataFields?.getOrNull(metadataFieldIndex)

    fun officeFilters(): List<OfficeFilterRecord> {
        val offices = catalog?.offices.orEmpty()
        return selectedOfficeIds.mapNotNull { id ->
            offices.find { it.id == id }?.let { OfficeFilterRecord(it.id, it.name) }
        }
    }

    fun metadataFilters(): List<MetadataFilterRecord> {
        val fields = catalog?.metadataFields.orEmpty().associateBy { it.fieldId }
        return selectedMetadata.flatMap { (fieldId, values) ->
            val fieldName = fields[fieldId]?.fieldName ?: return@flatMap emptyList()
            values.map { MetadataFilterRecord(fieldId, fieldName, it) }
        }
    }

    fun defaultName(): String {
        val token = boardToken ?: "board"
        val offices = officeFilters().joinToString(",") { it.officeName }.ifBlank { "all" }
        return "$token · $offices"
    }
}

class WizardSessionStore(private val ttl: Duration = 30.minutes) {
    private val drafts = ConcurrentHashMap<Long, WatchDraft>()

    fun get(userId: Long): WatchDraft? {
        purgeExpired()
        return drafts[userId]
    }

    fun put(draft: WatchDraft) {
        drafts[draft.userId] = draft.touch()
    }

    fun clear(userId: Long) {
        drafts.remove(userId)
    }

    fun start(userId: Long): WatchDraft {
        val draft = WatchDraft(userId = userId)
        put(draft)
        return draft
    }

    private fun purgeExpired() {
        val cutoff = System.currentTimeMillis() - ttl.inWholeMilliseconds
        drafts.entries.removeIf { it.value.updatedAtMillis < cutoff }
    }
}

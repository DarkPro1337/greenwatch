package io.github.darkpro1337.db

import io.github.darkpro1337.db.tables.*
import io.github.darkpro1337.greenhouse.WatchFilters
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

data class OfficeFilterRecord(
    val officeId: Long,
    val officeName: String,
)

data class MetadataFilterRecord(
    val fieldId: Long,
    val fieldName: String,
    val value: String,
)

data class WatchRecord(
    val id: Long,
    val userId: Long,
    val boardToken: String,
    val name: String,
    val createdAt: Long,
    val active: Boolean,
    val offices: List<OfficeFilterRecord>,
    val metadata: List<MetadataFilterRecord>,
) {
    fun toFilters(): WatchFilters = WatchFilters(
        officeIds = offices.map { it.officeId }.toSet(),
        metadataByField = metadata
            .groupBy({ it.fieldId }, { it.value })
            .mapValues { (_, values) -> values.toSet() },
    )

    fun summary(): String {
        val officePart = if (offices.isEmpty()) {
            "any office"
        } else {
            offices.joinToString { it.officeName }
        }
        val metaPart = if (metadata.isEmpty()) {
            "any category"
        } else {
            metadata.groupBy { it.fieldName }
                .entries
                .joinToString("; ") { (field, values) ->
                    "$field: ${values.joinToString { it.value }}"
                }
        }

        return "#$id · $name · `$boardToken`\nOffices: $officePart\n$metaPart"
    }
}

class WatchRepository {
    fun ensureUser(telegramId: Long) {
        transaction {
            val exists = UsersTable.selectAll()
                .where { UsersTable.telegramId eq telegramId }
                .count() > 0
            if (!exists) {
                UsersTable.insert {
                    it[UsersTable.telegramId] = telegramId
                    it[createdAt] = System.currentTimeMillis()
                }
            }
        }
    }

    fun createWatch(
        userId: Long,
        boardToken: String,
        name: String,
        offices: List<OfficeFilterRecord>,
        metadata: List<MetadataFilterRecord>,
    ): WatchRecord {
        ensureUser(userId)
        return transaction {
            val watchId = WatchesTable.insert {
                it[WatchesTable.userId] = userId
                it[WatchesTable.boardToken] = boardToken
                it[WatchesTable.name] = name
                it[createdAt] = System.currentTimeMillis()
                it[active] = true
            } get WatchesTable.id

            offices.forEach { office ->
                WatchOfficeFiltersTable.insert {
                    it[WatchOfficeFiltersTable.watchId] = watchId
                    it[officeId] = office.officeId
                    it[officeName] = office.officeName
                }
            }

            metadata.forEach { meta ->
                WatchMetadataFiltersTable.insert {
                    it[WatchMetadataFiltersTable.watchId] = watchId
                    it[fieldId] = meta.fieldId
                    it[fieldName] = meta.fieldName
                    it[value] = meta.value
                }
            }

            loadWatch(watchId)!!
        }
    }

    fun listWatches(userId: Long): List<WatchRecord> = transaction {
        WatchesTable.selectAll()
            .where { (WatchesTable.userId eq userId) and (WatchesTable.active eq true) }
            .orderBy(WatchesTable.id)
            .mapNotNull { loadWatch(it[WatchesTable.id]) }
    }

    fun listActiveWatches(): List<WatchRecord> = transaction {
        WatchesTable.selectAll()
            .where { WatchesTable.active eq true }
            .mapNotNull { loadWatch(it[WatchesTable.id]) }
    }

    fun deactivateWatch(userId: Long, watchId: Long): Boolean = transaction {
        val updated = WatchesTable.update({
            (WatchesTable.id eq watchId) and (WatchesTable.userId eq userId) and (WatchesTable.active eq true)
        }) {
            it[active] = false
        }
        updated > 0
    }

    fun markSeen(watchId: Long, jobIds: Collection<Long>) {
        if (jobIds.isEmpty()) return
        val now = System.currentTimeMillis()
        transaction {
            jobIds.forEach { id ->
                SeenJobsTable.insertIgnore {
                    it[SeenJobsTable.watchId] = watchId
                    it[jobId] = id
                    it[seenAt] = now
                }
            }
        }
    }

    fun seenJobIds(watchId: Long): Set<Long> = transaction {
        SeenJobsTable.selectAll()
            .where { SeenJobsTable.watchId eq watchId }
            .map { it[SeenJobsTable.jobId] }
            .toSet()
    }

    private fun loadWatch(watchId: Long): WatchRecord? {
        val row = WatchesTable.selectAll()
            .where { WatchesTable.id eq watchId }
            .singleOrNull() ?: return null

        val offices = WatchOfficeFiltersTable.selectAll()
            .where { WatchOfficeFiltersTable.watchId eq watchId }
            .map {
                OfficeFilterRecord(
                    officeId = it[WatchOfficeFiltersTable.officeId],
                    officeName = it[WatchOfficeFiltersTable.officeName],
                )
            }

        val metadata = WatchMetadataFiltersTable.selectAll()
            .where { WatchMetadataFiltersTable.watchId eq watchId }
            .map {
                MetadataFilterRecord(
                    fieldId = it[WatchMetadataFiltersTable.fieldId],
                    fieldName = it[WatchMetadataFiltersTable.fieldName],
                    value = it[WatchMetadataFiltersTable.value],
                )
            }

        return WatchRecord(
            id = row[WatchesTable.id],
            userId = row[WatchesTable.userId],
            boardToken = row[WatchesTable.boardToken],
            name = row[WatchesTable.name],
            createdAt = row[WatchesTable.createdAt],
            active = row[WatchesTable.active],
            offices = offices,
            metadata = metadata,
        )
    }
}

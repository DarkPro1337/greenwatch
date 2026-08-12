package io.github.darkpro1337.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val telegramId = long("telegram_id")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(telegramId)
}

object WatchesTable : Table("watches") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.telegramId, onDelete = ReferenceOption.CASCADE)
    val boardToken = varchar("board_token", 128)
    val name = varchar("name", 256)
    val createdAt = long("created_at")
    val active = bool("active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object WatchOfficeFiltersTable : Table("watch_office_filters") {
    val watchId = long("watch_id").references(WatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val officeId = long("office_id")
    val officeName = varchar("office_name", 256)
    override val primaryKey = PrimaryKey(watchId, officeId)
}

object WatchMetadataFiltersTable : Table("watch_metadata_filters") {
    val watchId = long("watch_id").references(WatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val fieldId = long("field_id")
    val fieldName = varchar("field_name", 256)
    val value = varchar("value", 512)
    override val primaryKey = PrimaryKey(watchId, fieldId, value)
}

object SeenJobsTable : Table("seen_jobs") {
    val watchId = long("watch_id").references(WatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val jobId = long("job_id")
    val seenAt = long("seen_at")
    override val primaryKey = PrimaryKey(watchId, jobId)
}

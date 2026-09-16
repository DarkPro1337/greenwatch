package io.github.darkpro1337.db

import io.github.darkpro1337.db.tables.SeenJobsTable
import io.github.darkpro1337.db.tables.UsersTable
import io.github.darkpro1337.db.tables.WatchMetadataFiltersTable
import io.github.darkpro1337.db.tables.WatchOfficeFiltersTable
import io.github.darkpro1337.db.tables.WatchesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.Connection

object DatabaseFactory {
    fun init(databasePath: String) {
        val url = when {
            databasePath.startsWith("jdbc:") -> databasePath
            else -> {
                ensureWritableDatabaseFile(databasePath)
                "jdbc:sqlite:$databasePath?foreign_keys=on&journal_mode=WAL"
            }
        }

        Database.connect(url, driver = "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            SchemaUtils.create(
                UsersTable,
                WatchesTable,
                WatchOfficeFiltersTable,
                WatchMetadataFiltersTable,
                SeenJobsTable,
            )
        }
    }

    internal fun ensureWritableDatabaseFile(databasePath: String) {
        val file = File(databasePath)
        val dir = file.parentFile ?: File(".")
        if (!dir.exists() && !dir.mkdirs()) {
            error("Cannot create database directory: ${dir.absolutePath}")
        }
        if (!dir.canWrite()) {
            error(
                "Database directory is not writable: ${dir.absolutePath}. " +
                    "The process uid must be able to create ${file.name} (Docker: chown 1000:1000 the volume).",
            )
        }
    }
}

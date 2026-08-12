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
import java.sql.Connection

object DatabaseFactory {
    fun init(databasePath: String) {
        val url = when {
            databasePath.startsWith("jdbc:") -> databasePath
            else -> "jdbc:sqlite:$databasePath?foreign_keys=on&journal_mode=WAL"
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
}

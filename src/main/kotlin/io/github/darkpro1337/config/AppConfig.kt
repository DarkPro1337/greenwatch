package io.github.darkpro1337.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class AppConfig(
    val botToken: String,
    val databasePath: String = "greenwatch.db",
    val pollInterval: Duration = 10.minutes,
) {
    companion object {
        fun fromEnv(): AppConfig {
            val token = System.getenv("BOT_TOKEN")?.trim().orEmpty()
            require(token.isNotEmpty()) { "Set BOT_TOKEN environment variable (from @BotFather)" }

            val dbPath = System.getenv("DATABASE_PATH")?.trim().orEmpty().ifEmpty { "greenwatch.db" }
            val minutes = System.getenv("POLL_INTERVAL_MINUTES")?.toLongOrNull() ?: 10L
            require(minutes > 0) { "POLL_INTERVAL_MINUTES must be > 0" }

            return AppConfig(
                botToken = token,
                databasePath = dbPath,
                pollInterval = minutes.minutes,
            )
        }
    }
}

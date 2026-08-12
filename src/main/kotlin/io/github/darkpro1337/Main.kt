package io.github.darkpro1337

import io.github.darkpro1337.bot.BotFactory
import io.github.darkpro1337.config.AppConfig
import io.github.darkpro1337.db.DatabaseFactory
import io.github.darkpro1337.db.WatchRepository
import io.github.darkpro1337.greenhouse.GreenhouseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() {
    val log = AppMain.logger()
    val config = AppConfig.fromEnv()
    DatabaseFactory.init(config.databasePath)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val greenhouseClient = GreenhouseClient()
    val watchRepository = WatchRepository()

    val (bot, poller) = BotFactory(
        botToken = config.botToken,
        greenhouseClient = greenhouseClient,
        watchRepository = watchRepository,
        scope = scope,
        pollInterval = config.pollInterval,
    ).create()

    log.info("Greenwatch started. Poll interval: {}", config.pollInterval)
    poller.start()
    bot.startPolling()
}

/** Marker for the application entry-point logger. */
private object AppMain

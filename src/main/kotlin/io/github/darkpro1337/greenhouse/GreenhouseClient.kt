package io.github.darkpro1337.greenhouse

import io.github.darkpro1337.greenhouse.dto.GreenhouseJob
import io.github.darkpro1337.greenhouse.dto.JobsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class GreenhouseClient(
    private val httpClient: HttpClient = defaultClient(),
    private val baseUrl: String = "https://boards-api.greenhouse.io/v1/boards",
) : AutoCloseable {

    suspend fun listJobs(boardToken: String, content: Boolean = true): List<GreenhouseJob> {
        val token = normalizeToken(boardToken)
        val url = buildString {
            append("$baseUrl/$token/jobs")
            if (content) append("?content=true")
        }

        val response = httpClient.get(url)
        if (response.status == HttpStatusCode.NotFound)
            throw BoardNotFoundException(token)

        if (response.status.value >= 400)
            throw GreenhouseApiException("Failed to load jobs for '$token': HTTP ${response.status}")

        return response.body<JobsResponse>().jobs
    }

    override fun close() = httpClient.close()

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }

        fun normalizeToken(raw: String): String {
            val trimmed = raw.trim()
            val fromUrl = Regex(
                """(?:job-boards(?:\.eu)?|boards)\.greenhouse\.io/([^/?#]+)""",
                RegexOption.IGNORE_CASE,
            ).find(trimmed)?.groupValues?.get(1)

            return (fromUrl ?: trimmed)
                .trim('/')
                .lowercase()
                .also { require(it.matches(Regex("""[a-z0-9_-]+"""))) { "Invalid board token: $raw" } }
        }
    }
}

class BoardNotFoundException(val boardToken: String) : RuntimeException("Greenhouse board not found: $boardToken")

class GreenhouseApiException(message: String) : RuntimeException(message)

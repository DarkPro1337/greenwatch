package io.github.darkpro1337.greenhouse

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GreenhouseClientRetryTest {
    @Test
    fun `retries 502 then returns jobs`() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installGreenhouseRetries()
            engine {
                addHandler {
                    attempts++
                    if (attempts < 2) respond("bad gateway", HttpStatusCode.BadGateway)
                    else jsonJobs()
                }
            }
        }

        client.use {
            val jobs = GreenhouseClient(it, baseUrl = "https://example.test/v1/boards")
                .listJobs("jetbrains", content = false)
            assertEquals(emptyList(), jobs)
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `retries connect timeout then returns jobs`() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installGreenhouseRetries()
            engine {
                addHandler {
                    attempts++
                    if (attempts < 2) throw ConnectException("Connect timeout has expired")
                    jsonJobs()
                }
            }
        }

        client.use {
            val jobs = GreenhouseClient(it, baseUrl = "https://example.test/v1/boards")
                .listJobs("jetbrains", content = false)
            assertEquals(emptyList(), jobs)
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `does not retry missing boards`() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installGreenhouseRetries()
            engine {
                addHandler {
                    attempts++
                    respond("not found", HttpStatusCode.NotFound)
                }
            }
        }

        client.use {
            assertFailsWith<BoardNotFoundException> {
                GreenhouseClient(it, baseUrl = "https://example.test/v1/boards")
                    .listJobs("missing-board", content = false)
            }
        }
        assertEquals(1, attempts)
    }
}

private fun MockRequestHandleScope.jsonJobs() = respond(
    content = """{"jobs":[]}""",
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

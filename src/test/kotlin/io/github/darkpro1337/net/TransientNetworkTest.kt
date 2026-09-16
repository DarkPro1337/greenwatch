package io.github.darkpro1337.net

import io.ktor.client.network.sockets.ConnectTimeoutException
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransientNetworkTest {
    @Test
    fun `retries typed network failures including nested causes`() {
        assertTrue(isTransientNetworkFailure(ConnectTimeoutException("Connect timeout has expired")))
        assertTrue(isTransientNetworkFailure(ConnectException("Network is unreachable")))
        assertTrue(isTransientNetworkFailure(UnresolvedAddressException()))
        assertTrue(isTransientNetworkFailure(RuntimeException("wrap", ConnectException("Connection refused"))))
    }

    @Test
    fun `does not classify by exception message`() {
        assertFalse(
            isTransientNetworkFailure(
                RuntimeException("Connect timeout has expired [url=https://example.test, connect_timeout=15000 ms]"),
            ),
        )
        assertFalse(isTransientNetworkFailure(IllegalArgumentException("Invalid board token")))
    }

    @Test
    fun `retries server and rate-limit statuses only`() {
        assertTrue(isRetryableHttpStatus(429))
        assertTrue(isRetryableHttpStatus(502))
        assertFalse(isRetryableHttpStatus(404))
        assertFalse(isRetryableHttpStatus(200))
    }
}

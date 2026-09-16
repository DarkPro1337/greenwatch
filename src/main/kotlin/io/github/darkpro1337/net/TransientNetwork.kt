package io.github.darkpro1337.net

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

internal fun isTransientNetworkFailure(cause: Throwable): Boolean {
    var current: Throwable? = cause
    while (current != null) {
        when (current) {
            is HttpRequestTimeoutException,
            is UnresolvedAddressException,
            is IOException,
            -> return true
        }
        current = current.cause
    }
    return false
}

internal fun isRetryableHttpStatus(code: Int): Boolean = code == 429 || code in 500..599

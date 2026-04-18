package com.rafambn.wgkotlin.daemon.client

import java.time.Duration

sealed class DaemonClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class Timeout(
        val timeout: Duration,
        cause: Throwable,
    ) : DaemonClientException(
        message = "Timed out waiting for daemon response after ${timeout.toMillis()}ms",
        cause = cause,
    )

    class ProtocolViolation(
        message: String,
    ) : DaemonClientException(message = message)
}

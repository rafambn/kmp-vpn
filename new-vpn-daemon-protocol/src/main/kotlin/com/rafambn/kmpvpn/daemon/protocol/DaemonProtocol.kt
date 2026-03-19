package com.rafambn.kmpvpn.daemon.protocol

/**
 * Phase 01 scaffold for typed daemon IPC models.
 */
public data class DaemonRequest(
    public val requestId: String,
    public val command: DaemonCommand,
)

public sealed interface DaemonCommand {
    public data object HealthCheck : DaemonCommand
}

public sealed interface DaemonResponse {
    public val requestId: String

    public data class Success(
        override val requestId: String,
        public val payload: String,
    ) : DaemonResponse

    public data class Failure(
        override val requestId: String,
        public val message: String,
    ) : DaemonResponse
}

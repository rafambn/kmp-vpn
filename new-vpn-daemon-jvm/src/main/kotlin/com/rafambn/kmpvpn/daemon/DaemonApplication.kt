package com.rafambn.kmpvpn.daemon

import com.rafambn.kmpvpn.daemon.protocol.DaemonCommand
import com.rafambn.kmpvpn.daemon.protocol.DaemonRequest
import com.rafambn.kmpvpn.daemon.protocol.DaemonResponse

/**
 * Phase 01 daemon scaffold with a single typed command path.
 */
public class DaemonApplication {

    public fun handle(request: DaemonRequest): DaemonResponse {
        return when (request.command) {
            DaemonCommand.HealthCheck -> DaemonResponse.Success(
                requestId = request.requestId,
                payload = "ok"
            )
        }
    }
}

public fun main() {
    val response = DaemonApplication().handle(
        DaemonRequest(
            requestId = "bootstrap",
            command = DaemonCommand.HealthCheck
        )
    )

    println(response)
}

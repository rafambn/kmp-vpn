package com.rafambn.kmpvpn.daemon.client

import com.rafambn.kmpvpn.daemon.protocol.DaemonRequest
import com.rafambn.kmpvpn.daemon.protocol.DaemonResponse

/**
 * Minimal client contract for daemon request forwarding.
 */
public interface DaemonClient {
    public fun send(request: DaemonRequest): DaemonResponse
}

public class InMemoryDaemonClient(
    private val requestHandler: (DaemonRequest) -> DaemonResponse
) : DaemonClient {
    override fun send(request: DaemonRequest): DaemonResponse {
        return requestHandler(request)
    }
}

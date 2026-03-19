package com.rafambn.kmpvpn.daemon.client

import com.rafambn.kmpvpn.daemon.protocol.DaemonCommand
import com.rafambn.kmpvpn.daemon.protocol.DaemonRequest
import com.rafambn.kmpvpn.daemon.protocol.DaemonResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class DaemonClientSmokeTest {

    @Test
    fun inMemoryClientForwardsRequestToHandler() {
        val client = InMemoryDaemonClient { request ->
            DaemonResponse.Success(requestId = request.requestId, payload = "forwarded")
        }

        val response = client.send(
            DaemonRequest(
                requestId = "client-1",
                command = DaemonCommand.HealthCheck
            )
        ) as DaemonResponse.Success

        assertEquals("client-1", response.requestId)
        assertEquals("forwarded", response.payload)
    }
}

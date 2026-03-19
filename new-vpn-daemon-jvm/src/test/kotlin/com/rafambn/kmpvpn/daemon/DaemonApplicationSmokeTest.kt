package com.rafambn.kmpvpn.daemon

import com.rafambn.kmpvpn.daemon.protocol.DaemonCommand
import com.rafambn.kmpvpn.daemon.protocol.DaemonRequest
import com.rafambn.kmpvpn.daemon.protocol.DaemonResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class DaemonApplicationSmokeTest {

    @Test
    fun healthCheckReturnsSuccessResponse() {
        val response = DaemonApplication().handle(
            DaemonRequest(
                requestId = "health-1",
                command = DaemonCommand.HealthCheck
            )
        )

        val success = response as DaemonResponse.Success
        assertEquals("health-1", success.requestId)
        assertEquals("ok", success.payload)
    }
}

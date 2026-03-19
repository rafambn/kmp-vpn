package com.rafambn.kmpvpn.daemon.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaemonProtocolSmokeTest {

    @Test
    fun requestUsesTypedCommandModel() {
        val request = DaemonRequest(
            requestId = "req-1",
            command = DaemonCommand.HealthCheck
        )

        assertEquals("req-1", request.requestId)
        assertTrue(request.command is DaemonCommand.HealthCheck)
    }
}

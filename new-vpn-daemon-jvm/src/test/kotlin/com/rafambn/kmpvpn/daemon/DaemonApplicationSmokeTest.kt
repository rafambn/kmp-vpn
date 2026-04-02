package com.rafambn.kmpvpn.daemon

import com.rafambn.kmpvpn.daemon.protocol.DaemonCommandResult
import com.rafambn.kmpvpn.daemon.protocol.DaemonErrorKind
import com.rafambn.kmpvpn.daemon.protocol.response.PingResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaemonProcessApiSmokeTest {

    @Test
    fun pingReturnsSuccess() = runBlocking {
        val response = DaemonProcessApiImpl().ping()

        assertTrue(response.isSuccess)
        assertEquals(PingResponse, (response as DaemonCommandResult.Success).data)
    }

    @Test
    fun knownButUnimplementedCommandReturnsPredictableFailure() = runBlocking {
        val response = DaemonProcessApiImpl().createInterface(
            interfaceName = "wg0",
        )
        val failure = response as DaemonCommandResult.Failure

        assertEquals(DaemonErrorKind.UNKNOWN_COMMAND, failure.kind)
        assertFalse(failure.message.isBlank())
    }
}

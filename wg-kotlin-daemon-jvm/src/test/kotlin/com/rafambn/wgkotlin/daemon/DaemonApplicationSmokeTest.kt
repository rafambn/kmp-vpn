package com.rafambn.wgkotlin.daemon

import com.rafambn.wgkotlin.daemon.command.CommandBinary
import com.rafambn.wgkotlin.daemon.planner.PlatformAdapter
import com.rafambn.wgkotlin.daemon.protocol.CommandResult
import com.rafambn.wgkotlin.daemon.protocol.TunSessionConfig
import com.rafambn.wgkotlin.daemon.protocol.response.PingResponse
import com.rafambn.wgkotlin.daemon.tun.TunHandle
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaemonApplicationSmokeTest {

    @Test
    fun pingReturnsSuccess() = runBlocking {
        val api = DaemonProcessApiImpl(adapter = RecordingAdapter())
        assertTrue(api.ping().isSuccess)
        assertEquals(PingResponse, (api.ping() as CommandResult.Success<PingResponse>).data)
    }

    @Test
    fun startSessionStreamsPacketsAndClosesHandle() = runBlocking {
        val adapter = RecordingAdapter()
        val api = DaemonProcessApiImpl(adapter = adapter)

        val packet = api.startSession(
            config = TunSessionConfig(interfaceName = "wg0", addresses = listOf("10.0.0.1/24")),
            outgoingPackets = emptyFlow(),
        ).first()

        assertEquals("1, 2, 3", packet.joinToString())
        assertEquals(1, adapter.startCalls)
        assertEquals(1, adapter.handle.closeCalls)
    }

    private class RecordingAdapter : PlatformAdapter {
        val handle = RecordingHandle()
        var startCalls: Int = 0
        override val platformId: String = "test"
        override val requiredBinaries: Set<CommandBinary> = emptySet()

        override suspend fun startSession(config: TunSessionConfig): TunHandle {
            startCalls++
            return handle
        }
    }

    private class RecordingHandle : TunHandle {
        override val interfaceName: String = "wg0"
        var closeCalls: Int = 0
        private var emitted = false

        override suspend fun readPacket(): ByteArray? {
            return if (emitted) { kotlinx.coroutines.delay(10); null } else byteArrayOf(1, 2, 3).also { emitted = true }
        }

        override suspend fun writePacket(packet: ByteArray) {}

        override fun close() {
            closeCalls++
        }
    }
}

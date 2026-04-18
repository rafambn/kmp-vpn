package com.rafambn.wgkotlin.daemon

import com.rafambn.wgkotlin.daemon.command.CommandBinary
import com.rafambn.wgkotlin.daemon.planner.PlatformAdapter
import com.rafambn.wgkotlin.daemon.protocol.TunSessionConfig
import com.rafambn.wgkotlin.daemon.tun.TunHandle
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DaemonClientIntegrationTest {

    @Test
    fun daemonApiUsesInjectedAdapterForSessionStart() = runBlocking {
        val adapter = object : PlatformAdapter {
            override val platformId: String = "test"
            override val requiredBinaries: Set<CommandBinary> = emptySet()
            override suspend fun startSession(config: TunSessionConfig): TunHandle {
                return object : TunHandle {
                    override val interfaceName: String = config.interfaceName
                    private var emitted = false
                    override suspend fun readPacket(): ByteArray? = if (emitted) { kotlinx.coroutines.delay(10); null } else byteArrayOf(7, 8, 9).also { emitted = true }
                    override suspend fun writePacket(packet: ByteArray) {}
                    override fun close() {}
                }
            }
        }
        val api = DaemonProcessApiImpl(adapter = adapter)

        val packet = api.startSession(
            config = TunSessionConfig(interfaceName = "wg0", addresses = listOf("10.0.0.1/24")),
            outgoingPackets = emptyFlow(),
        ).first()

        assertEquals("7, 8, 9", packet.joinToString())
    }
}

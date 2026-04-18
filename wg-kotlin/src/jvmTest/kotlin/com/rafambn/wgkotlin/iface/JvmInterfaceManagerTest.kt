package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.VpnPeer
import com.rafambn.wgkotlin.daemon.protocol.CommandResult
import com.rafambn.wgkotlin.daemon.protocol.DAEMON_RPC_PATH
import com.rafambn.wgkotlin.daemon.protocol.DaemonErrorKind
import com.rafambn.wgkotlin.daemon.protocol.DaemonProcessApi
import com.rafambn.wgkotlin.daemon.protocol.response.ApplyAddressesResponse
import com.rafambn.wgkotlin.daemon.protocol.response.ApplyDnsResponse
import com.rafambn.wgkotlin.daemon.protocol.response.ApplyMtuResponse
import com.rafambn.wgkotlin.daemon.protocol.response.ApplyRoutesResponse
import com.rafambn.wgkotlin.daemon.protocol.response.CreateInterfaceResponse
import com.rafambn.wgkotlin.daemon.protocol.response.DeleteInterfaceResponse
import com.rafambn.wgkotlin.daemon.protocol.response.InterfaceExistsResponse
import com.rafambn.wgkotlin.daemon.protocol.response.PingResponse
import com.rafambn.wgkotlin.daemon.protocol.response.ReadInterfaceInformationResponse
import com.rafambn.wgkotlin.session.DuplexChannelPipe
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.net.ServerSocket
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.protobuf.protobuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class JvmInterfaceManagerTest {

    @Test
    fun createAndDeleteRemainIdempotent() {
        val delegate = InMemoryInterfaceCommandExecutor()
        val executor = CountingExecutor(delegate)
        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val interfaceManager = JvmInterfaceManager(
            interfaceName = "utun150",
            commandExecutor = executor,
            tunPipe = tunPipe.first,
        )
        val config = configuration(
            interfaceName = "utun150",
            dnsDomainPool = (listOf("corp.local") to listOf("1.1.1.1")),
            addresses = listOf("10.0.0.1/32"),
            peers = listOf(VpnPeer(publicKey = "peer-a", allowedIps = listOf("0.0.0.0/0"))),
        )

        interfaceManager.create(config)
        interfaceManager.create(config)
        interfaceManager.up()
        interfaceManager.up()
        interfaceManager.down()
        interfaceManager.down()
        interfaceManager.delete()
        interfaceManager.delete()

        assertEquals(1, executor.openBridgeCalls)
        assertEquals(1, executor.closedBridgeCount)
        assertFalse(interfaceManager.exists())
    }

    @Test
    fun reconfigureRollsBackOnDnsApplyFailure() {
        val delegate = InMemoryInterfaceCommandExecutor()
        val executor = FailureInjectingExecutor(delegate)
        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val interfaceManager = JvmInterfaceManager(
            interfaceName = "utun151",
            commandExecutor = executor,
            tunPipe = tunPipe.first,
        )

        val baseConfiguration = configuration(
            interfaceName = "utun151",
            dnsDomainPool = (listOf("corp.local") to listOf("1.1.1.1")),
            addresses = listOf("10.10.0.1/32"),
            peers = listOf(VpnPeer(publicKey = "peer-a", allowedIps = listOf("10.200.0.0/24"))),
        )
        val updatedConfiguration = configuration(
            interfaceName = "utun151",
            dnsDomainPool = (listOf("corp.local") to listOf("9.9.9.9")),
            addresses = listOf("10.10.0.2/32"),
            peers = listOf(VpnPeer(publicKey = "peer-b", allowedIps = listOf("10.201.0.0/24"))),
        )

        interfaceManager.create(baseConfiguration)

        executor.failOnDns = true
        assertFailsWith<IllegalStateException> {
            interfaceManager.reconfigure(updatedConfiguration)
        }

        val current = interfaceManager.configuration()
        assertEquals(baseConfiguration.dnsDomainPool, current.dnsDomainPool)
        assertEquals(baseConfiguration.addresses, current.addresses)
        assertEquals(baseConfiguration.peers, current.peers)

        val info = assertNotNull(interfaceManager.readInformation())
        assertEquals(baseConfiguration.dnsDomainPool, info.dnsDomainPool)
        assertEquals(baseConfiguration.addresses, info.addresses)

        assertTrue(
            executor.appliedDnsCalls.any { call ->
                call == ("utun151" to (listOf("corp.local") to listOf("9.9.9.9")))
            },
        )
        assertEquals(
            "utun151" to (listOf("corp.local") to listOf("1.1.1.1")),
            executor.appliedDnsCalls.last(),
        )
    }

    @Test
    fun createRollsBackInterfaceOnApplyFailure() {
        val delegate = InMemoryInterfaceCommandExecutor()
        val executor = FailureInjectingExecutor(delegate)
        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val interfaceManager = JvmInterfaceManager(
            interfaceName = "utun153",
            commandExecutor = executor,
            tunPipe = tunPipe.first,
        )
        val config = configuration(
            interfaceName = "utun153",
            dnsDomainPool = (listOf("corp.local") to listOf("8.8.8.8")),
            addresses = listOf("10.30.0.1/32"),
        )

        executor.failOnDns = true

        assertFailsWith<IllegalStateException> {
            interfaceManager.create(config)
        }

        assertEquals(1, executor.deleteCalls)
        assertFalse(interfaceManager.exists())
        assertFailsWith<IllegalStateException> {
            interfaceManager.configuration()
        }
    }

    @Test
    fun isUpReflectsExternalInterfaceStateChanges() {
        val delegate = InMemoryInterfaceCommandExecutor()
        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val interfaceManager = JvmInterfaceManager(
            interfaceName = "utun154",
            commandExecutor = delegate,
            tunPipe = tunPipe.first,
        )
        val config = configuration(interfaceName = "utun154")

        interfaceManager.create(config)
        interfaceManager.up()
        assertTrue(interfaceManager.isUp())

        // Simulate external state change outside this manager instance.
        delegate.deleteInterface("utun154")

        assertFalse(interfaceManager.isUp())
    }

    @Test
    fun readInformationIncludesExecutorPeerStats() {
        val executor = InMemoryInterfaceCommandExecutor()
        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val interfaceManager = JvmInterfaceManager(
            interfaceName = "utun152",
            commandExecutor = executor,
            tunPipe = tunPipe.first,
        )
        val config = configuration(
            interfaceName = "utun152",
            peers = listOf(
                VpnPeer(publicKey = "peer-a"),
                VpnPeer(publicKey = "peer-b"),
            ),
        )

        interfaceManager.create(config)
        executor.setPeerStats(
            interfaceName = "utun152",
            peerStats = listOf(
                VpnPeerStats(
                    publicKey = "peer-a",
                    receivedBytes = 1024L,
                    transmittedBytes = 2048L,
                    lastHandshakeEpochSeconds = 1_700_000_000L,
                ),
            ),
        )

        val information = assertNotNull(interfaceManager.readInformation())

        assertEquals(1, information.peerStats.size)
        assertEquals("peer-a", information.peerStats.single().publicKey)
        assertEquals(1024L, information.peerStats.single().receivedBytes)
    }

    @Test
    fun asyncBridgeFailureClosesBridgeEvenWhenFailureHandlerCallsDown() {
        val executor = BridgeFailureExecutor()
        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val interfaceManager = JvmInterfaceManager(
            interfaceName = "utun155",
            commandExecutor = executor,
            tunPipe = tunPipe.first,
        )
        interfaceManager.create(configuration(interfaceName = "utun155"))

        var callbackCount = 0
        interfaceManager.up {
            callbackCount++
            interfaceManager.down()
        }

        executor.failBridge(IllegalStateException("daemon disconnected"))

        assertEquals(1, callbackCount)
        assertEquals(1, executor.closedBridgeCount)
    }

    private fun configuration(
        interfaceName: String,
        dnsDomainPool: Pair<List<String>, List<String>> = (emptyList<String>() to emptyList()),
        addresses: List<String> = emptyList(),
        peers: List<VpnPeer> = emptyList(),
    ): VpnConfiguration {
        return VpnConfiguration(
            interfaceName = interfaceName,
            dnsDomainPool = dnsDomainPool,
            addresses = addresses.toMutableList(),
            privateKey = LOCAL_PRIVATE_KEY,
            peers = peers,
        )
    }

    private class FailureInjectingExecutor(
        private val delegate: InMemoryInterfaceCommandExecutor,
    ) : InterfaceCommandExecutor by delegate {
        var failOnDns: Boolean = false
        var deleteCalls: Int = 0
        val appliedDnsCalls: MutableList<Pair<String, Pair<List<String>, List<String>>>> = mutableListOf()

        override fun applyDns(interfaceName: String, dnsDomainPool: Pair<List<String>, List<String>>) {
            appliedDnsCalls += interfaceName to (dnsDomainPool.first.toList() to dnsDomainPool.second.toList())
            if (failOnDns) {
                failOnDns = false
                delegate.applyDns(interfaceName, dnsDomainPool)
                throw IllegalStateException("forced dns failure")
            }
            delegate.applyDns(interfaceName, dnsDomainPool)
        }

        override fun deleteInterface(interfaceName: String) {
            deleteCalls++
            delegate.deleteInterface(interfaceName)
        }
    }

    private class CountingExecutor(
        private val delegate: InterfaceCommandExecutor,
    ) : InterfaceCommandExecutor by delegate {
        var openBridgeCalls: Int = 0
        var closedBridgeCount: Int = 0

        override fun openPacketBridge(
            interfaceName: String,
            pipe: com.rafambn.wgkotlin.session.DuplexChannelPipe<ByteArray>,
            onFailure: (Throwable) -> Unit,
        ): AutoCloseable {
            openBridgeCalls++
            return AutoCloseable { closedBridgeCount++ }
        }
    }

    private class BridgeFailureExecutor(
        private val delegate: InMemoryInterfaceCommandExecutor = InMemoryInterfaceCommandExecutor(),
    ) : InterfaceCommandExecutor by delegate {
        var closedBridgeCount: Int = 0
        private var onBridgeFailure: ((Throwable) -> Unit)? = null

        override fun openPacketBridge(
            interfaceName: String,
            pipe: DuplexChannelPipe<ByteArray>,
            onFailure: (Throwable) -> Unit,
        ): AutoCloseable {
            val delegateBridge = delegate.openPacketBridge(interfaceName, pipe, onFailure)
            onBridgeFailure = onFailure
            return AutoCloseable {
                closedBridgeCount++
                delegateBridge.close()
            }
        }

        fun failBridge(throwable: Throwable) {
            checkNotNull(onBridgeFailure) { "Bridge was not opened before failBridge()" }
                .invoke(throwable)
        }
    }

    // ── Packet bridge tests ───────────────────────────────────────────────────

    /**
     * Starts a minimal kRPC server that echoes packetIO packets back to the caller.
     */
    @Test
    fun upOpensRpcPacketBridgeAndPacketsFlowBidirectionally() = runBlocking {
        val port = randomPort()
        val serverReceived = Channel<ByteArray>(capacity = 8)

        val server: EmbeddedServer<NettyApplicationEngine, *> = embeddedServer(Netty, port = port) {
            install(WebSockets)
            install(Krpc) {
                serialization {
                    protobuf()
                }
            }
            routing {
                rpc(DAEMON_RPC_PATH) {
                    rpcConfig {
                        serialization {
                            protobuf()
                        }
                    }
                    registerService<DaemonProcessApi> {
                        object : StubPacketStreamApi() {
                            override fun packetIO(
                                interfaceName: String,
                                outgoingPackets: Flow<ByteArray>,
                            ): Flow<ByteArray> = kotlinx.coroutines.flow.channelFlow {
                                outgoingPackets.collect { packet ->
                                    serverReceived.send(packet)
                                    send(packet)
                                }
                            }
                        }
                    }
                }
            }
        }
        server.start(wait = false)
        delay(200)

        val baseExecutor = InMemoryInterfaceCommandExecutor()
        val rpcExecutor = DaemonBackedInterfaceCommandExecutor(
            host = "127.0.0.1",
            port = port,
            timeout = Duration.ofSeconds(5),
        )
        val mixedExecutor = object : InterfaceCommandExecutor by baseExecutor {
            override fun openPacketBridge(
                interfaceName: String,
                pipe: DuplexChannelPipe<ByteArray>,
                onFailure: (Throwable) -> Unit,
            ): AutoCloseable {
                return rpcExecutor.openPacketBridge(interfaceName, pipe, onFailure)
            }
        }

        val tunPipe = DuplexChannelPipe.create<ByteArray>()
        val manager = JvmInterfaceManager(interfaceName = "utun160", commandExecutor = mixedExecutor, tunPipe = tunPipe.first)
        manager.create(configuration(interfaceName = "utun160"))
        manager.up()

        try {
            val outbound = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
            tunPipe.second.send(outbound)

            val atServer = withTimeout(2_000) { serverReceived.receive() }
            assertTrue(atServer.contentEquals(outbound))

            val echoed = withTimeout(2_000) { tunPipe.second.receive() }
            assertTrue(echoed.contentEquals(outbound))
        } finally {
            manager.down()
            server.stop(100, 1_000)
        }
    }

    private open inner class StubPacketStreamApi : DaemonProcessApi {
        override suspend fun ping(): CommandResult<PingResponse> = unsupported("PING")
        override suspend fun createInterface(interfaceName: String): CommandResult<CreateInterfaceResponse> = unsupported("CREATE_INTERFACE")
        override suspend fun interfaceExists(interfaceName: String): CommandResult<InterfaceExistsResponse> = unsupported("INTERFACE_EXISTS")
        override suspend fun applyMtu(interfaceName: String, mtu: Int): CommandResult<ApplyMtuResponse> = unsupported("APPLY_MTU")
        override suspend fun applyAddresses(interfaceName: String, addresses: List<String>): CommandResult<ApplyAddressesResponse> = unsupported("APPLY_ADDRESSES")
        override suspend fun applyRoutes(interfaceName: String, routes: List<String>): CommandResult<ApplyRoutesResponse> = unsupported("APPLY_ROUTES")
        override suspend fun applyDns(
            interfaceName: String,
            dnsDomainPool: Pair<List<String>, List<String>>,
        ): CommandResult<ApplyDnsResponse> = unsupported("APPLY_DNS")
        override suspend fun readInterfaceInformation(interfaceName: String): CommandResult<ReadInterfaceInformationResponse> =
            unsupported("READ_INTERFACE_INFORMATION")
        override suspend fun deleteInterface(interfaceName: String): CommandResult<DeleteInterfaceResponse> = unsupported("DELETE_INTERFACE")
        override fun packetIO(interfaceName: String, outgoingPackets: Flow<ByteArray>): Flow<ByteArray> = emptyFlow()
    }

    private fun <T> unsupported(command: String): CommandResult<T> {
        return CommandResult.failure(
            kind = DaemonErrorKind.UNKNOWN_COMMAND,
            message = "Unsupported command `$command`",
        )
    }

    private fun randomPort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val LOCAL_PRIVATE_KEY = "oA8gY5Yg7R6pujISiFDUFxIr05o2IaNbS1Ry6j3TzXs="
    }
}

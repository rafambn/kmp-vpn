package com.rafambn.kmpvpn

import com.rafambn.kmpvpn.daemon.client.DaemonProcessClient
import com.rafambn.kmpvpn.daemon.protocol.CommandResult
import com.rafambn.kmpvpn.daemon.protocol.DAEMON_RPC_PATH
import com.rafambn.kmpvpn.daemon.protocol.DaemonErrorKind
import com.rafambn.kmpvpn.daemon.protocol.DaemonProcessApi
import com.rafambn.kmpvpn.daemon.protocol.daemonRpcUrl
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyAddressesResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyDnsResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyMtuResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyRoutesResponse
import com.rafambn.kmpvpn.daemon.protocol.response.CreateInterfaceResponse
import com.rafambn.kmpvpn.daemon.protocol.response.DeleteInterfaceResponse
import com.rafambn.kmpvpn.daemon.protocol.response.InterfaceExistsResponse
import com.rafambn.kmpvpn.daemon.protocol.response.PingResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ReadInterfaceInformationResponse
import com.rafambn.kmpvpn.session.CryptoSessionManagerImpl
import com.rafambn.kmpvpn.session.DuplexChannelPipe
import com.rafambn.kmpvpn.session.PeerSession
import com.rafambn.kmpvpn.session.SocketManagerImpl
import com.rafambn.kmpvpn.session.factory.PeerSessionFactory
import com.rafambn.kmpvpn.session.io.PacketAction
import com.rafambn.kmpvpn.session.io.UdpDatagram
import com.rafambn.kmpvpn.session.io.UdpEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import java.net.ServerSocket
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.protobuf.protobuf
import kotlinx.rpc.withService
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end data-plane tests that exercise the full packet path:
 *
 *   outbound: fake-daemon kRPC packetIO → RPC bridge → CryptoSessionManager
 *             → SocketManager → real UDP socket
 *
 *   inbound:  network pipe → CryptoSessionManager
 *             → RPC bridge → fake-daemon packetIO collector
 */
@OptIn(ExperimentalSerializationApi::class)
class VpnDataPlaneEndToEndTest {

    // ── Outbound ─────────────────────────────────────────────────────────────

    @Test
    fun outboundTunPacketIsEncryptedAndDeliveredToUdpPeer() = runBlocking {
        // Real UDP socket that stands in for the remote WireGuard peer.
        val selector = SelectorManager(Dispatchers.IO)
        val udpPeer = aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0))
        val peerPort = (udpPeer.localAddress as InetSocketAddress).port

        // Fake daemon kRPC server: emits injected packets from packetIO.
        val daemonPort = randomPort()
        val injectChannel = Channel<ByteArray>(capacity = 8)
        val daemonServer = embeddedServer(Netty, port = daemonPort) {
            install(ServerWebSockets)
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
                        packetStreamService(
                            onOutgoingPacket = { /* ignore */ },
                            incomingPackets = injectChannel.receiveAsFlow(),
                        )
                    }
                }
            }
        }
        daemonServer.start(wait = false)
        delay(200)

        // Build pipeline.
        val (jvmEnd, cryptoEnd) = DuplexChannelPipe.create<ByteArray>()
        val (socketEnd, cryptoNetworkEnd) = DuplexChannelPipe.create<UdpDatagram>()
        val socketManager = SocketManagerImpl()
        socketManager.start(listenPort = 0, networkPipe = socketEnd, onFailure = { throw AssertionError("Socket failure", it) })

        val config = VpnConfiguration(
            interfaceName = "wg-e2e",
            privateKey = "private-key",
            peers = listOf(
                VpnPeer(
                    publicKey = "peer-a",
                    endpointAddress = "127.0.0.1",
                    endpointPort = peerPort,
                    allowedIps = listOf("0.0.0.0/0"),
                ),
            ),
        )
        val cryptoManager = CryptoSessionManagerImpl(peerSessionFactory = PassthroughSessionFactory())
        cryptoManager.reconcileSessions(config)
        cryptoManager.start(
            tunPipe = cryptoEnd,
            networkPipe = cryptoNetworkEnd,
            onFailure = { throw AssertionError("Crypto failure", it) },
        )

        val bridge = openPacketRpcBridge(
            port = daemonPort,
            interfaceName = "wg-e2e",
            pipe = jvmEnd,
        )
        delay(100)

        try {
            // Fake daemon injects a raw IPv4 packet (simulating a TUN read).
            injectChannel.send(fakeIpv4Packet())

            // Expect the encrypted form to arrive at the UDP peer.
            val received = withTimeout(3_000) { udpPeer.receive() }
            val bytes = received.packet.readByteArray()
            assertTrue(bytes.contentEquals(fakeIpv4Packet() + ENCRYPT_MARKER))
        } finally {
            bridge.close()
            cryptoManager.stop()
            socketManager.stop()
            daemonServer.stop(100, 1_000)
            udpPeer.close()
            selector.close()
        }
    }

    // ── Inbound ──────────────────────────────────────────────────────────────

    /**
     * Proves that a decrypted packet reaches the daemon TUN writer.
     *
     * SocketManager is exercised separately in [SocketManagerTest]; here a plain
     * [DuplexChannelPipe] stands in for the network layer so the test stays
     * focused on the crypto ↔ bridge path.
     */
    @Test
    fun inboundDecryptedPacketIsWrittenToTunBridge() = runBlocking {
        // Fake daemon kRPC server: records packets pushed back from the app.
        val daemonPort = randomPort()
        val serverReceived = Channel<ByteArray>(capacity = 8)
        val daemonServer = embeddedServer(Netty, port = daemonPort) {
            install(ServerWebSockets)
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
                        packetStreamService(
                            onOutgoingPacket = { packet -> serverReceived.send(packet) },
                            incomingPackets = emptyFlow(),
                        )
                    }
                }
            }
        }
        daemonServer.start(wait = false)
        delay(200)

        // Build pipeline: bridge ↔ CryptoSessionManager.
        // A plain DuplexChannelPipe<UdpDatagram> replaces SocketManager so we can inject
        // inbound datagrams directly without real UDP port discovery.
        val (jvmEnd, cryptoEnd) = DuplexChannelPipe.create<ByteArray>()
        val (networkSocketEnd, networkCryptoEnd) = DuplexChannelPipe.create<UdpDatagram>()

        val config = VpnConfiguration(
            interfaceName = "wg-e2e",
            privateKey = "private-key",
            peers = listOf(
                VpnPeer(
                    publicKey = "peer-a",
                    endpointAddress = "198.51.100.1",
                    endpointPort = 51820,
                    allowedIps = listOf("0.0.0.0/0"),
                ),
            ),
        )
        val cryptoManager = CryptoSessionManagerImpl(peerSessionFactory = PassthroughSessionFactory())
        cryptoManager.reconcileSessions(config)
        cryptoManager.start(
            tunPipe = cryptoEnd,
            networkPipe = networkCryptoEnd,
            onFailure = { throw AssertionError("Crypto failure", it) },
        )

        val bridge = openPacketRpcBridge(
            port = daemonPort,
            interfaceName = "wg-e2e",
            pipe = jvmEnd,
        )
        delay(100)

        try {
            // Inject a datagram as if it arrived from the peer via UDP.
            val payload = byteArrayOf(0x01, 0x02, 0x03)
            networkSocketEnd.send(
                UdpDatagram(
                    payload = payload,
                    remoteEndpoint = UdpEndpoint(address = "198.51.100.1", port = 51820),
                ),
            )

            // Expect the decrypted form at the fake daemon WS server.
            val frame = withTimeout(3_000) { serverReceived.receive() }
            assertTrue(frame.contentEquals(payload + DECRYPT_MARKER))
        } finally {
            bridge.close()
            cryptoManager.stop()
            daemonServer.stop(100, 1_000)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun fakeIpv4Packet(): ByteArray =
        byteArrayOf(
            0x45, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00, 0x40, 0x00, 0x00, 0x00,
            10, 0, 0, 1,  // src: 10.0.0.1
            8, 8, 8, 8,   // dst: 8.8.8.8
        )

    private fun randomPort(): Int = ServerSocket(0).use { it.localPort }

    private fun openPacketRpcBridge(
        port: Int,
        interfaceName: String,
        pipe: DuplexChannelPipe<ByteArray>,
    ): AutoCloseable {
        val httpClient = HttpClient(CIO) {
            install(WebSockets)
            installKrpc {
                serialization {
                    protobuf()
                }
            }
        }
        val rpcClient = httpClient.rpc(daemonRpcUrl(host = "127.0.0.1", port = port))
        val client = DaemonProcessClient(
            service = rpcClient.withService<DaemonProcessApi>(),
            timeout = Duration.ofSeconds(15),
            resourceCloser = { httpClient.close() },
        )
        val outgoingPackets = Channel<ByteArray>(capacity = DuplexChannelPipe.DEFAULT_CAPACITY)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receiveJob = scope.launch {
            client.packetIO(interfaceName = interfaceName, outgoingPackets = outgoingPackets.receiveAsFlow())
                .collect { packet -> pipe.send(packet) }
        }
        val sendJob = scope.launch {
            while (true) {
                outgoingPackets.send(pipe.receive())
            }
        }

        return AutoCloseable {
            scope.cancel()
            runBlocking {
                runCatching { receiveJob.cancelAndJoin() }
                runCatching { sendJob.cancelAndJoin() }
            }
            outgoingPackets.close()
            client.close()
        }
    }

    private fun packetStreamService(
        onOutgoingPacket: suspend (ByteArray) -> Unit,
        incomingPackets: Flow<ByteArray>,
    ): DaemonProcessApi {
        return object : DaemonProcessApi {
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

            override fun packetIO(
                interfaceName: String,
                outgoingPackets: Flow<ByteArray>,
            ): Flow<ByteArray> {
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    outgoingPackets.collect { packet -> onOutgoingPacket(packet) }
                }
                return incomingPackets
            }
        }
    }

    private fun <T> unsupported(command: String): CommandResult<T> {
        return CommandResult.failure(
            kind = DaemonErrorKind.UNKNOWN_COMMAND,
            message = "Unsupported command `$command`",
        )
    }

    private class PassthroughSessionFactory : PeerSessionFactory {
        override fun create(config: VpnConfiguration, peer: VpnPeer, peerIndex: Int): PeerSession =
            PassthroughPeerSession(peerPublicKey = peer.publicKey, peerIndex = peerIndex)
    }

    private class PassthroughPeerSession(
        override val peerPublicKey: String,
        override val peerIndex: Int,
    ) : PeerSession {
        private var closed = false
        override val isActive: Boolean get() = !closed
        override fun close() { closed = true }

        override fun encryptRawPacket(src: ByteArray, dstSize: UInt): PacketAction =
            PacketAction.WriteToNetwork(src + ENCRYPT_MARKER)

        override fun decryptToRawPacket(src: ByteArray, dstSize: UInt): PacketAction =
            if (src.isEmpty()) PacketAction.Done else PacketAction.WriteToTunIpv4(src + DECRYPT_MARKER)

        override fun runPeriodicTask(dstSize: UInt): PacketAction = PacketAction.Done
    }

    private companion object {
        val ENCRYPT_MARKER = byteArrayOf(0xEE.toByte())
        val DECRYPT_MARKER = byteArrayOf(0xDD.toByte())
    }
}

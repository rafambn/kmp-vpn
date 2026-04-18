package com.rafambn.wgkotlin.daemon

import com.rafambn.wgkotlin.daemon.client.DaemonProcessClient
import com.rafambn.wgkotlin.daemon.command.ProcessInvocationModel
import com.rafambn.wgkotlin.daemon.command.ProcessLauncher
import com.rafambn.wgkotlin.daemon.command.ProcessOutputModel
import com.rafambn.wgkotlin.daemon.planner.LinuxOperationPlanner
import com.rafambn.wgkotlin.daemon.protocol.DaemonProcessApi
import com.rafambn.wgkotlin.daemon.protocol.DEFAULT_DAEMON_HOST
import com.rafambn.wgkotlin.daemon.protocol.daemonRpcUrl
import com.rafambn.wgkotlin.daemon.tun.TunHandle
import com.rafambn.wgkotlin.daemon.tun.TunHandleFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import java.net.ServerSocket
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.protobuf.protobuf
import kotlinx.rpc.withService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class DaemonClientIntegrationTest {

    @Test
    fun clientRoundTripAgainstDaemonServerUsesRealHandlers() = runBlocking {
        val launcher = RecordingLauncher()
        val service = DaemonProcessApiImpl(
            operationPlanner = LinuxOperationPlanner(),
            processLauncher = launcher,
            tunHandleFactory = RecordingTunHandleFactory(),
        )
        val port = randomPort()
        val engine = createDaemonServer(
            host = DEFAULT_DAEMON_HOST,
            port = port,
            service = service,
        )

        engine.start(wait = false)
        delay(200)

        val httpClient = HttpClient(CIO) {
            install(WebSockets)
            installKrpc {
                serialization {
                    protobuf()
                }
            }
        }
        val rpcClient = httpClient.rpc(daemonRpcUrl(host = DEFAULT_DAEMON_HOST, port = port))
        val client = DaemonProcessClient(
            timeout = java.time.Duration.ofSeconds(15),
            service = rpcClient.withService<DaemonProcessApi>(),
            resourceCloser = { httpClient.close() },
        )
        try {
            val hello = client.handshake()
            assertTrue(hello.isSuccess)

            val createResult = client.createInterface(interfaceName = "utun0")
            assertTrue(createResult.isSuccess)

            val mtuResult = client.applyMtu(
                interfaceName = "utun0",
                mtu = 1420,
            )
            assertTrue(mtuResult.isSuccess)
        } finally {
            client.close()
            engine.stop(100, 1_000)
        }

        assertEquals(2, launcher.invocations.size)
        assertEquals(listOf("tuntap", "add", "dev", "utun0", "mode", "tun"), launcher.invocations[0].arguments)
        assertEquals(listOf("link", "set", "dev", "utun0", "mtu", "1420"), launcher.invocations[1].arguments)
    }

    @Test
    fun packetIoStaysActiveAndDeliversEachPacketOnce() = runBlocking {
        val launcher = RecordingLauncher()
        val tunHandle = RecordingTunHandle(interfaceName = "utun0")
        val service = DaemonProcessApiImpl(
            operationPlanner = LinuxOperationPlanner(),
            processLauncher = launcher,
            tunHandleFactory = RecordingTunHandleFactory(tunHandle),
        )
        val port = randomPort()
        val engine = createDaemonServer(
            host = DEFAULT_DAEMON_HOST,
            port = port,
            service = service,
        )

        engine.start(wait = false)
        delay(200)

        val client = createClient(port)
        val outgoingPackets = Channel<ByteArray>(capacity = 8)
        val incomingPackets = Channel<ByteArray>(capacity = 8)
        val collectorJob = launch {
            client.packetIO(
                interfaceName = "utun0",
                outgoingPackets = outgoingPackets.receiveAsFlow(),
            ).collect { packet ->
                incomingPackets.send(packet)
            }
        }

        try {
            val outboundOne = byteArrayOf(0x01, 0x02, 0x03)
            val outboundTwo = byteArrayOf(0x04, 0x05, 0x06)
            val inboundOne = byteArrayOf(0x11, 0x12)
            val inboundTwo = byteArrayOf(0x21, 0x22)

            outgoingPackets.send(outboundOne)
            assertTrue(withTimeout(2_000) { tunHandle.awaitWritten() }.contentEquals(outboundOne))

            tunHandle.enqueueInbound(inboundOne)
            assertTrue(withTimeout(2_000) { incomingPackets.receive() }.contentEquals(inboundOne))

            outgoingPackets.send(outboundTwo)
            assertTrue(withTimeout(2_000) { tunHandle.awaitWritten() }.contentEquals(outboundTwo))

            tunHandle.enqueueInbound(inboundTwo)
            assertTrue(withTimeout(2_000) { incomingPackets.receive() }.contentEquals(inboundTwo))


            outgoingPackets.send(outboundOne)
            outgoingPackets.send(outboundTwo)
            assertTrue(withTimeout(2_000) { tunHandle.awaitWritten() }.contentEquals(outboundOne))
            assertTrue(withTimeout(2_000) { tunHandle.awaitWritten() }.contentEquals(outboundTwo))
            tunHandle.enqueueInbound(inboundOne)
            assertTrue(withTimeout(2_000) { incomingPackets.receive() }.contentEquals(inboundOne))
            tunHandle.enqueueInbound(inboundTwo)
            assertTrue(withTimeout(2_000) { incomingPackets.receive() }.contentEquals(inboundTwo))

            assertNull(withTimeoutOrNull(300) { tunHandle.awaitWritten() })
            assertNull(withTimeoutOrNull(300) { incomingPackets.receive() })
        } finally {
            collectorJob.cancelAndJoin()
            outgoingPackets.close()
            client.close()
            engine.stop(100, 1_000)
        }

        assertTrue(launcher.invocations.any { it.arguments == listOf("link", "show", "dev", "utun0") })
        assertTrue(launcher.invocations.any { it.arguments == listOf("link", "set", "dev", "utun0", "up") })
    }

    @Test
    fun packetIoDoesNotWriteMorePacketsAfterCollectorCancellation() = runBlocking {
        val tunHandle = RecordingTunHandle(interfaceName = "utun0")
        val service = DaemonProcessApiImpl(
            operationPlanner = LinuxOperationPlanner(),
            processLauncher = RecordingLauncher(),
            tunHandleFactory = RecordingTunHandleFactory(tunHandle),
        )
        val port = randomPort()
        val engine = createDaemonServer(
            host = DEFAULT_DAEMON_HOST,
            port = port,
            service = service,
        )

        engine.start(wait = false)
        delay(200)

        val client = createClient(port)
        val outgoingPackets = Channel<ByteArray>(capacity = 8)
        val collectorJob = launch {
            client.packetIO(
                interfaceName = "utun0",
                outgoingPackets = outgoingPackets.receiveAsFlow(),
            ).collect { /* keep stream active */ }
        }

        try {
            val firstPacket = byteArrayOf(0x31, 0x32)
            outgoingPackets.send(firstPacket)
            assertTrue(withTimeout(2_000) { tunHandle.awaitWritten() }.contentEquals(firstPacket))

            collectorJob.cancelAndJoin()
            outgoingPackets.send(byteArrayOf(0x41, 0x42))

            assertNull(withTimeoutOrNull(300) { tunHandle.awaitWrittenOrNull() })
        } finally {
            outgoingPackets.close()
            client.close()
            engine.stop(100, 1_000)
        }
    }

    @Test
    fun packetIoAllowsDifferentInterfacesAtSameTime() = runBlocking {
        val tunOne = RecordingTunHandle(interfaceName = "utun1")
        val tunTwo = RecordingTunHandle(interfaceName = "utun2")
        val service = DaemonProcessApiImpl(
            operationPlanner = LinuxOperationPlanner(),
            processLauncher = RecordingLauncher(),
            tunHandleFactory = RecordingTunHandleFactory(tunOne, tunTwo),
        )
        val port = randomPort()
        val engine = createDaemonServer(
            host = DEFAULT_DAEMON_HOST,
            port = port,
            service = service,
        )

        engine.start(wait = false)
        delay(200)

        val client = createClient(port)
        val outgoingOne = Channel<ByteArray>(capacity = 8)
        val outgoingTwo = Channel<ByteArray>(capacity = 8)
        val incomingOne = Channel<ByteArray>(capacity = 8)
        val incomingTwo = Channel<ByteArray>(capacity = 8)

        val jobOne = launch {
            client.packetIO("utun1", outgoingOne.receiveAsFlow()).collect { packet ->
                incomingOne.send(packet)
            }
        }
        val jobTwo = launch {
            client.packetIO("utun2", outgoingTwo.receiveAsFlow()).collect { packet ->
                incomingTwo.send(packet)
            }
        }

        try {
            val outboundOne = byteArrayOf(0x11, 0x01)
            val outboundTwo = byteArrayOf(0x22, 0x02)
            val inboundOne = byteArrayOf(0x33, 0x03)
            val inboundTwo = byteArrayOf(0x44, 0x04)

            outgoingOne.send(outboundOne)
            outgoingTwo.send(outboundTwo)
            assertTrue(withTimeout(2_000) { tunOne.awaitWritten() }.contentEquals(outboundOne))
            assertTrue(withTimeout(2_000) { tunTwo.awaitWritten() }.contentEquals(outboundTwo))

            tunOne.enqueueInbound(inboundOne)
            tunTwo.enqueueInbound(inboundTwo)
            assertTrue(withTimeout(2_000) { incomingOne.receive() }.contentEquals(inboundOne))
            assertTrue(withTimeout(2_000) { incomingTwo.receive() }.contentEquals(inboundTwo))
        } finally {
            jobOne.cancelAndJoin()
            jobTwo.cancelAndJoin()
            outgoingOne.close()
            outgoingTwo.close()
            client.close()
            engine.stop(100, 1_000)
        }
    }

    @Test
    fun packetIoRejectsSecondConnectionForSameInterface() = runBlocking {
        val tunHandle = RecordingTunHandle(interfaceName = "utun1")
        val service = DaemonProcessApiImpl(
            operationPlanner = LinuxOperationPlanner(),
            processLauncher = RecordingLauncher(),
            tunHandleFactory = RecordingTunHandleFactory(tunHandle),
        )
        val port = randomPort()
        val engine = createDaemonServer(
            host = DEFAULT_DAEMON_HOST,
            port = port,
            service = service,
        )

        engine.start(wait = false)
        delay(200)

        val client = createClient(port)
        val firstOutgoing = Channel<ByteArray>(capacity = 8)
        val secondOutgoing = Channel<ByteArray>(capacity = 8)
        val firstJob = launch {
            client.packetIO("utun1", firstOutgoing.receiveAsFlow()).collect { }
        }

        try {
            withTimeout(2_000) {
                while (!tunHandle.upObserved) {
                    delay(10)
                }
            }

            val failure = kotlin.test.assertFailsWith<IllegalStateException> {
                client.packetIO("utun1", secondOutgoing.receiveAsFlow()).collect { }
            }
            assertTrue(failure.message.orEmpty().contains("Session already active for utun1"))
        } finally {
            firstJob.cancelAndJoin()
            firstOutgoing.close()
            secondOutgoing.close()
            client.close()
            engine.stop(100, 1_000)
        }
    }

    private fun createClient(port: Int): DaemonProcessClient {
        val httpClient = HttpClient(CIO) {
            install(WebSockets)
            installKrpc {
                serialization {
                    protobuf()
                }
            }
        }
        val rpcClient = httpClient.rpc(daemonRpcUrl(host = DEFAULT_DAEMON_HOST, port = port))
        return DaemonProcessClient(
            timeout = Duration.ofSeconds(15),
            service = rpcClient.withService<DaemonProcessApi>(),
            resourceCloser = { httpClient.close() },
        )
    }

    private class RecordingLauncher : ProcessLauncher {
        val invocations: MutableList<ProcessInvocationModel> = mutableListOf()

        override fun run(invocation: ProcessInvocationModel): ProcessOutputModel {
            invocations += invocation
            return ProcessOutputModel(
                exitCode = 0,
                stdout = "",
                stderr = "",
            )
        }
    }

    private class RecordingTunHandle(
        override val interfaceName: String,
    ) : TunHandle {
        private val writtenPackets = Channel<ByteArray>(capacity = 8)
        private val inboundPackets = Channel<ByteArray>(capacity = 8)
        @Volatile
        var upObserved: Boolean = false

        override suspend fun readPacket(): ByteArray? {
            upObserved = true
            return withTimeoutOrNull(50) { inboundPackets.receive() }
        }

        override suspend fun writePacket(packet: ByteArray) {
            writtenPackets.send(packet.copyOf())
        }

        suspend fun enqueueInbound(packet: ByteArray) {
            inboundPackets.send(packet.copyOf())
        }

        suspend fun awaitWritten(): ByteArray {
            return writtenPackets.receive()
        }

        suspend fun awaitWrittenOrNull(): ByteArray? {
            return writtenPackets.receiveCatching().getOrNull()
        }

        override fun close() {
            inboundPackets.close()
            writtenPackets.close()
        }
    }

    private class RecordingTunHandleFactory(
        vararg handles: RecordingTunHandle,
    ) : TunHandleFactory {
        private val handlesByInterface = handles.associateBy { it.interfaceName }

        override fun open(interfaceName: String): TunHandle {
            return handlesByInterface[interfaceName]
                ?: error("No test TUN handle for $interfaceName")
        }
    }

    private fun randomPort(): Int {
        return ServerSocket(0).use { socket -> socket.localPort }
    }
}

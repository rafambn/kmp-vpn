package com.rafambn.wgkotlin.daemon.client

import com.rafambn.wgkotlin.daemon.protocol.CommandResult
import com.rafambn.wgkotlin.daemon.protocol.DAEMON_RPC_PATH
import com.rafambn.wgkotlin.daemon.protocol.DaemonErrorKind
import com.rafambn.wgkotlin.daemon.protocol.DaemonProcessApi
import com.rafambn.wgkotlin.daemon.protocol.DnsConfig
import com.rafambn.wgkotlin.daemon.protocol.TunSessionConfig
import com.rafambn.wgkotlin.daemon.protocol.response.PingResponse
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.net.ServerSocket
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.protobuf.protobuf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class DaemonClientSmokeTest {
    @Test
    fun krpcClientPerformsHandshakeAndStartSessionRoundTrip() = runBlocking {
        val port = randomPort()
        val engine = startServer(
            port = port,
            service = object : StubDaemonProcessApi() {
                override suspend fun ping(): CommandResult<PingResponse> = success(PingResponse)

                override fun startSession(config: TunSessionConfig, outgoingPackets: Flow<ByteArray>): Flow<ByteArray> {
                    assertEquals("utun55", config.interfaceName)
                    assertEquals(listOf("corp.local"), config.dns.searchDomains)
                    return flowOf(byteArrayOf(1, 2, 3))
                }
            },
        )

        val client = DaemonProcessClient.create(config = DaemonClientConfig(port = port))

        try {
            assertTrue(client.handshake().isSuccess)

            val packets: List<ByteArray> = client.startSession(
                config = TunSessionConfig(
                    interfaceName = "utun55",
                    dns = DnsConfig(searchDomains = listOf("corp.local"), servers = listOf("1.1.1.1")),
                ),
                outgoingPackets = flowOf(byteArrayOf(9)),
            ).toList()

            assertEquals(1, packets.size)
            assertEquals("1, 2, 3", packets.single().joinToString())
        } finally {
            client.close()
            engine.stop(100, 1_000)
        }
    }

    @Test
    fun requestTimeoutIsSurfaced() = runBlocking {
        val port = randomPort()
        val engine = startServer(
            port = port,
            service = object : StubDaemonProcessApi() {
                override suspend fun ping(): CommandResult<PingResponse> {
                    delay(300)
                    return success(PingResponse)
                }
            },
        )

        val client = DaemonProcessClient.create(
            config = DaemonClientConfig(
                port = port,
                timeout = Duration.ofMillis(50),
            ),
        )

        try {
            val failure = assertFailsWith<DaemonClientException.Timeout> {
                client.ping()
            }
            assertEquals(50L, failure.timeout.toMillis())
        } finally {
            client.close()
            engine.stop(100, 1_000)
        }
    }

    @Test
    fun globalBootstrapSupportsOverridesAndMultipleClientConfigs() = runBlocking {
        val stubService = object : StubDaemonProcessApi() {
            override suspend fun ping(): CommandResult<PingResponse> = success(PingResponse)
        }
        val overrideModule = module {
            factory<DaemonProcessApi> { stubService }
        }

        val first = DaemonProcessClient.create(
            config = DaemonClientConfig(port = 8787),
            overrideModules = listOf(overrideModule),
        )
        val second = DaemonProcessClient.create(
            config = DaemonClientConfig(port = 8788),
            overrideModules = listOf(overrideModule),
        )

        try {
            assertTrue(first.ping().isSuccess)
            assertTrue(second.ping().isSuccess)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun handshakeRejectsRemoteFailure() = runBlocking {
        val client = DaemonProcessClient(
            service = object : StubDaemonProcessApi() {
                override suspend fun ping(): CommandResult<PingResponse> {
                    return CommandResult.failure(
                        kind = DaemonErrorKind.INTERNAL_ERROR,
                        message = "nope",
                    )
                }
            },
        )

        val failure = assertFailsWith<DaemonClientException.ProtocolViolation> {
            client.handshake()
        }

        assertTrue(failure.message.orEmpty().contains("nope"))
    }

    private fun startServer(
        port: Int,
        service: DaemonProcessApi,
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val engine = embeddedServer(Netty, host = "127.0.0.1", port = port, module = {
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
                        service
                    }
                }
            }
        })
        engine.start(wait = false)
        return engine
    }

    private fun <S> success(data: S): CommandResult<S> = CommandResult.success(data = data)

    private open class StubDaemonProcessApi : DaemonProcessApi {
        override suspend fun ping(): CommandResult<PingResponse> {
            return CommandResult.failure(
                kind = DaemonErrorKind.UNKNOWN_COMMAND,
                message = "Unsupported command `PING`",
            )
        }

        override fun startSession(config: TunSessionConfig, outgoingPackets: Flow<ByteArray>): Flow<ByteArray> = emptyFlow()
    }

    private fun randomPort(): Int {
        return ServerSocket(0).use { socket -> socket.localPort }
    }
}

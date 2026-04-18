package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.daemon.client.DaemonProcessClient
import com.rafambn.wgkotlin.daemon.protocol.CommandResult
import com.rafambn.wgkotlin.daemon.protocol.DaemonProcessApi
import com.rafambn.wgkotlin.daemon.protocol.daemonRpcUrl
import com.rafambn.wgkotlin.session.DuplexChannelPipe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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

@OptIn(ExperimentalSerializationApi::class)
class DaemonBackedInterfaceCommandExecutor(
    private val host: String,
    private val port: Int,
    private val timeout: Duration,
) : InterfaceCommandExecutor {
    private val client: DaemonProcessClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val httpClient = HttpClient(CIO) {
            install(WebSockets)
            installKrpc {
                serialization {
                    protobuf()
                }
            }
        }
        val rpcClient = httpClient.rpc(daemonRpcUrl(host = host, port = port))
        DaemonProcessClient(
            service = rpcClient.withService<DaemonProcessApi>(),
            timeout = timeout,
            resourceCloser = { httpClient.close() },
        )
    }

    override fun createInterface(interfaceName: String) {
        callDaemon(operation = "createInterface", interfaceName = interfaceName) { daemon ->
            daemon.createInterface(interfaceName = interfaceName)
        }
    }

    override fun interfaceExists(interfaceName: String): Boolean {
        val result = callDaemon(operation = "interfaceExists", interfaceName = interfaceName) { daemon ->
            daemon.interfaceExists(interfaceName)
        }
        return when (result) {
            is CommandResult.Success -> result.data.exists
            is CommandResult.Failure -> throw IllegalStateException(
                "Daemon operation `interfaceExists` failed for `$interfaceName`: ${result.kind} ${result.message}",
            )
        }
    }

    override fun applyMtu(interfaceName: String, mtu: Int) {
        callDaemon(operation = "applyMtu", interfaceName = interfaceName) { daemon ->
            daemon.applyMtu(interfaceName = interfaceName, mtu = mtu)
        }
    }

    override fun applyAddresses(interfaceName: String, addresses: List<String>) {
        callDaemon(operation = "applyAddresses", interfaceName = interfaceName) { daemon ->
            daemon.applyAddresses(interfaceName = interfaceName, addresses = addresses)
        }
    }

    override fun applyRoutes(interfaceName: String, routes: List<String>) {
        callDaemon(operation = "applyRoutes", interfaceName = interfaceName) { daemon ->
            daemon.applyRoutes(interfaceName = interfaceName, routes = routes)
        }
    }

    override fun applyDns(interfaceName: String, dnsDomainPool: Pair<List<String>, List<String>>) {
        callDaemon(operation = "applyDns", interfaceName = interfaceName) { daemon ->
            daemon.applyDns(interfaceName = interfaceName, dnsDomainPool = dnsDomainPool)
        }
    }

    override fun readInformation(interfaceName: String): VpnInterfaceInformation? {
        val result = runCatching {
            callDaemon(
                operation = "readInterfaceInformation",
                interfaceName = interfaceName,
                throwOnFailure = false,
            ) { daemon ->
                daemon.readInterfaceInformation(interfaceName = interfaceName)
            }
        }.getOrNull() ?: return null

        return when (result) {
            is CommandResult.Success -> VpnInterfaceInformation(
                interfaceName = result.data.interfaceName,
                isUp = result.data.isUp,
                addresses = result.data.addresses,
                dnsDomainPool = result.data.dnsDomainPool,
                mtu = result.data.mtu,
                listenPort = result.data.listenPort,
            )

            is CommandResult.Failure -> null
        }
    }

    override fun deleteInterface(interfaceName: String) {
        callDaemon(operation = "deleteInterface", interfaceName = interfaceName) { daemon ->
            daemon.deleteInterface(interfaceName = interfaceName)
        }
    }

    override fun openPacketBridge(
        interfaceName: String,
        pipe: DuplexChannelPipe<ByteArray>,
        onFailure: (Throwable) -> Unit,
    ): AutoCloseable {
        val outgoingPackets = Channel<ByteArray>(capacity = DuplexChannelPipe.DEFAULT_CAPACITY)
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineName("kmpvpn-packet-rpc-bridge"),
        )
        val bridgeReady = CompletableDeferred<Unit>()
        val bridgeTerminated = CompletableDeferred<Throwable>()
        val startupConfirmed = AtomicBoolean(false)

        fun reportTermination(throwable: Throwable) {
            if (!bridgeTerminated.isCompleted) {
                bridgeTerminated.complete(throwable)
            }
            if (startupConfirmed.get()) {
                onFailure(throwable)
            }
        }

        scope.launch {
            try {
                bridgeReady.complete(Unit)
                client.packetIO(
                    interfaceName = interfaceName,
                    outgoingPackets = outgoingPackets.receiveAsFlow(),
                ).collect { packet ->
                    pipe.send(packet)
                }
                reportTermination(
                    IllegalStateException("Packet bridge closed by daemon for `$interfaceName`: stream completed"),
                )
            } catch (_: CancellationException) {
                // shutdown path
            } catch (throwable: Throwable) {
                if (!bridgeReady.isCompleted) {
                    bridgeReady.completeExceptionally(throwable)
                }
                reportTermination(throwable)
            }
        }

        scope.launch {
            try {
                while (true) {
                    outgoingPackets.send(pipe.receive())
                }
            } catch (_: CancellationException) {
                // shutdown path
            } catch (throwable: Throwable) {
                reportTermination(throwable)
            }
        }

        try {
            runBlocking {
                withTimeout(CONNECT_TIMEOUT_MILLIS) {
                    bridgeReady.await()
                }
                val startupFailure = withTimeoutOrNull(STARTUP_STABILITY_MILLIS) {
                    bridgeTerminated.await()
                }
                if (startupFailure != null) {
                    throw startupFailure
                }
            }
            startupConfirmed.set(true)
            if (bridgeTerminated.isCompleted) {
                onFailure(runBlocking { bridgeTerminated.await() })
            }
        } catch (throwable: Throwable) {
            scope.cancel("DaemonBackedInterfaceCommandExecutor packet bridge failed to connect")
            outgoingPackets.close()
            throw IllegalStateException(
                "Failed to open packet bridge for `$interfaceName`: ${throwable.message ?: "unknown"}",
                throwable,
            )
        }

        return AutoCloseable {
            scope.cancel("DaemonBackedInterfaceCommandExecutor packet bridge closed")
            outgoingPackets.close()
        }
    }

    private fun <S> callDaemon(
        operation: String,
        interfaceName: String,
        throwOnFailure: Boolean = true,
        rpcCall: suspend (DaemonProcessClient) -> CommandResult<S>,
    ): CommandResult<S> {
        val result = try {
            runBlocking {
                rpcCall(client)
            }
        } catch (throwable: Throwable) {
            throw IllegalStateException(
                "Daemon operation `$operation` failed to reach $host:$port: ${throwable.message ?: "unknown"}",
                throwable,
            )
        }
        if (throwOnFailure && result is CommandResult.Failure) {
            throw IllegalStateException(
                "Daemon operation `$operation` failed for `$interfaceName`: ${result.kind} ${result.message}",
            )
        }
        return result
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS: Long = 5_000
        const val STARTUP_STABILITY_MILLIS: Long = 200
    }
}

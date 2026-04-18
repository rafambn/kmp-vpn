package com.rafambn.wgkotlin.daemon.client

import com.rafambn.wgkotlin.daemon.protocol.CommandResult
import com.rafambn.wgkotlin.daemon.protocol.DEFAULT_DAEMON_HOST
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
import io.ktor.client.HttpClient
import java.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import org.koin.core.module.Module

class DaemonProcessClient(
    val service: DaemonProcessApi,
    val timeout: Duration = Duration.ofSeconds(15),
    val resourceCloser: () -> Unit = {}
) : DaemonProcessApi, AutoCloseable {

    companion object {
        internal fun create(
            config: DaemonClientConfig,
            overrideModules: List<Module> = emptyList(),
        ): DaemonProcessClient {
            val dependencies = DaemonClientKoinBootstrap.resolveDependencies(
                config = config,
                overrideModules = overrideModules,
            )

            return DaemonProcessClient(
                timeout = config.timeout,
                service = dependencies.service,
                resourceCloser = dependencies::close,
            )
        }
    }

    suspend fun handshake(timeout: Duration = Duration.ofSeconds(5)): CommandResult<PingResponse> {
        val response = callWithTimeout(timeout) { service.ping() }

        if (response is CommandResult.Failure) {
            throw DaemonClientException.ProtocolViolation(
                message = "Handshake failed: ${response.message}",
            )
        }

        return response
    }

    override suspend fun ping(): CommandResult<PingResponse> {
        return callWithTimeout(timeout) { service.ping() }
    }

    override suspend fun createInterface(interfaceName: String): CommandResult<CreateInterfaceResponse> {
        return callWithTimeout(timeout) {
            service.createInterface(interfaceName = interfaceName)
        }
    }

    override suspend fun interfaceExists(interfaceName: String): CommandResult<InterfaceExistsResponse> {
        return callWithTimeout(timeout) {
            service.interfaceExists(interfaceName = interfaceName)
        }
    }

    override suspend fun applyMtu(
        interfaceName: String,
        mtu: Int,
    ): CommandResult<ApplyMtuResponse> {
        return callWithTimeout(timeout) {
            service.applyMtu(interfaceName = interfaceName, mtu = mtu)
        }
    }

    override suspend fun applyAddresses(
        interfaceName: String,
        addresses: List<String>,
    ): CommandResult<ApplyAddressesResponse> {
        return callWithTimeout(timeout) {
            service.applyAddresses(interfaceName = interfaceName, addresses = addresses)
        }
    }

    override suspend fun applyRoutes(
        interfaceName: String,
        routes: List<String>,
    ): CommandResult<ApplyRoutesResponse> {
        return callWithTimeout(timeout) {
            service.applyRoutes(
                interfaceName = interfaceName,
                routes = routes,
            )
        }
    }

    override suspend fun applyDns(
        interfaceName: String,
        dnsDomainPool: Pair<List<String>, List<String>>,
    ): CommandResult<ApplyDnsResponse> {
        return callWithTimeout(timeout) {
            service.applyDns(interfaceName = interfaceName, dnsDomainPool = dnsDomainPool)
        }
    }

    override suspend fun readInterfaceInformation(
        interfaceName: String,
    ): CommandResult<ReadInterfaceInformationResponse> {
        return callWithTimeout(timeout) {
            service.readInterfaceInformation(interfaceName = interfaceName)
        }
    }

    override suspend fun deleteInterface(
        interfaceName: String,
    ): CommandResult<DeleteInterfaceResponse> {
        return callWithTimeout(timeout) {
            service.deleteInterface(interfaceName = interfaceName)
        }
    }

    override fun packetIO(
        interfaceName: String,
        outgoingPackets: Flow<ByteArray>,
    ): Flow<ByteArray> {
        return service.packetIO(interfaceName = interfaceName, outgoingPackets = outgoingPackets)
    }

    private suspend fun <D> callWithTimeout(
        timeout: Duration,
        call: suspend () -> CommandResult<D>,
    ): CommandResult<D> {
        if (timeout.toMillis() <= 0L) {
            throw IllegalArgumentException("Timeout must be positive")
        }

        return try {
            withTimeout(timeout.toMillis()) {
                call()
            }
        } catch (timeoutFailure: TimeoutCancellationException) {
            throw DaemonClientException.Timeout(
                timeout = timeout,
                cause = timeoutFailure,
            )
        }
    }

    override fun close() {
        resourceCloser()
    }
}

data class DaemonClientConfig(
    val host: String = DEFAULT_DAEMON_HOST,
    val port: Int,
    val timeout: Duration = Duration.ofSeconds(15),
) {
    init {
        require(host.isNotBlank()) { "Daemon host cannot be blank" }
        require(port in 1..65535) { "Daemon port must be between 1 and 65535" }
        require(timeout.toMillis() > 0L) { "Timeout must be positive" }
    }
}

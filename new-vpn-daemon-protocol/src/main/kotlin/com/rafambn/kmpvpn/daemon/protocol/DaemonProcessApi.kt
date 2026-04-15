package com.rafambn.kmpvpn.daemon.protocol

import com.rafambn.kmpvpn.daemon.protocol.response.ApplyAddressesResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyDnsResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyMtuResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ApplyRoutesResponse
import com.rafambn.kmpvpn.daemon.protocol.response.CreateInterfaceResponse
import com.rafambn.kmpvpn.daemon.protocol.response.DeleteInterfaceResponse
import com.rafambn.kmpvpn.daemon.protocol.response.InterfaceExistsResponse
import com.rafambn.kmpvpn.daemon.protocol.response.PingResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ReadInterfaceInformationResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc

/**
 * kRPC control-plane contract shared by daemon server and JVM client.
 *
 * Commands are intentionally split into dedicated RPC methods.
 * Primitive parameters are preferred, except for high-cardinality commands.
 *
 * Interface up/down is implicit: the daemon brings the interface up when the
 * packet stream connects and down when it disconnects.
 */
@Rpc
interface DaemonProcessApi {
    suspend fun ping(): CommandResult<PingResponse>

    suspend fun createInterface(
        interfaceName: String,
    ): CommandResult<CreateInterfaceResponse>

    suspend fun interfaceExists(
        interfaceName: String,
    ): CommandResult<InterfaceExistsResponse>

    suspend fun applyMtu(
        interfaceName: String,
        mtu: Int,
    ): CommandResult<ApplyMtuResponse>

    suspend fun applyAddresses(
        interfaceName: String,
        addresses: List<String>,
    ): CommandResult<ApplyAddressesResponse>

    suspend fun applyRoutes(
        interfaceName: String,
        routes: List<String>,
    ): CommandResult<ApplyRoutesResponse>

    suspend fun applyDns(
        interfaceName: String,
        dnsDomainPool: Pair<List<String>, List<String>>,
    ): CommandResult<ApplyDnsResponse>

    suspend fun readInterfaceInformation(
        interfaceName: String,
    ): CommandResult<ReadInterfaceInformationResponse>

    suspend fun deleteInterface(
        interfaceName: String,
    ): CommandResult<DeleteInterfaceResponse>

    fun packetIO(
        interfaceName: String,
        outgoingPackets: Flow<ByteArray>,
    ): Flow<ByteArray>
}

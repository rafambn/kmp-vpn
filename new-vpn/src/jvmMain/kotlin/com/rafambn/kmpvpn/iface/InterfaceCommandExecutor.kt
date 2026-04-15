package com.rafambn.kmpvpn.iface

import com.rafambn.kmpvpn.session.DuplexChannelPipe

/**
 * JVM boundary for privileged interface commands.
 *
 * Production implementations must proxy commands through the privileged daemon.
 * Peer/session state is intentionally excluded from this contract.
 *
 * Interface up/down is now implicit: [openPacketBridge] starts the packet bridge
 * (daemon brings interface up on packetIO connect), and [AutoCloseable.close] on
 * the returned handle stops it (daemon brings interface down when packetIO ends).
 */
interface InterfaceCommandExecutor {

    fun createInterface(interfaceName: String)

    fun interfaceExists(interfaceName: String): Boolean

    fun applyMtu(interfaceName: String, mtu: Int)

    fun applyAddresses(interfaceName: String, addresses: List<String>)

    fun applyRoutes(interfaceName: String, routes: List<String>)

    fun applyDns(interfaceName: String, dnsDomainPool: Pair<List<String>, List<String>>)

    fun readInformation(interfaceName: String): VpnInterfaceInformation?

    fun deleteInterface(interfaceName: String)

    /**
     * Opens the packetIO RPC bridge.
     * [pipe] is the JVM (interface) end; bridge coroutines read/write it.
     * Implementations must return only after bridge startup succeeds; otherwise throw.
     * Close the returned [AutoCloseable] to stop the bridge.
     */
    fun openPacketBridge(
        interfaceName: String,
        pipe: DuplexChannelPipe<ByteArray>,
        onFailure: (Throwable) -> Unit = {},
    ): AutoCloseable
}

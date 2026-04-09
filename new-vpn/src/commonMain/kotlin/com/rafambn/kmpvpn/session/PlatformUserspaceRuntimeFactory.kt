package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.iface.VpnPeerStats
import com.rafambn.kmpvpn.session.io.UdpPort

internal typealias UserspaceRuntimeFactory = (
    configuration: VpnConfiguration,
    listenPort: Int,
    pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
    peerStats: () -> List<VpnPeerStats>,
    onFailure: (Throwable) -> Unit,
) -> UserspaceRuntimeHandle?

internal interface UserspaceRuntimeHandle : AutoCloseable {
    fun isRunning(): Boolean

    fun peerStats(): List<VpnPeerStats>
}

internal expect object PlatformUserspaceRuntimeFactory {
    fun create(
        configuration: VpnConfiguration,
        listenPort: Int,
        pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
        peerStats: () -> List<VpnPeerStats>,
        onFailure: (Throwable) -> Unit,
    ): UserspaceRuntimeHandle?
}

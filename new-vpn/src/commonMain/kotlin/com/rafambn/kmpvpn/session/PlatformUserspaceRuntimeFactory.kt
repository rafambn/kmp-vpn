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

internal object PlatformUserspaceRuntimeFactory {
    fun create(
        configuration: VpnConfiguration,
        listenPort: Int,
        pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
        peerStats: () -> List<VpnPeerStats>,
        onFailure: (Throwable) -> Unit,
    ): UserspaceRuntimeHandle {
        return DefaultUserspaceRuntimeHandle(
            configuration = configuration,
            onFailure = onFailure,
            listenPort = listenPort,
            receiveTimeoutMillis = DEFAULT_RECEIVE_TIMEOUT_MILLIS,
            idleDelayMillis = DEFAULT_IDLE_DELAY_MILLIS,
            periodicIntervalMillis = DEFAULT_PERIODIC_INTERVAL_MILLIS,
            pollOnce = pollOnce,
            peerStats = peerStats,
        )
    }

    private const val DEFAULT_RECEIVE_TIMEOUT_MILLIS: Long = 50L
    private const val DEFAULT_IDLE_DELAY_MILLIS: Long = 10L
    private const val DEFAULT_PERIODIC_INTERVAL_MILLIS: Long = 100L
}

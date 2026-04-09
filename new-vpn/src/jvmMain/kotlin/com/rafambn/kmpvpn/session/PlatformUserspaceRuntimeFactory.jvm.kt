package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.iface.JvmPlatformProperties
import com.rafambn.kmpvpn.iface.VpnPeerStats
import com.rafambn.kmpvpn.session.io.UdpPort

internal object JvmPlatformUserspaceRuntimeFactory {
    fun create(
        configuration: VpnConfiguration,
        listenPort: Int,
        pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
        peerStats: () -> List<VpnPeerStats>,
        onFailure: (Throwable) -> Unit,
    ): UserspaceRuntimeHandle? {
        return when (
            System.getProperty(
                JvmPlatformProperties.RUNTIME_MODE,
                JvmPlatformProperties.RUNTIME_MODE_PRODUCTION,
            ).lowercase()
        ) {
            JvmPlatformProperties.RUNTIME_MODE_DISABLED -> null
            else -> createProduction(
                configuration = configuration,
                listenPort = listenPort,
                pollOnce = pollOnce,
                peerStats = peerStats,
                onFailure = onFailure,
            )
        }
    }

    fun createProduction(
        configuration: VpnConfiguration,
        listenPort: Int,
        pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
        peerStats: () -> List<VpnPeerStats>,
        onFailure: (Throwable) -> Unit,
    ): UserspaceRuntimeHandle {
        val receiveTimeoutMillis = System.getProperty(JvmPlatformProperties.RUNTIME_RECEIVE_TIMEOUT_MILLIS)?.toLongOrNull() ?: 50L
        val idleDelayMillis = System.getProperty(JvmPlatformProperties.RUNTIME_IDLE_DELAY_MILLIS)?.toLongOrNull() ?: 10L
        val periodicIntervalMillis = System.getProperty(JvmPlatformProperties.RUNTIME_PERIODIC_INTERVAL_MILLIS)?.toLongOrNull() ?: 100L

        return JvmUserspaceRuntimeHandle(
            configuration = configuration,
            onFailure = onFailure,
            listenPort = listenPort,
            receiveTimeoutMillis = receiveTimeoutMillis,
            idleDelayMillis = idleDelayMillis,
            periodicIntervalMillis = periodicIntervalMillis,
            pollOnce = pollOnce,
            peerStats = peerStats,
        )
    }
}

internal actual object PlatformUserspaceRuntimeFactory {
    actual fun create(
        configuration: VpnConfiguration,
        listenPort: Int,
        pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
        peerStats: () -> List<VpnPeerStats>,
        onFailure: (Throwable) -> Unit,
    ): UserspaceRuntimeHandle? {
        return JvmPlatformUserspaceRuntimeFactory.create(
            configuration = configuration,
            listenPort = listenPort,
            pollOnce = pollOnce,
            peerStats = peerStats,
            onFailure = onFailure,
        )
    }
}

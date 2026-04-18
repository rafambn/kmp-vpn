package com.rafambn.wgkotlin

import com.rafambn.wgkotlin.iface.InterfaceManager
import com.rafambn.wgkotlin.iface.VpnInterfaceInformation
import com.rafambn.wgkotlin.session.CryptoSessionManager
import com.rafambn.wgkotlin.session.DuplexChannelPipe
import com.rafambn.wgkotlin.session.SocketManager
import com.rafambn.wgkotlin.session.io.UdpDatagram

internal fun testVpn(
    configuration: VpnConfiguration,
    cryptoSessionManager: CryptoSessionManager? = null,
    socketManager: SocketManager? = null,
    interfaceManager: InterfaceManager? = null,
): Vpn {
    return Vpn(
        configuration = normalizedTestConfiguration(configuration),
        cryptoSessionManager = cryptoSessionManager,
        socketManager = socketManager,
        interfaceManager = interfaceManager,
    )
}

internal class MockSocketManager : SocketManager {
    var startCalls: Int = 0
    var stopCalls: Int = 0

    override fun start(listenPort: Int, networkPipe: DuplexChannelPipe<UdpDatagram>, onFailure: (Throwable) -> Unit) {
        startCalls++
    }

    override fun stop() {
        stopCalls++
    }

    override fun isRunning(): Boolean = false
}

internal class MockInterfaceManager(
    private var currentConfiguration: VpnConfiguration,
) : InterfaceManager {
    var startCalls: Int = 0
    var stopCalls: Int = 0
    var reconfigureCalls: Int = 0

    private var running: Boolean = false

    override fun isRunning(): Boolean = running

    override fun start(config: VpnConfiguration, onFailure: (Throwable) -> Unit) {
        startCalls++
        running = true
        currentConfiguration = snapshotConfiguration(config)
    }

    override fun stop() {
        stopCalls++
        running = false
    }

    override fun reconfigure(config: VpnConfiguration) {
        reconfigureCalls++
        currentConfiguration = snapshotConfiguration(config)
        running = true
    }

    override fun information(): VpnInterfaceInformation? {
        if (!running) {
            return null
        }

        return VpnInterfaceInformation(
            interfaceName = currentConfiguration.interfaceName,
            isUp = true,
            addresses = currentConfiguration.addresses.toList(),
            dns = currentConfiguration.dns,
            mtu = currentConfiguration.mtu,
            listenPort = currentConfiguration.listenPort,
        )
    }
}

internal fun snapshotConfiguration(config: VpnConfiguration): VpnConfiguration {
    return VpnConfiguration(
        interfaceName = config.interfaceName,
        dns = DnsConfig(
            searchDomains = config.dns.searchDomains.toList(),
            servers = config.dns.servers.toList(),
        ),
        mtu = config.mtu,
        addresses = config.addresses.toMutableList(),
        listenPort = config.listenPort,
        privateKey = config.privateKey,
        peers = config.peers.map { peer ->
            VpnPeer(
                endpointPort = peer.endpointPort,
                endpointAddress = peer.endpointAddress,
                publicKey = peer.publicKey,
                allowedIps = peer.allowedIps.toList(),
                persistentKeepalive = peer.persistentKeepalive,
                presharedKey = peer.presharedKey,
            )
        },
    )
}

private fun normalizedTestConfiguration(config: VpnConfiguration): VpnConfiguration {
    return if (config.listenPort != null) {
        config
    } else {
        snapshotConfiguration(config).copy(listenPort = 0)
    }
}

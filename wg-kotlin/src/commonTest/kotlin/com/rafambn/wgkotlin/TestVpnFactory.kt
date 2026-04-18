package com.rafambn.wgkotlin

import com.rafambn.wgkotlin.iface.InterfaceManager
import com.rafambn.wgkotlin.iface.VpnInterfaceInformation
import com.rafambn.wgkotlin.session.CryptoSessionManager
import com.rafambn.wgkotlin.session.DuplexChannelPipe
import com.rafambn.wgkotlin.session.SocketManager
import com.rafambn.wgkotlin.session.io.UdpDatagram
import com.rafambn.wgkotlin.session.io.UdpEndpoint

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
    var createCalls: Int = 0
    var upCalls: Int = 0
    var downCalls: Int = 0
    var deleteCalls: Int = 0
    var reconfigureCalls: Int = 0

    private var created: Boolean = false
    private var up: Boolean = false

    override fun exists(): Boolean = created

    override fun create(config: VpnConfiguration) {
        createCalls++
        created = true
        currentConfiguration = snapshotConfiguration(config)
    }

    override fun up(onBridgeFailure: (Throwable) -> Unit) {
        upCalls++
        up = true
    }

    override fun down() {
        downCalls++
        up = false
    }

    override fun delete() {
        deleteCalls++
        created = false
        up = false
    }

    override fun isUp(): Boolean = up

    override fun configuration(): VpnConfiguration = snapshotConfiguration(currentConfiguration)

    override fun reconfigure(config: VpnConfiguration) {
        reconfigureCalls++
        currentConfiguration = snapshotConfiguration(config)
    }

    override fun readInformation(): VpnInterfaceInformation {
        return VpnInterfaceInformation(
            interfaceName = currentConfiguration.interfaceName,
            isUp = up,
            addresses = currentConfiguration.addresses,
            dnsDomainPool = currentConfiguration.dnsDomainPool,
            mtu = currentConfiguration.mtu,
            listenPort = currentConfiguration.listenPort,
        )
    }
}

internal fun snapshotConfiguration(config: VpnConfiguration): VpnConfiguration {
    return VpnConfiguration(
        interfaceName = config.interfaceName,
        dnsDomainPool = config.dnsDomainPool.first.toList() to config.dnsDomainPool.second.toList(),
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

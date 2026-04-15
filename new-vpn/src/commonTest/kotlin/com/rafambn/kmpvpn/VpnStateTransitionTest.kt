package com.rafambn.kmpvpn

import com.rafambn.kmpvpn.iface.InterfaceManager
import com.rafambn.kmpvpn.iface.VpnInterfaceInformation
import com.rafambn.kmpvpn.iface.VpnPeerStats
import com.rafambn.kmpvpn.session.CryptoSessionManager
import com.rafambn.kmpvpn.session.CryptoSessionManagerImpl
import com.rafambn.kmpvpn.session.DuplexChannelPipe
import com.rafambn.kmpvpn.session.SocketManager
import com.rafambn.kmpvpn.session.io.UdpDatagram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VpnStateTransitionTest {

    private val privateKey = "oA8gY5Yg7R6pujISiFDUFxIr05o2IaNbS1Ry6j3TzXs="
    private val peerKey = "6fX3drXr/7L0KleChX2NDSSSXWMQZnIcXtNCmieYw0I="

    @Test
    fun lifecycleTransitionsFollowContract() {
        val vpn = testVpn(configuration = baseConfiguration(interfaceName = "utun130"))

        assertEquals(VpnState.NotCreated, vpn.state())

        vpn.create()
        assertEquals(VpnState.Created, vpn.state())

        vpn.start()
        assertEquals(VpnState.Running, vpn.state())

        vpn.stop()
        assertEquals(VpnState.Created, vpn.state())

        vpn.delete()
        assertEquals(VpnState.NotCreated, vpn.state())
    }

    @Test
    fun stopAndDeleteAreIdempotent() {
        val vpn = testVpn(configuration = baseConfiguration(interfaceName = "utun131"))

        vpn.stop()
        assertEquals(VpnState.NotCreated, vpn.state())

        vpn.delete()
        vpn.delete()
        assertEquals(VpnState.NotCreated, vpn.state())
    }

    @Test
    fun interfaceFailureDoesNotPersistSyntheticState() {
        val vpn = testVpn(
            configuration = baseConfiguration(interfaceName = "utun132"),
            interfaceManager = FailingUpInterfaceManager(),
        )

        assertFailsWith<IllegalStateException> {
            vpn.start()
        }

        assertEquals(VpnState.Created, vpn.state())
    }

    @Test
    fun startSkipsCreateWhenInterfaceAlreadyExists() {
        val configuration = baseConfiguration(interfaceName = "utun133")
        val interfaceManager = ExistingInterfaceManager(configuration)
        val vpn = testVpn(
            configuration = configuration,
            interfaceManager = interfaceManager,
        )

        vpn.start()

        assertTrue(interfaceManager.isUp())
        assertEquals(VpnState.Running, vpn.state())
    }

    @Test
    fun stopContinuesCleanupWhenDownFails() {
        val configuration = baseConfiguration(interfaceName = "utun134")
        val socketManager = RecordingSocketManager()
        val cryptoSessionManager = RecordingCryptoSessionManager()
        val vpn = testVpn(
            configuration = configuration,
            cryptoSessionManager = cryptoSessionManager,
            socketManager = socketManager,
            interfaceManager = DownFailingInterfaceManager(configuration),
        )

        assertFailsWith<IllegalStateException> {
            vpn.stop()
        }

        assertEquals(1, socketManager.stopCalls)
        assertEquals(1, cryptoSessionManager.stopCalls)
    }

    @Test
    fun deleteContinuesCleanupAndDeleteWhenDownFails() {
        val configuration = baseConfiguration(interfaceName = "utun135")
        val socketManager = RecordingSocketManager()
        val cryptoSessionManager = RecordingCryptoSessionManager()
        val interfaceManager = DownFailingInterfaceManager(configuration)
        val vpn = testVpn(
            configuration = configuration,
            cryptoSessionManager = cryptoSessionManager,
            socketManager = socketManager,
            interfaceManager = interfaceManager,
        )

        assertFailsWith<IllegalStateException> {
            vpn.delete()
        }

        assertEquals(1, socketManager.stopCalls)
        assertEquals(1, cryptoSessionManager.stopCalls)
        assertEquals(1, interfaceManager.deleteCalls)
    }

    private fun baseConfiguration(interfaceName: String): VpnConfiguration {
        return VpnConfiguration(
            interfaceName = interfaceName,
            listenPort = 0,
            privateKey = privateKey,
            peers = listOf(VpnPeer(publicKey = peerKey, endpointAddress = "198.51.100.1", endpointPort = 51820)),
        )
    }

    private class FailingUpInterfaceManager(
        private val tunPipe: DuplexChannelPipe<ByteArray> = DuplexChannelPipe.create<ByteArray>().first,
    ) : InterfaceManager {
        private var created: Boolean = false
        private var currentConfiguration: VpnConfiguration? = null

        override fun exists(): Boolean = created

        override fun create(config: VpnConfiguration) {
            created = true
            currentConfiguration = config
        }

        override fun up(onBridgeFailure: (Throwable) -> Unit) {
            error("boom")
        }

        override fun down() {
            // no-op
        }

        override fun delete() {
            created = false
            currentConfiguration = null
        }

        override fun isUp(): Boolean = false

        override fun configuration(): VpnConfiguration {
            return checkNotNull(currentConfiguration)
        }

        override fun reconfigure(config: VpnConfiguration) {
            currentConfiguration = config
        }

        override fun readInformation(): VpnInterfaceInformation {
            return VpnInterfaceInformation(
                interfaceName = "utun132",
                isUp = false,
                addresses = emptyList(),
                dnsDomainPool = (emptyList<String>() to emptyList()),
                mtu = null,
                listenPort = null,
            )
        }
    }

    private class ExistingInterfaceManager(
        private var currentConfiguration: VpnConfiguration,
        private val tunPipe: DuplexChannelPipe<ByteArray> = DuplexChannelPipe.create<ByteArray>().first,
    ) : InterfaceManager {
        private var up: Boolean = false

        override fun exists(): Boolean = true

        override fun create(config: VpnConfiguration) {
            error("create should not be called when the interface already exists")
        }

        override fun up(onBridgeFailure: (Throwable) -> Unit) {
            up = true
        }

        override fun down() {
            up = false
        }

        override fun delete() {
            up = false
        }

        override fun isUp(): Boolean = up

        override fun configuration(): VpnConfiguration = currentConfiguration

        override fun reconfigure(config: VpnConfiguration) {
            currentConfiguration = config
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

    private class DownFailingInterfaceManager(
        private var currentConfiguration: VpnConfiguration,
        private val tunPipe: DuplexChannelPipe<ByteArray> = DuplexChannelPipe.create<ByteArray>().first,
    ) : InterfaceManager {
        var deleteCalls: Int = 0
            private set

        override fun exists(): Boolean = true
        override fun create(config: VpnConfiguration) {}
        override fun up(onBridgeFailure: (Throwable) -> Unit) {}

        override fun down() {
            error("down failed")
        }

        override fun delete() {
            deleteCalls++
        }

        override fun isUp(): Boolean = false
        override fun configuration(): VpnConfiguration = currentConfiguration

        override fun reconfigure(config: VpnConfiguration) {
            currentConfiguration = config
        }

        override fun readInformation(): VpnInterfaceInformation {
            return VpnInterfaceInformation(
                interfaceName = currentConfiguration.interfaceName,
                isUp = false,
                addresses = currentConfiguration.addresses,
                dnsDomainPool = currentConfiguration.dnsDomainPool,
                mtu = currentConfiguration.mtu,
                listenPort = currentConfiguration.listenPort,
            )
        }
    }

    private class RecordingSocketManager : SocketManager {
        var stopCalls: Int = 0
            private set

        override fun start(listenPort: Int, networkPipe: DuplexChannelPipe<UdpDatagram>, onFailure: (Throwable) -> Unit) {}

        override fun stop() {
            stopCalls++
        }

        override fun isRunning(): Boolean = false
    }

    private class RecordingCryptoSessionManager : CryptoSessionManager {
        var stopCalls: Int = 0
            private set

        override fun reconcileSessions(config: VpnConfiguration) {}

        override fun start(
            tunPipe: DuplexChannelPipe<ByteArray>,
            networkPipe: DuplexChannelPipe<UdpDatagram>,
            onFailure: (Throwable) -> Unit,
        ) {
        }

        override fun stop() {
            stopCalls++
        }

        override fun peerStats(): List<VpnPeerStats> = emptyList()
        override fun hasActiveSessions(): Boolean = false
    }
}

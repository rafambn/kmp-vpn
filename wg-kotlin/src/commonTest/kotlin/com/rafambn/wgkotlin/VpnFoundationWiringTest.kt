package com.rafambn.wgkotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VpnFoundationWiringTest {

    private val privateKey = "oA8gY5Yg7R6pujISiFDUFxIr05o2IaNbS1Ry6j3TzXs="
    private val publicKey = "V6w5nNq2WEYLRh3SeDsICoZ6irMIXja+6JGZveHFk/Q="
    private val peerKey = "6fX3drXr/7L0KleChX2NDSSSXWMQZnIcXtNCmieYw0I="

    @Test
    fun inMemoryLifecycleStillWorks() {
        val vpn = testVpn(
            configuration = VpnConfiguration(
                interfaceName = "utun120",
                privateKey = privateKey,
                peers = listOf(VpnPeer(publicKey = peerKey, endpointAddress = "198.51.100.1", endpointPort = 51820)),
            ),
        )

        assertEquals(VpnState.Stopped, vpn.state())

        vpn.start()
        assertEquals(VpnState.Running, vpn.state())

        vpn.stop()
        assertEquals(VpnState.Stopped, vpn.state())
        assertNull(vpn.information())
    }

    @Test
    fun repeatedStartKeepsVpnRunning() {
        val vpn = testVpn(
            configuration = VpnConfiguration(
                interfaceName = "utun121",
                privateKey = privateKey,
                peers = listOf(VpnPeer(publicKey = peerKey, endpointAddress = "198.51.100.1", endpointPort = 51820)),
            ),
        )

        vpn.start()
        vpn.start()

        assertEquals(VpnState.Running, vpn.state())
    }

    @Test
    fun reconfigureAllowsFullConfigurationUpdate() {
        val vpn = testVpn(
            configuration = VpnConfiguration(
                interfaceName = "utun122",
                dns = DnsConfig(searchDomains = listOf("corp.local"), servers = listOf("1.1.1.1")),
                privateKey = privateKey,
                peers = listOf(VpnPeer(publicKey = peerKey, endpointAddress = "198.51.100.1", endpointPort = 51820)),
            ),
        )

        vpn.start()

        vpn.reconfigure(
            VpnConfiguration(
                interfaceName = "utun122",
                dns = DnsConfig(searchDomains = listOf("corp.local"), servers = listOf("9.9.9.9")),
                addresses = mutableListOf("10.20.30.2/32"),
                privateKey = privateKey,
                peers = listOf(VpnPeer(publicKey = publicKey, endpointAddress = "198.51.100.2", endpointPort = 51821)),
            ),
        )

        val current = requireNotNull(vpn.information()).vpnConfiguration
        requireNotNull(current)

        assertEquals(DnsConfig(searchDomains = listOf("corp.local"), servers = listOf("9.9.9.9")), current.dns)
        assertEquals(listOf("10.20.30.2/32"), current.addresses)
        assertEquals(listOf(publicKey), current.peers.map { peer -> peer.publicKey })
    }
}

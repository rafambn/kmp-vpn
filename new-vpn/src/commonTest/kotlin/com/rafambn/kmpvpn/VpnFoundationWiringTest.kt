package com.rafambn.kmpvpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnFoundationWiringTest {

    @Test
    fun inMemoryLifecycleStillWorks() {
        val vpn = Vpn(
            vpnConfiguration = DefaultVpnConfiguration(
                interfaceName = "wg0",
                privateKey = "private-key",
                publicKey = "public-key",
                peers = listOf(VpnPeer(publicKey = "peer-wg0"))
            )
        )

        assertFalse(vpn.exists())

        vpn.create()
        assertTrue(vpn.exists())

        vpn.start()
        assertTrue(vpn.isRunning())

        vpn.stop()
        assertFalse(vpn.isRunning())

        vpn.delete()
        assertFalse(vpn.exists())
    }

    @Test
    fun createRemainsIdempotent() {
        val vpn = Vpn(
            vpnConfiguration = DefaultVpnConfiguration(
                interfaceName = "wg1",
                privateKey = "private-key",
                publicKey = "public-key",
                peers = listOf(VpnPeer(publicKey = "peer-wg1"))
            )
        )

        val first = vpn.create()
        val second = vpn.create()

        assertTrue(first === second)
    }

    @Test
    fun reconfigureAllowsFullConfigurationUpdate() {
        val vpn = Vpn(
            vpnConfiguration = DefaultVpnConfiguration(
                interfaceName = "wg2",
                dns = mutableListOf("1.1.1.1"),
                privateKey = "private-key",
                publicKey = "public-key",
                peers = listOf(VpnPeer(publicKey = "peer-wg2-a")),
            ),
        )

        vpn.start()

        vpn.reconfigure(
            DefaultVpnConfiguration(
                interfaceName = "wg2",
                dns = mutableListOf("9.9.9.9"),
                addresses = mutableListOf("10.20.30.2/32"),
                privateKey = "private-key",
                publicKey = "public-key",
                peers = listOf(VpnPeer(publicKey = "peer-wg2-b")),
            ),
        )

        val current = vpn.configuration()

        assertEquals(listOf("9.9.9.9"), current.dns)
        assertEquals(listOf("10.20.30.2/32"), current.addresses)
        assertEquals(listOf("peer-wg2-b"), current.adapter.peers.map { peer -> peer.publicKey })
    }
}

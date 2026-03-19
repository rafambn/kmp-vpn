package com.rafambn.kmpvpn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnFoundationWiringTest {

    @Test
    fun inMemoryLifecycleStillWorks() {
        val vpn = Vpn(
            vpnConfiguration = DefaultVpnConfiguration(
                interfaceName = "wg0",
                privateKey = "private-key",
                publicKey = "public-key"
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
                publicKey = "public-key"
            )
        )

        val first = vpn.create()
        val second = vpn.create()

        assertTrue(first === second)
    }
}

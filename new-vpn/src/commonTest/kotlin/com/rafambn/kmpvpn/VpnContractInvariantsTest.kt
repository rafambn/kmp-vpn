package com.rafambn.kmpvpn

import kotlin.test.Test
import kotlin.test.assertFailsWith

class VpnContractInvariantsTest {

    @Test
    fun rejectsBlankInterfaceName() {
        assertFailsWith<IllegalArgumentException> {
            Vpn(
                vpnConfiguration = DefaultVpnConfiguration(
                    interfaceName = " ",
                    privateKey = "private-key",
                    publicKey = "public-key",
                ),
            )
        }
    }

    @Test
    fun rejectsDuplicatePeerPublicKeysOnConstruction() {
        val duplicatedPeers = listOf(
            VpnPeer(publicKey = "peer-a"),
            VpnPeer(publicKey = "peer-a"),
        )

        assertFailsWith<IllegalArgumentException> {
            Vpn(
                vpnConfiguration = DefaultVpnConfiguration(
                    interfaceName = "wg0",
                    privateKey = "private-key",
                    publicKey = "public-key",
                    peers = duplicatedPeers,
                ),
            )
        }
    }

    @Test
    fun reconfigureRejectsDuplicatedPeerPublicKeys() {
        val vpn = Vpn(
            vpnConfiguration = DefaultVpnConfiguration(
                interfaceName = "wg0",
                privateKey = "private-key",
                publicKey = "public-key",
                peers = listOf(VpnPeer(publicKey = "peer-a")),
            ),
        )

        vpn.create()

        assertFailsWith<IllegalArgumentException> {
            vpn.reconfigure(
                DefaultVpnConfiguration(
                    interfaceName = "wg0",
                    privateKey = "private-key",
                    publicKey = "public-key",
                    peers = listOf(
                        VpnPeer(publicKey = "peer-a"),
                        VpnPeer(publicKey = "peer-a"),
                    ),
                ),
            )
        }
    }

    @Test
    fun reconfigureRejectsInterfaceNameChange() {
        val vpn = Vpn(
            vpnConfiguration = DefaultVpnConfiguration(
                interfaceName = "wg0",
                privateKey = "private-key",
                publicKey = "public-key",
            ),
        )

        vpn.create()

        assertFailsWith<IllegalArgumentException> {
            vpn.reconfigure(
                DefaultVpnConfiguration(
                    interfaceName = "wg1",
                    privateKey = "private-key",
                    publicKey = "public-key",
                ),
            )
        }
    }
}

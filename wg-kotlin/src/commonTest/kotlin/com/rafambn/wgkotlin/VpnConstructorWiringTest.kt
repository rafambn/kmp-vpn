package com.rafambn.wgkotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class VpnConstructorWiringTest {

    private val privateKey = "oA8gY5Yg7R6pujISiFDUFxIr05o2IaNbS1Ry6j3TzXs="
    private val peerKey = "6fX3drXr/7L0KleChX2NDSSSXWMQZnIcXtNCmieYw0I="

    @Test
    fun secondaryConstructorBuildsIndependentInstances() {
        val first = Vpn(configuration(interfaceName = "utun101"))
        val second = Vpn(configuration(interfaceName = "utun102"))

        assertNotSame(first, second)
        assertEquals(VpnState.Stopped, first.state())
        assertEquals(VpnState.Stopped, second.state())

        first.start()

        assertEquals(VpnState.Running, first.state())
        assertEquals(VpnState.Stopped, second.state())
    }

    @Test
    fun explicitEngineStillSupportsLifecycle() {
        val vpn = Vpn(
            configuration = configuration(interfaceName = "utun103"),
            engine = Engine.BORINGTUN,
        )

        vpn.start()
        assertEquals(VpnState.Running, vpn.state())

        vpn.stop()
        assertEquals(VpnState.Stopped, vpn.state())
    }

    private fun configuration(interfaceName: String): VpnConfiguration {
        return VpnConfiguration(
            interfaceName = interfaceName,
            listenPort = when (interfaceName) {
                "utun101" -> 52101
                "utun102" -> 52102
                "utun103" -> 52103
                else -> 52100
            },
            privateKey = privateKey,
            peers = listOf(
                VpnPeer(
                    publicKey = peerKey,
                    endpointAddress = "198.51.100.1",
                    endpointPort = 51820,
                ),
            ),
        )
    }
}

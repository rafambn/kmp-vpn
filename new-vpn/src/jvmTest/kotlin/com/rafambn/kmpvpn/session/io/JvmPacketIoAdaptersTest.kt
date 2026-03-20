package com.rafambn.kmpvpn.session.io

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class JvmPacketIoAdaptersTest {

    @Test
    fun ktorDatagramUdpPortRoutesPacketsOverSocket() = runTest {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socketA = aSocket(selectorManager).udp().bind(InetSocketAddress("127.0.0.1", 0))
        val socketB = aSocket(selectorManager).udp().bind(InetSocketAddress("127.0.0.1", 0))

        try {
            val portA = KtorDatagramUdpPort(
                socket = socketA,
                remoteAddress = socketB.localAddress,
                receiveTimeoutMillis = 1_000L,
            )
            val portB = KtorDatagramUdpPort(
                socket = socketB,
                remoteAddress = socketA.localAddress,
                receiveTimeoutMillis = 1_000L,
            )

            portA.sendPacket(byteArrayOf(7, 8, 9))
            val received = portB.receivePacket()

            assertNotNull(received)
            assertContentEquals(byteArrayOf(7, 8, 9), received)
        } finally {
            socketA.close()
            socketB.close()
            selectorManager.close()
        }
    }
}

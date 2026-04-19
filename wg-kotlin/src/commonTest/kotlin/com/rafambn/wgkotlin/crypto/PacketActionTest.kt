package com.rafambn.wgkotlin.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PacketActionTest {

    @Test
    fun writeToNetworkUsesByteArrayContentForEquality() {
        val first = PacketAction.WriteToNetwork(byteArrayOf(0x01, 0x02))
        val second = PacketAction.WriteToNetwork(byteArrayOf(0x01, 0x02))
        val different = PacketAction.WriteToNetwork(byteArrayOf(0x02, 0x03))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }

    @Test
    fun writeToTunIpv4UsesByteArrayContentForEquality() {
        val first = PacketAction.WriteToTunIpv4(byteArrayOf(0x01, 0x02))
        val second = PacketAction.WriteToTunIpv4(byteArrayOf(0x01, 0x02))
        val different = PacketAction.WriteToTunIpv4(byteArrayOf(0x02, 0x03))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }

    @Test
    fun writeToTunIpv6UsesByteArrayContentForEquality() {
        val first = PacketAction.WriteToTunIpv6(byteArrayOf(0x01, 0x02))
        val second = PacketAction.WriteToTunIpv6(byteArrayOf(0x01, 0x02))
        val different = PacketAction.WriteToTunIpv6(byteArrayOf(0x02, 0x03))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }
}

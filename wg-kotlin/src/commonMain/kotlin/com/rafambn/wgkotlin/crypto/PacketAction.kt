package com.rafambn.wgkotlin.crypto

sealed class PacketAction {
    data object Done : PacketAction()

    class WriteToNetwork(val packet: ByteArray) : PacketAction() {
        override fun equals(other: Any?): Boolean =
            other is WriteToNetwork && packet.contentEquals(other.packet)

        override fun hashCode(): Int = packet.contentHashCode()

        override fun toString(): String = "WriteToNetwork(packet=${packet.contentToString()})"
    }

    class WriteToTunIpv4(val packet: ByteArray) : PacketAction() {
        override fun equals(other: Any?): Boolean =
            other is WriteToTunIpv4 && packet.contentEquals(other.packet)

        override fun hashCode(): Int = packet.contentHashCode()

        override fun toString(): String = "WriteToTunIpv4(packet=${packet.contentToString()})"
    }

    class WriteToTunIpv6(val packet: ByteArray) : PacketAction() {
        override fun equals(other: Any?): Boolean =
            other is WriteToTunIpv6 && packet.contentEquals(other.packet)

        override fun hashCode(): Int = packet.contentHashCode()

        override fun toString(): String = "WriteToTunIpv6(packet=${packet.contentToString()})"
    }

    data class Error(val code: UInt) : PacketAction()

    data class NotSupported(val operation: String) : PacketAction()
}

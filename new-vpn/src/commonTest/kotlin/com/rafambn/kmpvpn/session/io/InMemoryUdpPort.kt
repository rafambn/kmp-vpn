package com.rafambn.kmpvpn.session.io

class InMemoryUdpPort(
    private val incomingPackets: ArrayDeque<ByteArray> = ArrayDeque(),
) : UdpPort {
    val sentPackets: MutableList<ByteArray> = mutableListOf()

    override suspend fun receivePacket(): ByteArray? {
        return incomingPackets.removeFirstOrNull()?.copyOf()
    }

    override suspend fun sendPacket(packet: ByteArray) {
        sentPackets += packet.copyOf()
    }
}

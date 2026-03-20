package com.rafambn.kmpvpn.session.io

class InMemoryTunPort(
    private val incomingPackets: ArrayDeque<ByteArray> = ArrayDeque(),
) : TunPort {
    val writtenPackets: MutableList<ByteArray> = mutableListOf()

    override suspend fun readPacket(): ByteArray? {
        return incomingPackets.removeFirstOrNull()?.copyOf()
    }

    override suspend fun writePacket(packet: ByteArray) {
        writtenPackets += packet.copyOf()
    }
}

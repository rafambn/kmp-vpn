package com.rafambn.kmpvpn.session.io

interface UdpPort {
    suspend fun receivePacket(): ByteArray?

    suspend fun sendPacket(packet: ByteArray)
}

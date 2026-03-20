package com.rafambn.kmpvpn.session.io

interface TunPort {
    suspend fun readPacket(): ByteArray?

    suspend fun writePacket(packet: ByteArray)
}

package com.rafambn.kmpvpn.daemon.tun

/**
 * Live packet handle for one prepared interface.
 *
 * Implementations bridge the daemon packet stream to the actual interface packet
 * source/sink. The current stub implementation is used until platform-specific
 * TUN backends land.
 */
internal interface TunHandle : AutoCloseable {
    val interfaceName: String

    suspend fun readPacket(): ByteArray?

    suspend fun writePacket(packet: ByteArray)
}

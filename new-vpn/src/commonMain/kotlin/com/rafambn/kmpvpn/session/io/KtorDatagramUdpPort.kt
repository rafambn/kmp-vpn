package com.rafambn.kmpvpn.session.io

import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.SocketAddress
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ktor-based UDP adapter that can run from common code.
 */
class KtorDatagramUdpPort(
    private val socket: BoundDatagramSocket,
    private val remoteAddress: SocketAddress? = null,
    private val receiveTimeoutMillis: Long? = null,
) : UdpPort {
    init {
        receiveTimeoutMillis?.let { timeout ->
            require(timeout > 0L) {
                "receiveTimeoutMillis must be greater than zero when provided"
            }
        }
    }

    override suspend fun receivePacket(): ByteArray? {
        val received = if (receiveTimeoutMillis == null) {
            socket.receive()
        } else {
            withTimeoutOrNull(receiveTimeoutMillis) {
                socket.receive()
            }
        } ?: return null

        return received.packet.readBytes()
    }

    override suspend fun sendPacket(packet: ByteArray) {
        val destination = remoteAddress
            ?: throw IllegalStateException("Cannot send packet without a remote address")

        socket.send(
            Datagram(
                packet = ByteReadPacket(packet.copyOf()),
                address = destination,
            ),
        )
    }
}

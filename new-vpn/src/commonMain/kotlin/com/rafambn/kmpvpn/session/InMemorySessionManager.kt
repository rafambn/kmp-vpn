package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnAdapterConfiguration
import com.rafambn.kmpvpn.VpnPeer
import com.rafambn.kmpvpn.requireUniquePeerPublicKeys

internal class InMemorySessionManager : SessionManager {
    private val sessionsByPeer: LinkedHashMap<String, SessionSnapshot> = linkedMapOf()

    override fun ensureSessions(config: VpnAdapterConfiguration) {
        requireUniquePeerPublicKeys(config.peers)

        sessionsByPeer.clear()
        config.peers.forEach { peer ->
            sessionsByPeer[peer.publicKey] = peer.asSessionSnapshot(isActive = true)
        }
    }

    override fun reconcileSessions(config: VpnAdapterConfiguration) {
        requireUniquePeerPublicKeys(config.peers)

        val previousSessions = sessionsByPeer.toMap()
        sessionsByPeer.clear()

        config.peers.forEach { peer ->
            val previous = previousSessions[peer.publicKey]
            val isActive = previous?.isActive ?: false
            sessionsByPeer[peer.publicKey] = peer.asSessionSnapshot(isActive = isActive)
        }
    }

    override fun sessions(): List<SessionSnapshot> {
        return sessionsByPeer.values.toList()
    }

    override fun session(peerKey: String): SessionSnapshot? {
        return sessionsByPeer[peerKey]
    }

    override fun closeAll() {
        sessionsByPeer.replaceAll { _, snapshot ->
            snapshot.copy(isActive = false)
        }
    }

    private fun VpnPeer.asSessionSnapshot(isActive: Boolean): SessionSnapshot {
        return SessionSnapshot(
            peerPublicKey = publicKey,
            endpointAddress = endpointAddress,
            endpointPort = endpointPort,
            allowedIps = allowedIps,
            isActive = isActive
        )
    }
}

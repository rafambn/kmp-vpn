package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnAdapterConfiguration

/**
 * Contract for transport session ownership used by [com.rafambn.kmpvpn.Vpn].
 */
interface SessionManager {

    /**
     * Ensures sessions are started for all peers in the current configuration.
     */
    fun ensureSessions(config: VpnAdapterConfiguration)

    /**
     * Reconciles existing sessions with the desired configuration.
     */
    fun reconcileSessions(config: VpnAdapterConfiguration)

    /**
     * Returns snapshots for all known sessions.
     */
    fun sessions(): List<SessionSnapshot>

    /**
     * Returns one session by peer public key, or `null` if missing.
     */
    fun session(peerKey: String): SessionSnapshot?

    /**
     * Closes all known sessions.
     */
    fun closeAll()
}

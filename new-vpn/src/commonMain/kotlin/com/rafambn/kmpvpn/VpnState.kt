package com.rafambn.kmpvpn

/**
 * Observed orchestrator lifecycle state for a VPN interface.
 *
 * This state is derived from live interface/session observations and does not
 * persist synthetic lifecycle markers between operations.
 */
sealed class VpnState {

    /**
     * Interface is not currently present in the system.
     */
    data object NotCreated : VpnState()

    /**
     * Interface exists and is configured, but not running.
     */
    data class Created(val interfaceName: String) : VpnState()

    /**
     * Interface exists and has active sessions while being up.
     */
    data class Running(val interfaceName: String) : VpnState()
}

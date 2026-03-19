package com.rafambn.kmpvpn

/**
 * Stream model for VPN lifecycle telemetry events.
 */
sealed class VpnEvent {

    /**
     * Alert event for non-fatal conditions.
     */
    data class Alert(
        val message: String,
    ) : VpnEvent()

    /**
     * Failure event carrying a human-readable failure message.
     */
    data class Failure(
        val message: String,
    ) : VpnEvent()
}

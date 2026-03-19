package com.rafambn.kmpvpn

/**
 * Represents a VPN peer configuration
 */
data class VpnPeer(
    val endpointPort: Int? = null,
    val endpointAddress: String? = null,
    val publicKey: String,
    val allowedIps: List<String> = emptyList(),
    val persistentKeepalive: Int? = null,
    val presharedKey: String? = null
)

package com.rafambn.wgkotlin

/**
 * Full VPN configuration consumed by the orchestrator.
 */
data class VpnConfiguration(
    val interfaceName: String,
    val dns: DnsConfig = DnsConfig(),
    val mtu: Int? = null,
    val addresses: MutableList<String> = mutableListOf(),
    val listenPort: Int? = null,
    val privateKey: String, // Keys.genkey().getBase64PrivateKey()
    val peers: List<VpnPeer> = emptyList(),
)

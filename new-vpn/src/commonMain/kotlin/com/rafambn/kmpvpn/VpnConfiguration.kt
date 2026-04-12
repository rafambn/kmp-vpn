package com.rafambn.kmpvpn

/**
 * Full VPN configuration consumed by the orchestrator.
 */
data class VpnConfiguration(
    val interfaceName: String,
    val dnsDomainPool: Pair<List<String>, List<String>> = (emptyList<String>() to emptyList()),
    val mtu: Int? = null,
    val addresses: MutableList<String> = mutableListOf(),
    val listenPort: Int? = null,
    val privateKey: String, // Keys.genkey().getBase64PrivateKey()
    val peers: List<VpnPeer> = emptyList(),
)

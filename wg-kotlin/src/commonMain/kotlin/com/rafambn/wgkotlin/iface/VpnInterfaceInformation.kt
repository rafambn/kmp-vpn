package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.VpnConfiguration

/**
 * Read-only interface information returned by [InterfaceManager.readInformation].
 */
data class VpnInterfaceInformation(
    val interfaceName: String,
    val isUp: Boolean,
    val addresses: List<String>,
    val dnsDomainPool: Pair<List<String>, List<String>>,
    val mtu: Int?,
    val listenPort: Int? = null,
    val peerStats: List<VpnPeerStats> = emptyList(),
    val vpnConfiguration: VpnConfiguration? = null,
)

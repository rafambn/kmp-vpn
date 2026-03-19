package com.rafambn.kmpvpn.platform.iface

/**
 * Read-only interface information returned by [VpnInterface.readInformation].
 */
data class VpnInterfaceInformation(
    val interfaceName: String,
    val isUp: Boolean,
    val addresses: List<String>,
    val dnsServers: List<String>,
    val mtu: Int?,
)

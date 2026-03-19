package com.rafambn.kmpvpn

/**
 * Full VPN configuration consumed by the orchestrator.
 */
interface VpnConfiguration {

    val interfaceName: String

    val dns: MutableList<String>

    val mtu: Int?

    val addresses: MutableList<String>

    val table: String?

    val saveConfig: Boolean

    val adapter: VpnAdapterConfiguration

}

package com.rafambn.kmpvpn


interface VpnConfiguration : VpnAdapterConfiguration {

    val interfaceName: String

    val dns: MutableList<String>

    val mtu: Int?

    val addresses: MutableList<String>

    val table: String?

    val saveConfig: Boolean

}

class DefaultVpnConfiguration(
    override val interfaceName: String,
    override val dns: MutableList<String> = mutableListOf(),
    override val mtu: Int? = null,
    override val addresses: MutableList<String> = mutableListOf(),
    override val table: String? = null,
    override val saveConfig: Boolean = false,
    override val listenPort: Int? = null,
    override val privateKey: String, // Keys.genkey().getBase64PrivateKey()
    override val publicKey: String,
    override val fwMark: Int? = null,
    override val peers: List<VpnPeer> = emptyList()
) : DefaultVpnAdapterConfiguration(
    listenPort,
    privateKey,
    publicKey,
    fwMark,
    peers
), VpnConfiguration

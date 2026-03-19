package com.rafambn.kmpvpn

class DefaultVpnConfiguration(
    override val interfaceName: String,
    override val dns: MutableList<String> = mutableListOf(),
    override val mtu: Int? = null,
    override val addresses: MutableList<String> = mutableListOf(),
    override val table: String? = null,
    override val saveConfig: Boolean = false,
    override val adapter: VpnAdapterConfiguration,
) : VpnConfiguration {

    constructor(
        interfaceName: String,
        dns: MutableList<String> = mutableListOf(),
        mtu: Int? = null,
        addresses: MutableList<String> = mutableListOf(),
        table: String? = null,
        saveConfig: Boolean = false,
        listenPort: Int? = null,
        privateKey: String, // Keys.genkey().getBase64PrivateKey()
        publicKey: String,
        fwMark: Int? = null,
        peers: List<VpnPeer> = emptyList(),
    ) : this(
        interfaceName = interfaceName,
        dns = dns,
        mtu = mtu,
        addresses = addresses,
        table = table,
        saveConfig = saveConfig,
        adapter = DefaultVpnAdapterConfiguration(
            listenPort = listenPort,
            privateKey = privateKey,
            publicKey = publicKey,
            fwMark = fwMark,
            peers = peers,
        ),
    )
}

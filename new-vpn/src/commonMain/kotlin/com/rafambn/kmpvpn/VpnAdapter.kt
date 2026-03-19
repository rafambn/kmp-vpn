package com.rafambn.kmpvpn

/**
 * Minimal adapter model retained for refactoring the public VPN API.
 */
class VpnAdapter(
    val interfaceName: String,
    initialConfiguration: VpnAdapterConfiguration
) : AutoCloseable {
    private val allows: MutableList<String> = mutableListOf()
    private var currentConfiguration: VpnAdapterConfiguration = initialConfiguration
    private var running: Boolean = false

    fun configuration(): VpnAdapterConfiguration {
        return currentConfiguration
    }

    fun reconfigure(cfg: VpnAdapterConfiguration) {
        currentConfiguration = cfg
    }

    fun append(cfg: VpnAdapterConfiguration) {
        currentConfiguration = currentConfiguration.mergeWith(cfg)
    }

    fun sync(cfg: VpnAdapterConfiguration) {
        reconfigure(cfg)
    }

    fun allows(): MutableList<String> {
        return allows
    }

    fun isRunning(): Boolean {
        return running
    }

    fun start() {
        running = true
    }

    fun stop() {
        running = false
    }

    fun delete() {
        running = false
        allows.clear()
    }

    fun remove(publicKey: String) {
        val remainingPeers = currentConfiguration.peers.filterNot { it.publicKey == publicKey }
        currentConfiguration = currentConfiguration.copyWith(peers = remainingPeers)
    }

    override fun close() {
        delete()
    }

    private fun VpnAdapterConfiguration.mergeWith(
        update: VpnAdapterConfiguration
    ): VpnAdapterConfiguration {
        return copyWith(
            listenPort = update.listenPort ?: listenPort,
            privateKey = update.privateKey.ifBlank { privateKey },
            publicKey = update.publicKey.ifBlank { publicKey },
            fwMark = update.fwMark ?: fwMark,
            peers = peers + update.peers
        )
    }

    private fun VpnAdapterConfiguration.copyWith(
        listenPort: Int? = this.listenPort,
        privateKey: String = this.privateKey,
        publicKey: String = this.publicKey,
        fwMark: Int? = this.fwMark,
        peers: List<VpnPeer> = this.peers
    ): VpnAdapterConfiguration {
        return DefaultVpnAdapterConfiguration(
            listenPort = listenPort,
            privateKey = privateKey,
            publicKey = publicKey,
            fwMark = fwMark,
            peers = peers
        )
    }
}

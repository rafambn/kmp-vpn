package com.rafambn.kmpvpn

/**
 * Minimal VPN facade retained for the refactor branch.
 *
 * This version intentionally keeps only in-memory lifecycle state and the
 * core project models. Platform-specific behavior now lives outside this module.
 */
class Vpn(
    val engine: Engine = Engine.BORINGTUN,
    val vpnConfiguration: VpnConfiguration,
    val onAlert: ((String) -> Unit)? = null
) : AutoCloseable {

    private var adapter: VpnAdapter? = null

    init {
        require(vpnConfiguration.interfaceName.isNotBlank()) {
            "Interface name cannot be empty"
        }
    }

    fun exists(): Boolean {
        return adapter != null
    }

    fun isRunning(): Boolean {
        return adapter?.isRunning() == true
    }

    fun create(): VpnAdapter {
        adapter?.let { return it }

        return VpnAdapter(
            interfaceName = vpnConfiguration.interfaceName,
            initialConfiguration = vpnConfiguration
        ).also { adapter = it }
    }

    fun start(): VpnAdapter {
        val currentAdapter = create()
        if (currentAdapter.isRunning()) {
            val message = "`${currentAdapter.interfaceName}` already exists and is up"
            onAlert?.invoke(message)
            throw IllegalStateException(message)
        }

        currentAdapter.start()
        return currentAdapter
    }

    fun stop() {
        adapter?.stop()
    }

    fun delete() {
        adapter?.delete()
        adapter = null
    }

    fun adapter(): VpnAdapter? {
        return adapter
    }

    override fun close() {
        delete()
    }

    companion object {
        const val DEFAULT_PORT: Int = 51820
    }
}

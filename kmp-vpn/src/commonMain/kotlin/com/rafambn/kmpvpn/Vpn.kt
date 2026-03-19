package com.rafambn.kmpvpn

import com.rafambn.kmpvpn.address.VpnAddress
import com.rafambn.kmpvpn.info.VpnInterfaceInformation
import com.rafambn.kmpvpn.platform.PlatformService
import com.rafambn.kmpvpn.platform.createPlatformService

class Vpn(
    engine: Engine = Engine.BORINGTUN,
    val vpnConfiguration: VpnConfiguration,
    val onAlert: ((Pair<ErrorCode, String>) -> Unit)? = null
) : AutoCloseable {

    private val platformService: PlatformService<out VpnAddress>
    private var adapter: VpnAdapter? = null

    init {
        val interfaceName = vpnConfiguration.interfaceName

        require(interfaceName.isNotEmpty()) { "Interface name cannot be empty" }

        platformService = createPlatformService(engine)

        require(platformService.isValidInterfaceName(interfaceName)) {
            "Interface name must match utun[0-9]+ format (e.g., utun0, utun1, utun42)"
        }

        try {
            adapter = platformService.adapter(interfaceName)
        } catch (_: Exception) {
            // Silently continue if adapter resolution fails
        }
    }

    fun started(): Boolean = adapter != null

    fun open() {
        if (adapter != null && adapter!!.address().isUp()) {
            val shortName = adapter!!.address().shortName()
            alertUser(ErrorCode.INTERFACE_ALREADY_UP, "`$shortName` already exists and is up")
            throw IllegalStateException("`$shortName` already exists and is up")
        }

        val req = StartRequest(configuration = vpnConfiguration)

        adapter = platformService.start(req)
    }

    fun information(): VpnInterfaceInformation? {
        return adapter?.information()
    }

    private fun alertUser(errorCode: ErrorCode, message: String) {
        val alert = Pair(errorCode, message)
        onAlert?.invoke(alert)
    }

    override fun close() {
        if (adapter != null) {
            platformService.stop(vpnConfiguration, adapter!!)
        }
    }

    companion object {
        const val DEFAULT_PORT: Int = 51820
    }
}

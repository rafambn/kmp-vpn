package com.rafambn.kmpvpn.platform

import com.rafambn.kmpvpn.NATMode
import com.rafambn.kmpvpn.VpnAdapter
import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.StartRequest
import com.rafambn.kmpvpn.VpnAdapterConfiguration
import com.rafambn.kmpvpn.address.VpnAddress
import com.rafambn.kmpvpn.info.VpnInterfaceInformation

/**
 * Platform-specific VPN service
 */
interface PlatformService<T : VpnAddress> {

    fun configuration(adapter: VpnAdapter): VpnAdapterConfiguration

    fun reconfigure(vpnAdapter: VpnAdapter, cfg: VpnAdapterConfiguration)

    fun append(vpnAdapter: VpnAdapter, cfg: VpnAdapterConfiguration)

    fun sync(vpnAdapter: VpnAdapter, cfg: VpnAdapterConfiguration)
    fun getNat(iface: String): NATMode?
    fun setNat(iface: String, nat: NATMode?)
    fun information(adapter: VpnAdapter): VpnInterfaceInformation
    fun remove(vpnAdapter: VpnAdapter, publicKey: String)

    @Throws(Exception::class)
    fun adapter(name: String): VpnAdapter

    @Throws(Exception::class)
    fun getByPublicKey(publicKey: String): VpnAdapter?

    @Throws(Exception::class)
    fun start(request: StartRequest): VpnAdapter

    @Throws(Exception::class)
    fun stop(configuration: VpnConfiguration, adapter: VpnAdapter)

    fun interfaceNameToNativeName(interfaceName: String): String?
}

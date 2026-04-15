package com.rafambn.kmpvpn.iface

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.session.DuplexChannelPipe

/**
 * Contract for platform-facing VPN interface ownership.
 *
 * This contract owns both interface lifecycle and interface runtime configuration.
 * Caller provides the cleartext packet pipe to use for exchanging raw IP packets
 * with the platform TUN device via [com.rafambn.kmpvpn.session.CryptoSessionManager].
 */
interface InterfaceManager {

    /**
     * Checks whether an interface with the provided name exists.
     */
    fun exists(): Boolean

    /**
     * Creates the interface with the provided base configuration.
     */
    fun create(config: VpnConfiguration)

    /**
     * Brings the interface up, opening the packet bridge to the daemon TUN device.
     * [onBridgeFailure] is called if the underlying packet bridge fails asynchronously.
     */
    fun up(onBridgeFailure: (Throwable) -> Unit = {})

    /**
     * Brings the interface down, closing the packet bridge.
     */
    fun down()

    /**
     * Deletes the interface.
     */
    fun delete()

    /**
     * Returns `true` when the interface is currently up.
     */
    fun isUp(): Boolean

    /**
     * Returns current effective interface configuration.
     */
    fun configuration(): VpnConfiguration

    /**
     * Replaces current interface configuration.
     */
    fun reconfigure(config: VpnConfiguration)

    /**
     * Reads current interface information.
     */
    fun readInformation(): VpnInterfaceInformation?
}

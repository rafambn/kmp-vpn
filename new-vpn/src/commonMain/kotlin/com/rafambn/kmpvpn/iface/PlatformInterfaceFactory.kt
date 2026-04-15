package com.rafambn.kmpvpn.iface

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.session.DuplexChannelPipe

/**
 * Platform-aware factory entrypoint for [InterfaceManager] creation.
 */
expect object PlatformInterfaceFactory {
    fun create(configuration: VpnConfiguration, tunPipe: DuplexChannelPipe<ByteArray>): InterfaceManager
}

package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.session.DuplexChannelPipe

/**
 * Platform-aware factory entrypoint for [InterfaceManager] creation.
 */
expect object PlatformInterfaceFactory {
    fun create(configuration: VpnConfiguration, tunPipe: DuplexChannelPipe<ByteArray>): InterfaceManager
}

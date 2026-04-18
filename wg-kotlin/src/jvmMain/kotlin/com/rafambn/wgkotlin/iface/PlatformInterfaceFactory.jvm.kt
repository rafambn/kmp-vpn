package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.session.DuplexChannelPipe

actual object PlatformInterfaceFactory {
    actual fun create(configuration: VpnConfiguration, tunPipe: DuplexChannelPipe<ByteArray>): InterfaceManager {
        return JvmInterfaceKoinBootstrap.createInterfaceManager(configuration, tunPipe)
    }
}

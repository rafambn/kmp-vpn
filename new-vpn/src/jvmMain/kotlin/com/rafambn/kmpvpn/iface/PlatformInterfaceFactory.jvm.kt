package com.rafambn.kmpvpn.iface

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.session.DuplexChannelPipe

actual object PlatformInterfaceFactory {
    actual fun create(configuration: VpnConfiguration, tunPipe: DuplexChannelPipe<ByteArray>): InterfaceManager {
        return JvmInterfaceKoinBootstrap.createInterfaceManager(configuration, tunPipe)
    }
}

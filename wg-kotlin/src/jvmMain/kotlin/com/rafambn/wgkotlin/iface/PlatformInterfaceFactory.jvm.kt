package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.session.DuplexChannelPipe

actual object PlatformInterfaceFactory {
    actual fun create(tunPipe: DuplexChannelPipe<ByteArray>): InterfaceManager {
        return JvmInterfaceKoinBootstrap.createInterfaceManager(tunPipe)
    }
}

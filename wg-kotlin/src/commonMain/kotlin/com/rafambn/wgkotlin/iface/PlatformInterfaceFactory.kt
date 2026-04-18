package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.session.DuplexChannelPipe

expect object PlatformInterfaceFactory {
    fun create(tunPipe: DuplexChannelPipe<ByteArray>): InterfaceManager
}

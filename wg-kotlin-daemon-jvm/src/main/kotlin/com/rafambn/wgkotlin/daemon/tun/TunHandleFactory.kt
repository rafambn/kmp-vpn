package com.rafambn.wgkotlin.daemon.tun

internal fun interface TunHandleFactory {
    fun open(interfaceName: String): TunHandle
}

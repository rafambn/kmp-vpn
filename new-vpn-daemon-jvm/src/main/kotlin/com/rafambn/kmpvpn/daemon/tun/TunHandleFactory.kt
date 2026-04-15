package com.rafambn.kmpvpn.daemon.tun

internal fun interface TunHandleFactory {
    fun open(interfaceName: String): TunHandle
}

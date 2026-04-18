package com.rafambn.wgkotlin.session.io

data class UdpDatagram(
    val payload: ByteArray,
    val remoteEndpoint: UdpEndpoint,
)

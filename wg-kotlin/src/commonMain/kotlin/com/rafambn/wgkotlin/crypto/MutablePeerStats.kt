package com.rafambn.wgkotlin.crypto

internal class MutablePeerStats(
    @Volatile var receivedBytes: Long = 0L,
    @Volatile var transmittedBytes: Long = 0L,
)

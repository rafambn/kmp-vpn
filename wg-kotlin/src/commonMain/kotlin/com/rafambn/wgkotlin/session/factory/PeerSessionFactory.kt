package com.rafambn.wgkotlin.session.factory

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.VpnPeer
import com.rafambn.wgkotlin.session.PeerSession

interface PeerSessionFactory {
    fun create(
        config: VpnConfiguration,
        peer: VpnPeer,
        peerIndex: Int,
    ): PeerSession
}

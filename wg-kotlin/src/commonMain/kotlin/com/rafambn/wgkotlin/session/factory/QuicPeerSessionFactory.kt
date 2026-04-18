package com.rafambn.wgkotlin.session.factory

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.VpnPeer
import com.rafambn.wgkotlin.session.PeerSession

class QuicPeerSessionFactory : PeerSessionFactory {
    override fun create(
        config: VpnConfiguration,
        peer: VpnPeer,
        peerIndex: Int,
    ): PeerSession {
        throw UnsupportedOperationException("QUIC engine is not supported yet")
    }
}

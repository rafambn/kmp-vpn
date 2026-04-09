package com.rafambn.kmpvpn

/**
 * WireGuard session/data-plane configuration consumed by the session manager.
 */
interface VpnAdapterConfiguration {

    val listenPort: Int?

    val privateKey: String

    //TODO(Create default key generator)
    //return Keys.pubkeyBase64(privateKey()).getBase64PublicKey()
    val publicKey: String

    val peers: List<VpnPeer>
}

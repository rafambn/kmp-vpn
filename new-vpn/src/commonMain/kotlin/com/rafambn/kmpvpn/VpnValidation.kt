package com.rafambn.kmpvpn

internal fun requireNonBlankInterfaceName(interfaceName: String) {
    require(interfaceName.isNotBlank()) {
        "Interface name cannot be empty"
    }
}

internal fun requireUniquePeerPublicKeys(peers: List<VpnPeer>) {
    val duplicatedKeys = peers
        .groupingBy { peer -> peer.publicKey }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys

    require(duplicatedKeys.isEmpty()) {
        "Peer public keys must be unique. Duplicated keys: ${duplicatedKeys.joinToString()}"
    }
}

internal fun requireValidConfiguration(config: VpnConfiguration) {
    requireNonBlankInterfaceName(config.interfaceName)
    requireUniquePeerPublicKeys(config.adapter.peers)
}

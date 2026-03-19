package com.rafambn.kmpvpn

data class StartRequest(
    val configuration: VpnConfiguration,
    val interfaceName: String? = null
)

package com.rafambn.wgkotlin.daemon.protocol.response

import kotlinx.serialization.Serializable

@Serializable
data class ApplyMtuResponse(
    val interfaceName: String,
    val mtu: Int,
)

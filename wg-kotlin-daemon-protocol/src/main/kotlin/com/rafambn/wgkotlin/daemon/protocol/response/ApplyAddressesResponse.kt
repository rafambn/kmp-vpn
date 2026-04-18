package com.rafambn.wgkotlin.daemon.protocol.response

import kotlinx.serialization.Serializable

@Serializable
data class ApplyAddressesResponse(
    val interfaceName: String,
    val addresses: List<String>,
)

package com.rafambn.wgkotlin.daemon.protocol.response

import kotlinx.serialization.Serializable

@Serializable
data class InterfaceExistsResponse(
    val interfaceName: String,
    val exists: Boolean,
)

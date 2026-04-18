package com.rafambn.wgkotlin.daemon.protocol.response

import kotlinx.serialization.Serializable

@Serializable
data class CreateInterfaceResponse(
    val interfaceName: String,
)

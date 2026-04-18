package com.rafambn.wgkotlin.daemon.protocol.response

import kotlinx.serialization.Serializable

@Serializable
data class ApplyDnsResponse(
    val interfaceName: String,
    val dnsDomainPool: Pair<List<String>, List<String>>,
)

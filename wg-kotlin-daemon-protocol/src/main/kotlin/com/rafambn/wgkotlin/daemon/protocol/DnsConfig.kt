package com.rafambn.wgkotlin.daemon.protocol

import kotlinx.serialization.Serializable

@Serializable
data class DnsConfig(
    val searchDomains: List<String> = emptyList(),
    val servers: List<String> = emptyList(),
)

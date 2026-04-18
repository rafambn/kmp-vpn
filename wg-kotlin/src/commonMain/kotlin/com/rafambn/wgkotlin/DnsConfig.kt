package com.rafambn.wgkotlin

data class DnsConfig(
    val searchDomains: List<String> = emptyList(),
    val servers: List<String> = emptyList(),
)

package com.rafambn.kmpvpn.info

import com.rafambn.kmpvpn.InetSocketAddress
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * Information about a VPN peer
 */
interface VpnPeerInformation {

    fun allowedIps(): List<String>

    fun remoteAddress(): InetSocketAddress?

    fun publicKey(): String

    fun presharedKey(): String?

    fun endpoint(): String?

    fun tx(): Long

    fun rx(): Long

    @OptIn(ExperimentalTime::class)
    fun lastHandshake(): Instant

    fun error(): String?

    fun persistentKeepalive(): Int?

    fun protocolVersion(): Int?
}

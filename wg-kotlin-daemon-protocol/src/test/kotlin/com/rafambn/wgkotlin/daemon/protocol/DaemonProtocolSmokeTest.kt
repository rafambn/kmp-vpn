package com.rafambn.wgkotlin.daemon.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class DaemonProtocolSmokeTest {

    private val protoBuf = ProtoBuf

    @Test
    fun dnsConfigRoundTripPreservesTypedFields() {
        val original = DnsConfig(
            searchDomains = listOf("corp.local", "dev.local"),
            servers = listOf("1.1.1.1", "9.9.9.9"),
        )

        val encoded = protoBuf.encodeToByteArray(original)
        val decoded = protoBuf.decodeFromByteArray<DnsConfig>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun tunSessionConfigRoundTripPreservesStructuredFields() {
        val original = TunSessionConfig(
            interfaceName = "utun42",
            mtu = 1420,
            addresses = listOf("10.20.30.40/32", "fd00::1/128"),
            routes = listOf("0.0.0.0/0", "::/0"),
            dns = DnsConfig(
                searchDomains = listOf("corp.local"),
                servers = listOf("1.1.1.1"),
            ),
        )

        val encoded = protoBuf.encodeToByteArray(original)
        val decoded = protoBuf.decodeFromByteArray<TunSessionConfig>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun deserializationRejectsMalformedCommandShape() {
        val malformed = byteArrayOf(0x0A)

        kotlin.test.assertFailsWith<SerializationException> {
            protoBuf.decodeFromByteArray<TunSessionConfig>(malformed)
        }
    }

    @Test
    fun daemonProcessApiExposesOnlyPingAndStartSession() {
        val functions = DaemonProcessApi::class.java.declaredMethods
            .map { method -> method.name }
            .sorted()

        assertEquals(listOf("ping", "startSession"), functions)
    }

    @Test
    fun startSessionRemainsPacketStreamApi() {
        val function = DaemonProcessApi::class.java.declaredMethods.single { method -> method.name == "startSession" }
        val returnTypeName = function.genericReturnType.typeName

        assertTrue(returnTypeName.contains("Flow"))
        assertFalse(returnTypeName.contains("CommandResult"))
    }

    @Test
    fun pingReturnsDirectPingResponse() {
        val function = DaemonProcessApi::class.java.declaredMethods.single { method -> method.name == "ping" }
        val continuationTypeName = function.genericParameterTypes.single().typeName

        assertTrue(continuationTypeName.contains("PingResponse"))
        assertFalse(continuationTypeName.contains("CommandResult"))
    }

    @Test
    fun daemonRpcUrlWrapsIpv6Hosts() {
        assertEquals("ws://[::1]:8787/services", daemonRpcUrl(host = "::1", port = 8787))
        assertEquals("ws://127.0.0.1:8787/services", daemonRpcUrl(host = "127.0.0.1", port = 8787))
    }
}

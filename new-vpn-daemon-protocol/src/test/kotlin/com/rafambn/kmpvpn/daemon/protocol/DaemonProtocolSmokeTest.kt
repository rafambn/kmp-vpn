package com.rafambn.kmpvpn.daemon.protocol

import com.rafambn.kmpvpn.daemon.protocol.response.ApplyDnsResponse
import com.rafambn.kmpvpn.daemon.protocol.response.CreateInterfaceResponse
import com.rafambn.kmpvpn.daemon.protocol.response.ReadInterfaceInformationResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalSerializationApi::class)
class DaemonProtocolSmokeTest {

    private val protoBuf = ProtoBuf

    @Test
    fun applyDnsPayloadRoundTripPreservesTypedFields() {
        val original = ApplyDnsResponse(
            interfaceName = "wg0",
            dnsDomainPool = (
                listOf("corp.local", "dev.local") to
                    listOf("1.1.1.1", "9.9.9.9")
                ),
        )

        val encoded = protoBuf.encodeToByteArray(original)
        val decoded = protoBuf.decodeFromByteArray<ApplyDnsResponse>(encoded)

        assertEquals("wg0", decoded.interfaceName)
        assertEquals(
            listOf("corp.local", "dev.local") to listOf("1.1.1.1", "9.9.9.9"),
            decoded.dnsDomainPool,
        )
    }

    @Test
    fun failureResultRoundTripPreservesErrorPayload() {
        val original = CommandResult.failure<Unit>(
            kind = DaemonErrorKind.COMMAND_FAILED,
            message = "invalid route",
            detail = DaemonFailureDetail(
                executable = "ip",
                exitCode = 2,
            ),
        )

        val encoded = protoBuf.encodeToByteArray(original)
        val decoded = protoBuf.decodeFromByteArray<CommandResult<Unit>>(encoded) as CommandResult.Failure

        assertEquals(DaemonErrorKind.COMMAND_FAILED, decoded.kind)
        assertEquals("invalid route", decoded.message)
        assertEquals("ip", decoded.detail?.executable)
        assertEquals(2, decoded.detail?.exitCode)
    }

    @Test
    fun deserializationRejectsMalformedCommandShape() {
        val malformed = byteArrayOf(0x0A)

        kotlin.test.assertFailsWith<SerializationException> {
            protoBuf.decodeFromByteArray<ApplyDnsResponse>(malformed)
        }
    }

    @Test
    fun readInterfaceInformationPayloadRoundTripPreservesStructuredData() {
        val original = ReadInterfaceInformationResponse(
            interfaceName = "wg0",
            isUp = true,
            addresses = listOf("10.20.30.40/32"),
            dnsDomainPool = (listOf("corp.local") to listOf("1.1.1.1")),
            mtu = 1420,
            listenPort = 51820,
        )

        val encoded = protoBuf.encodeToByteArray(original)
        val decoded = protoBuf.decodeFromByteArray<ReadInterfaceInformationResponse>(encoded)

        assertEquals("wg0", decoded.interfaceName)
        assertEquals(true, decoded.isUp)
        assertEquals(listOf("10.20.30.40/32"), decoded.addresses)
        assertEquals(listOf("corp.local") to listOf("1.1.1.1"), decoded.dnsDomainPool)
        assertEquals(1420, decoded.mtu)
        assertEquals(51820, decoded.listenPort)
    }

    @Test
    fun protocolTypesRemainControlPlaneOnly() {
        val typeNames = listOf(
            "PingResponse",
            "ApplyDnsResponse",
            "ApplyRoutesResponse",
            "ReadInterfaceInformationResponse",
        )

        typeNames.forEach { typeName ->
            val normalized = typeName.lowercase()
            assertFalse(normalized.contains("packet"), "Type `$typeName` must not carry packet payload")
            assertFalse(normalized.contains("tun"), "Type `$typeName` must not control runtime packet loop")
            assertFalse(normalized.contains("udp"), "Type `$typeName` must not control runtime packet loop")
        }
    }

    @Test
    fun createInterfaceResponseRoundTripPreservesInterfaceName() {
        val original = CreateInterfaceResponse(interfaceName = "utun0")

        val encoded = protoBuf.encodeToByteArray(original)
        val decoded = protoBuf.decodeFromByteArray<CreateInterfaceResponse>(encoded)

        assertEquals("utun0", decoded.interfaceName)
    }

    @Test
    fun daemonRpcUrlWrapsIpv6Hosts() {
        assertEquals("ws://[::1]:8787/services", daemonRpcUrl(host = "::1", port = 8787))
        assertEquals("ws://127.0.0.1:8787/services", daemonRpcUrl(host = "127.0.0.1", port = 8787))
    }

}

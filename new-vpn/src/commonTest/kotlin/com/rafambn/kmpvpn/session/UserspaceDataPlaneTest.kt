package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertFalse
import kotlin.test.Test

class UserspaceDataPlaneTest {

    @Test
    fun inboundOutboundAndPeriodicWorkersRunSideBySide() = runBlocking {
        val inboundStarted = CompletableDeferred<Unit>()
        val outboundStarted = CompletableDeferred<Unit>()
        val periodicStarted = CompletableDeferred<Unit>()
        val packetWorkerGate = CompletableDeferred<Unit>()
        val periodicWorkerGate = CompletableDeferred<Unit>()

        val dataPlane = UserspaceDataPlane(
            configuration = VpnConfiguration(
                interfaceName = "wg-test",
                privateKey = "private-key",
            ),
            onFailure = { throwable ->
                throw AssertionError("Unexpected data plane failure", throwable)
            },
            listenPort = 0,
            pollInboundPacketOnce = {
                inboundStarted.complete(Unit)
                packetWorkerGate.await()
                false
            },
            pollOutboundPacketOnce = {
                outboundStarted.complete(Unit)
                packetWorkerGate.await()
                false
            },
            runPeriodicWorkOnce = {
                periodicStarted.complete(Unit)
                periodicWorkerGate.await()
                false
            },
        )

        try {
            withTimeout(1_000L) {
                inboundStarted.await()
                outboundStarted.await()
                periodicStarted.await()
            }
        } finally {
            packetWorkerGate.complete(Unit)
            periodicWorkerGate.complete(Unit)
            dataPlane.close()
        }
    }

    @Test
    fun periodicWorkerWaitsBeforeFirstRun() = runBlocking {
        val periodicStarted = CompletableDeferred<Unit>()

        val dataPlane = UserspaceDataPlane(
            configuration = VpnConfiguration(
                interfaceName = "wg-test",
                privateKey = "private-key",
            ),
            onFailure = { throwable ->
                throw AssertionError("Unexpected data plane failure", throwable)
            },
            listenPort = 0,
            pollInboundPacketOnce = {
                delay(500L)
                false
            },
            pollOutboundPacketOnce = {
                delay(500L)
                false
            },
            runPeriodicWorkOnce = {
                periodicStarted.complete(Unit)
                false
            },
        )

        try {
            delay(50L)
            assertFalse(periodicStarted.isCompleted)
            withTimeout(1_000L) {
                periodicStarted.await()
            }
        } finally {
            dataPlane.close()
        }
    }
}

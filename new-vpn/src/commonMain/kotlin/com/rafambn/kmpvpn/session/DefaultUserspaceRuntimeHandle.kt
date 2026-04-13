package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.iface.VpnPeerStats
import com.rafambn.kmpvpn.session.io.KtorDatagramUdpPort
import com.rafambn.kmpvpn.session.io.UdpPort
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal class DefaultUserspaceRuntimeHandle(
    configuration: VpnConfiguration,
    private val onFailure: (Throwable) -> Unit,
    listenPort: Int,
    receiveTimeoutMillis: Long,
    private val idleDelayMillis: Long,
    periodicIntervalMillis: Long,
    private val pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
    private val peerStats: () -> List<VpnPeerStats>,
) : UserspaceRuntimeHandle {
    private val running = MutableStateFlow(true)
    private val peerStatsSnapshot = MutableStateFlow<List<VpnPeerStats>>(emptyList())
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineName("kmpvpn-runtime-${configuration.interfaceName}"),
    )
    private val selectorManager = SelectorManager(scope.coroutineContext)
    private val socket: BoundDatagramSocket = runBlocking {
        aSocket(selectorManager).udp().bind(
            InetSocketAddress("0.0.0.0", listenPort),
        )
    }
    private val periodicTicker = FixedIntervalTicker(periodicIntervalMillis)
    private val udpPort = KtorDatagramUdpPort(
        socket = socket,
        receiveTimeoutMillis = receiveTimeoutMillis,
    )
    private val runtimeJob = scope.launch {
        runLoop()
    }

    init {
        peerStatsSnapshot.value = peerStats()
    }

    override fun isRunning(): Boolean {
        return running.value
    }

    override fun peerStats(): List<VpnPeerStats> {
        return peerStatsSnapshot.value
    }

    override fun close() {
        runBlocking {
            if (!running.value) {
                return@runBlocking
            }

            running.value = false
            socket.close()
            selectorManager.close()
            runtimeJob.cancelAndJoin()
            scope.cancel()
            peerStatsSnapshot.value = peerStats()
        }
    }

    private suspend fun runLoop() {
        try {
            while (running.value) {
                val didWork = pollOnce(udpPort, periodicTicker::shouldTick)
                peerStatsSnapshot.value = peerStats()
                if (!didWork) {
                    delay(idleDelayMillis.coerceAtLeast(0L))
                }
            }
        } catch (_: CancellationException) {
            // shutdown path
        } catch (throwable: Throwable) {
            if (running.value) {
                running.value = false
                onFailure(throwable)
            }
        } finally {
            peerStatsSnapshot.value = peerStats()
            running.value = false
        }
    }

    private class FixedIntervalTicker(
        intervalMillis: Long,
    ) {
        private val interval: Duration = intervalMillis.coerceAtLeast(1L).milliseconds
        private var nextTick = TimeSource.Monotonic.markNow() + interval

        fun shouldTick(): Boolean {
            if (nextTick.hasNotPassedNow()) {
                return false
            }
            nextTick = TimeSource.Monotonic.markNow() + interval
            return true
        }
    }
}

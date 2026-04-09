package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.VpnConfiguration
import com.rafambn.kmpvpn.iface.VpnPeerStats
import com.rafambn.kmpvpn.session.io.KtorDatagramUdpPort
import com.rafambn.kmpvpn.session.io.UdpPort
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal class JvmUserspaceRuntimeHandle(
    configuration: VpnConfiguration,
    private val onFailure: (Throwable) -> Unit,
    listenPort: Int,
    receiveTimeoutMillis: Long,
    private val idleDelayMillis: Long,
    periodicIntervalMillis: Long,
    private val pollOnce: suspend (UdpPort, () -> Boolean) -> Boolean,
    private val peerStats: () -> List<VpnPeerStats>,
) : UserspaceRuntimeHandle {
    private val running = AtomicBoolean(true)
    private val peerStatsSnapshot = AtomicReference<List<VpnPeerStats>>(emptyList())
    private val selectorManager = SelectorManager(Dispatchers.IO)
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
    private val runtimeThread = Thread(
        {
            runLoop()
        },
        "kmpvpn-runtime-${configuration.interfaceName}",
    ).apply {
        isDaemon = true
        start()
    }

    init {
        peerStatsSnapshot.set(peerStats())
    }

    override fun isRunning(): Boolean {
        return running.get()
    }

    override fun peerStats(): List<VpnPeerStats> {
        return peerStatsSnapshot.get()
    }

    override fun close() {
        if (!running.getAndSet(false)) {
            return
        }

        socket.close()
        selectorManager.close()
        runtimeThread.interrupt()
        runtimeThread.join(CLOSE_TIMEOUT_MILLIS)
    }

    private fun runLoop() {
        try {
            while (running.get()) {
                val didWork = runBlocking { pollOnce(udpPort, periodicTicker::shouldTick) }
                peerStatsSnapshot.set(peerStats())
                if (!didWork) {
                    Thread.sleep(idleDelayMillis)
                }
            }
        } catch (_: InterruptedException) {
            if (running.get()) {
                running.set(false)
            }
        } catch (throwable: Throwable) {
            if (running.getAndSet(false)) {
                onFailure(throwable)
            }
        } finally {
            peerStatsSnapshot.set(peerStats())
            running.set(false)
        }
    }

    private class FixedIntervalTicker(
        intervalMillis: Long,
    ) {
        private val intervalNanos = intervalMillis.coerceAtLeast(1L) * 1_000_000L
        private var nextTickNanos: Long = System.nanoTime() + intervalNanos

        fun shouldTick(): Boolean {
            val now = System.nanoTime()
            if (now < nextTickNanos) {
                return false
            }
            nextTickNanos = now + intervalNanos
            return true
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_MILLIS: Long = 2_000L
    }
}

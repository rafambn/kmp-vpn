package com.rafambn.wgkotlin

import com.rafambn.wgkotlin.iface.InterfaceManager
import com.rafambn.wgkotlin.iface.PlatformInterfaceFactory
import com.rafambn.wgkotlin.iface.VpnInterfaceInformation
import com.rafambn.wgkotlin.session.CryptoSessionManager
import com.rafambn.wgkotlin.session.CryptoSessionManagerImpl
import com.rafambn.wgkotlin.session.DuplexChannelPipe
import com.rafambn.wgkotlin.session.SocketManager
import com.rafambn.wgkotlin.session.SocketManagerImpl
import com.rafambn.wgkotlin.session.io.UdpDatagram

class Vpn(
    configuration: VpnConfiguration,
    engine: Engine = Engine.BORINGTUN,
    cryptoSessionManager: CryptoSessionManager? = null,
    socketManager: SocketManager? = null,
    interfaceManager: InterfaceManager? = null,
) : AutoCloseable {

    companion object {
        const val DEFAULT_PORT: Int = 51820
    }

    private val tunPipePair = DuplexChannelPipe.create<ByteArray>()
    private val networkPipePair = DuplexChannelPipe.create<UdpDatagram>()

    private var vpnConfiguration = configuration
    private val cryptoSessionManager = cryptoSessionManager ?: CryptoSessionManagerImpl(engine = engine)
    private val socketManager = socketManager ?: SocketManagerImpl()
    private val interfaceManager = interfaceManager ?: PlatformInterfaceFactory.create(tunPipePair.first)

    init {
        requireValidConfiguration(vpnConfiguration)
    }

    fun state(): VpnState {
        return if (interfaceManager.isRunning() && cryptoSessionManager.hasActiveSessions()) {
            VpnState.Running
        } else {
            VpnState.Stopped
        }
    }

    fun start(): InterfaceManager {
        requireValidConfiguration(vpnConfiguration)
        stop()

        sessionOperation("reconcileSessions") {
            cryptoSessionManager.reconcileSessions(vpnConfiguration)
        }

        sessionOperation("start") {
            cryptoSessionManager.start(tunPipePair.second, networkPipePair.second) { stop() }
        }

        sessionOperation("socketStart") {
            socketManager.start(
                listenPort = vpnConfiguration.listenPort ?: DEFAULT_PORT,
                networkPipe = networkPipePair.first,
                onFailure = { stop() },
            )
        }

        interfaceOperation("start") {
            interfaceManager.start(vpnConfiguration) { stop() }
        }

        return interfaceManager
    }

    fun stop() {
        runCleanupSequence(
            { interfaceOperation("stop") { interfaceManager.stop() } },
            { sessionOperation("socketStop") { socketManager.stop() } },
            { sessionOperation("stop") { cryptoSessionManager.stop() } },
        )
    }

    fun information(): VpnInterfaceInformation? {
        val liveInformation = interfaceOperation("information") {
            interfaceManager.information()
        } ?: return null

        val runtimePeerStats = cryptoSessionManager.peerStats()
        val informationWithPeerStats = if (runtimePeerStats.isEmpty()) {
            liveInformation
        } else {
            liveInformation.copy(peerStats = runtimePeerStats)
        }

        return informationWithPeerStats.copy(vpnConfiguration = vpnConfiguration)
    }

    fun reconfigure(config: VpnConfiguration) {
        requireValidConfiguration(config)
        require(config.interfaceName == vpnConfiguration.interfaceName) {
            "Cannot reconfigure interface `${vpnConfiguration.interfaceName}` using `${config.interfaceName}`"
        }

        val previousListenPort = vpnConfiguration.listenPort ?: DEFAULT_PORT
        vpnConfiguration = config

        sessionOperation("reconcileSessions") {
            cryptoSessionManager.reconcileSessions(config)
        }

        if (interfaceManager.isRunning()) {
            val newListenPort = config.listenPort ?: DEFAULT_PORT

            interfaceOperation("reconfigure") {
                interfaceManager.reconfigure(config)
            }

            if (previousListenPort != newListenPort) {
                sessionOperation("socketRestart") {
                    socketManager.stop()
                    socketManager.start(newListenPort, networkPipePair.first) { stop() }
                }
            }
        }
    }

    override fun close() {
        stop()
    }

    private inline fun <T> interfaceOperation(name: String, block: () -> T): T {
        return operation(kind = "Interface", name = name, block = block)
    }

    private inline fun <T> sessionOperation(name: String, block: () -> T): T {
        return operation(kind = "Session", name = name, block = block)
    }

    private inline fun <T> operation(kind: String, name: String, block: () -> T): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            throw IllegalStateException(
                "$kind operation `$name` failed: ${throwable.message ?: "unknown"}",
                throwable,
            )
        }
    }

    private fun runCleanupSequence(vararg operations: () -> Unit) {
        var firstError: Throwable? = null
        for (operation in operations) {
            try {
                operation()
            } catch (error: Throwable) {
                if (firstError == null) {
                    firstError = error
                }
            }
        }
        if (firstError != null) {
            throw firstError
        }
    }
}

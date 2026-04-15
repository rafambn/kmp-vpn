package com.rafambn.kmpvpn

import com.rafambn.kmpvpn.iface.InterfaceManager
import com.rafambn.kmpvpn.iface.PlatformInterfaceFactory
import com.rafambn.kmpvpn.iface.VpnInterfaceInformation
import com.rafambn.kmpvpn.session.CryptoSessionManager
import com.rafambn.kmpvpn.session.CryptoSessionManagerImpl
import com.rafambn.kmpvpn.session.DuplexChannelPipe
import com.rafambn.kmpvpn.session.SocketManager
import com.rafambn.kmpvpn.session.SocketManagerImpl
import com.rafambn.kmpvpn.session.io.UdpDatagram

/**
 * Core orchestrator facade for VPN lifecycle operations.
 *
 * Manages the full lifecycle of a VPN interface (create, start, stop, delete)
 * while delegating peer-session and interface concerns to [CryptoSessionManager],
 * [SocketManager], and [InterfaceManager].
 *
 * Owns both duplex channel pipes: one for TUN/crypto cleartext exchange (tunPipePair),
 * one for socket/crypto network exchange (networkPipePair). Distributes the appropriate
 * ends to each manager.
 */
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
    private val interfaceManager = interfaceManager ?: PlatformInterfaceFactory.create(configuration, tunPipePair.first)

    init {
        requireValidConfiguration(vpnConfiguration)
    }

    /**
     * Returns current lifecycle state derived from current system observations.
     */
    fun state(): VpnState {
        if (!exists()) {
            return VpnState.NotCreated
        }

        return if (isRunning()) {
            VpnState.Running
        } else {
            VpnState.Created
        }
    }

    /**
     * Returns whether the VPN interface currently exists.
     */
    fun exists(): Boolean {
        return interfaceManager.exists()
    }

    /**
     * Returns whether the interface exists and active peer sessions are running.
     */
    fun isRunning(): Boolean {
        if (!exists()) {
            return false
        }
        if (!interfaceManager.isUp()) {
            return false
        }

        return cryptoSessionManager.hasActiveSessions()
    }

    /**
     * Creates the interface and returns the managed interface contract.
     */
    fun create(): InterfaceManager {
        interfaceOperation("create") {
            interfaceManager.create(vpnConfiguration)
        }

        sessionOperation("reconcileSessions") {
            cryptoSessionManager.reconcileSessions(interfaceManager.configuration())
        }

        return interfaceManager
    }

    /**
     * Starts the interface and ensures sessions are active.
     */
    fun start(): InterfaceManager {
        val managedInterface = if (exists()) {
            interfaceManager
        } else {
            create()
        }

        val currentConfiguration = interfaceOperation("configuration") {
            managedInterface.configuration()
        }
        requireValidConfiguration(currentConfiguration)

        interfaceOperation("reconfigure") {
            managedInterface.reconfigure(currentConfiguration)
        }

        sessionOperation("reconcileSessions") {
            cryptoSessionManager.reconcileSessions(currentConfiguration)
        }

        sessionOperation("start") {
            cryptoSessionManager.start(tunPipePair.second, networkPipePair.second) { stop() }
        }

        sessionOperation("socketStart") {
            socketManager.start(
                listenPort = currentConfiguration.listenPort ?: DEFAULT_PORT,
                networkPipe = networkPipePair.first,
                onFailure = { stop() },
            )
        }

        interfaceOperation("up") { managedInterface.up { stop() } }

        return managedInterface
    }

    /**
     * Stops the interface. This operation is idempotent.
     */
    fun stop() {
        runCleanupSequence(
            { interfaceOperation("down") { interfaceManager.down() } },
            { sessionOperation("socketStop") { socketManager.stop() } },
            { sessionOperation("stop") { cryptoSessionManager.stop() } },
        )
    }

    /**
     * Deletes the interface. This operation is idempotent.
     */
    fun delete() {
        runCleanupSequence(
            { interfaceOperation("down") { interfaceManager.down() } },
            { sessionOperation("socketStop") { socketManager.stop() } },
            { sessionOperation("stop") { cryptoSessionManager.stop() } },
            { interfaceOperation("delete") { interfaceManager.delete() } },
        )
    }

    /**
     * Returns current live interface information, or `null` if the interface does not exist.
     */
    fun information(): VpnInterfaceInformation? {
        if (!exists()) {
            return null
        }

        val baseInformation = interfaceOperation("readInformation") {
            interfaceManager.readInformation()
        }

        val currentDefinedConfiguration = interfaceOperation("configuration") {
            interfaceManager.configuration()
        }

        val liveInformation = baseInformation ?: VpnInterfaceInformation(
            interfaceName = currentDefinedConfiguration.interfaceName,
            isUp = interfaceManager.isUp(),
            addresses = currentDefinedConfiguration.addresses.toList(),
            dnsDomainPool = currentDefinedConfiguration.dnsDomainPool,
            mtu = currentDefinedConfiguration.mtu,
            listenPort = currentDefinedConfiguration.listenPort,
        )

        val runtimePeerStats = cryptoSessionManager.peerStats()
        val informationWithPeerStats = if (runtimePeerStats.isEmpty()) {
            liveInformation
        } else {
            liveInformation.copy(peerStats = runtimePeerStats)
        }

        return informationWithPeerStats.copy(vpnConfiguration = currentDefinedConfiguration)
    }

    /**
     * Replaces current interface configuration and reconciles sessions.
     */
    fun reconfigure(config: VpnConfiguration) {
        requireValidConfiguration(config)
        require(config.interfaceName == vpnConfiguration.interfaceName) {
            "Cannot reconfigure interface `${vpnConfiguration.interfaceName}` using `${config.interfaceName}`"
        }

        val previousListenPort = vpnConfiguration.listenPort ?: DEFAULT_PORT

        interfaceOperation("reconfigure") {
            interfaceManager.reconfigure(config)
        }

        vpnConfiguration = config

        sessionOperation("reconcileSessions") {
            cryptoSessionManager.reconcileSessions(interfaceManager.configuration())
        }

        if (interfaceManager.isUp()) {
            val newListenPort = config.listenPort ?: DEFAULT_PORT
            if (previousListenPort != newListenPort) {
                sessionOperation("socketRestart") {
                    socketManager.stop()
                    socketManager.start(newListenPort, networkPipePair.first, { stop() })
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

    private inline fun runBestEffort(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // preserve original failure during rollback
        }
    }

    private inline fun runCleanupSequence(vararg operations: () -> Unit) {
        var firstError: Throwable? = null
        for (operation in operations) {
            try {
                operation()
            } catch (e: Throwable) {
                if (firstError == null) {
                    firstError = e
                }
            }
        }
        if (firstError != null) {
            throw firstError
        }
    }
}

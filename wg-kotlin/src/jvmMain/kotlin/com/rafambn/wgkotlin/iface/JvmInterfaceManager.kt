package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.requireValidConfiguration
import com.rafambn.wgkotlin.session.DuplexChannelPipe

/**
 * JVM-backed [InterfaceManager] implementation using privileged
 * [InterfaceCommandExecutor] operations.
 *
 * The cleartext packet pipe is provided by the caller. [up] opens the packetIO RPC bridge
 * via [InterfaceCommandExecutor.openPacketBridge]; [down] closes it.
 */
class JvmInterfaceManager(
    private val interfaceName: String,
    private val commandExecutor: InterfaceCommandExecutor,
    private val tunPipe: DuplexChannelPipe<ByteArray>,
) : InterfaceManager {
    private var currentConfiguration: VpnConfiguration? = null
    private var up: Boolean = false

    private var activeBridge: AutoCloseable? = null

    override fun exists(): Boolean {
        val observedExists = commandExecutor.interfaceExists(interfaceName)
        if (!observedExists) {
            runCatching { activeBridge?.close() }
            activeBridge = null
            currentConfiguration = null
            up = false
        }
        return observedExists
    }

    override fun create(config: VpnConfiguration) {
        requireValidConfiguration(config)
        require(config.interfaceName == interfaceName) {
            "Cannot create interface `${config.interfaceName}` on a manager for `$interfaceName`"
        }

        if (currentConfiguration == config && commandExecutor.interfaceExists(interfaceName)) {
            return
        }

        var interfaceCreated = false
        try {
            commandExecutor.createInterface(interfaceName)
            interfaceCreated = true
            applyMtu(config)
            applyAddresses(config)
            applyRoutes(config)
            applyDns(config)
        } catch (throwable: Throwable) {
            if (interfaceCreated) {
                runCatching { commandExecutor.deleteInterface(interfaceName) }
            }
            runCatching { activeBridge?.close() }
            activeBridge = null
            currentConfiguration = null
            up = false
            throw IllegalStateException(
                "Failed to create interface `$interfaceName`: ${throwable.message ?: "unknown"}",
                throwable,
            )
        }

        currentConfiguration = config.copy()
        up = false
    }

    override fun up(onBridgeFailure: (Throwable) -> Unit) {
        if (activeBridge != null && up) {
            return
        }

        runCatching { activeBridge?.close() }
        activeBridge = null
        activeBridge = commandExecutor.openPacketBridge(
            interfaceName = interfaceName,
            pipe = tunPipe,
        ) { throwable ->
            up = false
            val failedBridge = activeBridge
            runCatching { failedBridge?.close() }
            if (activeBridge === failedBridge) {
                activeBridge = null
            }
            onBridgeFailure(throwable)
        }
        up = true
    }

    override fun down() {
        if (activeBridge == null && !up) {
            return
        }

        runCatching { activeBridge?.close() }
        activeBridge = null
        up = false
    }

    override fun delete() {
        runCatching { activeBridge?.close() }
        activeBridge = null
        commandExecutor.deleteInterface(interfaceName)
        currentConfiguration = null
        up = false
    }

    override fun isUp(): Boolean {
        val observedUp = commandExecutor.readInformation(interfaceName)?.isUp == true
        up = observedUp
        return observedUp
    }

    override fun configuration(): VpnConfiguration {
        val configuration = currentConfiguration ?: throw IllegalStateException(
            "Cannot access configuration before create()",
        )

        return configuration.copy()
    }

    override fun reconfigure(config: VpnConfiguration) {
        val previousConfiguration = currentConfiguration
            ?: throw IllegalStateException("Cannot reconfigure before create()")

        if (previousConfiguration == config) {
            return
        }

        require(config.interfaceName == interfaceName) {
            "Cannot reconfigure interface `$interfaceName` using `${config.interfaceName}`"
        }

        requireValidConfiguration(config)

        try {
            applyMtu(config)
            applyAddresses(config)
            applyRoutes(config)
            applyDns(config)
        } catch (throwable: Throwable) {
            restoreConfiguration(previousConfiguration)
            throw IllegalStateException(
                "Failed to reconfigure interface `$interfaceName`: ${throwable.message ?: "unknown"}",
                throwable,
            )
        }

        currentConfiguration = config.copy()
    }

    override fun readInformation(): VpnInterfaceInformation? {
        return commandExecutor.readInformation(interfaceName)
    }

    private fun applyMtu(config: VpnConfiguration) {
        val mtu = config.mtu ?: return
        commandExecutor.applyMtu(config.interfaceName, mtu)
    }

    private fun applyAddresses(config: VpnConfiguration) {
        commandExecutor.applyAddresses(config.interfaceName, config.addresses.toList())
    }

    private fun applyRoutes(config: VpnConfiguration) {
        commandExecutor.applyRoutes(
            interfaceName = config.interfaceName,
            routes = config.peers
                .flatMap { peer -> peer.allowedIps }
                .filter { route -> route.isNotBlank() }
                .distinct()
                .sorted(),
        )
    }

    private fun applyDns(config: VpnConfiguration) {
        commandExecutor.applyDns(config.interfaceName, config.dnsDomainPool)
    }

    private fun restoreConfiguration(config: VpnConfiguration) {
        try {
            applyMtu(config)
            applyAddresses(config)
            applyRoutes(config)
            applyDns(config)
        } catch (_: Throwable) {
            // rollback is best-effort; original failure remains primary
        }
    }
}

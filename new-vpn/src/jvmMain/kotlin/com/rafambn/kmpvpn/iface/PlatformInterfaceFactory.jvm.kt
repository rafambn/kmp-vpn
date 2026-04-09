package com.rafambn.kmpvpn.iface

import com.rafambn.kmpvpn.VpnConfiguration

internal object JvmPlatformInterfaceFactory {
    fun create(configuration: VpnConfiguration): VpnInterface {
        return when (
            System.getProperty(
                JvmPlatformProperties.INTERFACE_MODE,
                JvmPlatformProperties.INTERFACE_MODE_PRODUCTION,
            ).lowercase()
        ) {
            JvmPlatformProperties.INTERFACE_MODE_IN_MEMORY -> createInMemory()
            else -> createProduction()
        }
    }

    fun createProduction(): VpnInterface {
        return JvmVpnInterface(
            commandExecutor = DaemonBackedInterfaceCommandExecutor(),
            // The repo still only has the in-process TUN provider abstraction; the control path is now production-backed.
            tunProvider = InMemoryTunProvider(),
        )
    }

    fun createInMemory(): VpnInterface {
        return JvmVpnInterface(
            commandExecutor = InMemoryInterfaceCommandExecutor(),
            tunProvider = InMemoryTunProvider(),
        )
    }
}

actual object PlatformInterfaceFactory {
    actual fun create(configuration: VpnConfiguration): VpnInterface {
        return JvmPlatformInterfaceFactory.create(configuration)
    }
}

package com.rafambn.kmpvpn.iface

import com.rafambn.kmpvpn.VpnConfiguration

actual object PlatformInterfaceFactory {
    actual fun create(configuration: VpnConfiguration): VpnInterface {
        return when (
            System.getProperty(
                JvmPlatformProperties.INTERFACE_MODE,
                JvmPlatformProperties.INTERFACE_MODE_PRODUCTION,
            ).lowercase()
        ) {
            JvmPlatformProperties.INTERFACE_MODE_IN_MEMORY -> JvmVpnInterface(
                interfaceName = configuration.interfaceName,
                commandExecutor = InMemoryInterfaceCommandExecutor(),
                tunProvider = InMemoryTunProvider(),
            )
            else -> JvmVpnInterface(
                interfaceName = configuration.interfaceName,
                commandExecutor = DaemonBackedInterfaceCommandExecutor(),
                tunProvider = InMemoryTunProvider(),
            )
        }
    }
}

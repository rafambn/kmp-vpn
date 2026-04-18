package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.VpnConfiguration
import com.rafambn.wgkotlin.daemon.protocol.DEFAULT_DAEMON_HOST
import com.rafambn.wgkotlin.daemon.protocol.DEFAULT_DAEMON_PORT
import com.rafambn.wgkotlin.session.DuplexChannelPipe
import java.time.Duration
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal object JvmInterfaceKoinBootstrap {
    private val baseModule: Module = module {

        factory<InterfaceCommandExecutor> {
            when (
                System.getProperty(
                    JvmInterfaceProperties.INTERFACE_MODE,
                    JvmInterfaceProperties.INTERFACE_MODE_PRODUCTION,
                ).lowercase()
            ) {
                JvmInterfaceProperties.INTERFACE_MODE_IN_MEMORY -> InMemoryInterfaceCommandExecutor()
                else -> DaemonBackedInterfaceCommandExecutor(
                    host = System.getProperty(JvmInterfaceProperties.DAEMON_HOST, DEFAULT_DAEMON_HOST),
                    port = System.getProperty(JvmInterfaceProperties.DAEMON_PORT)?.toIntOrNull() ?: DEFAULT_DAEMON_PORT,
                    timeout = Duration.ofMillis(
                        System.getProperty(JvmInterfaceProperties.DAEMON_TIMEOUT_MILLIS)?.toLongOrNull() ?: 15_000L,
                    ),
                )
            }
        }

        factory<InterfaceManager> { params ->
            val configuration = params.get<VpnConfiguration>()
            val tunPipe = params.get<DuplexChannelPipe<ByteArray>>()
            JvmInterfaceManager(
                interfaceName = configuration.interfaceName,
                commandExecutor = get(),
                tunPipe = tunPipe,
            )
        }
    }

    fun createInterfaceManager(
        configuration: VpnConfiguration,
        tunPipe: DuplexChannelPipe<ByteArray>,
        overrideModules: List<Module> = emptyList(),
    ): InterfaceManager {
        val app = koinApplication {
            allowOverride(true)
            modules(listOf(baseModule) + overrideModules)
        }

        return try {
            app.koin.get(parameters = { parametersOf(configuration, tunPipe) })
        } finally {
            app.close()
        }
    }
}

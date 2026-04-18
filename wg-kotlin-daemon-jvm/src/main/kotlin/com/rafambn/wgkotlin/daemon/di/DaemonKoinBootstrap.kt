package com.rafambn.wgkotlin.daemon.di

import com.rafambn.wgkotlin.daemon.DaemonImpl
import com.rafambn.wgkotlin.daemon.command.CommonsExecProcessLauncher
import com.rafambn.wgkotlin.daemon.command.ProcessLauncher
import com.rafambn.wgkotlin.daemon.platformAdapter.PlatformAdapter
import com.rafambn.wgkotlin.daemon.platformAdapter.PlatformAdapterFactory
import com.rafambn.wgkotlin.daemon.protocol.DaemonApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal object DaemonKoinBootstrap {
    private val baseModule: Module = module {
        single<ProcessLauncher> { CommonsExecProcessLauncher() }
        single<PlatformAdapter> { PlatformAdapterFactory.fromOs(processLauncher = get()) }
        single<DaemonApi> {
            DaemonImpl(adapter = get())
        }
    }

    fun resolveDependencies(overrideModules: List<Module> = emptyList()): DaemonRuntimeDependencies {
        val app = koinApplication {
            allowOverride(true)
            modules(listOf(baseModule) + overrideModules)
        }

        return try {
            DaemonRuntimeDependencies(
                adapter = app.koin.get(),
                service = app.koin.get(),
            )
        } finally {
            app.close()
        }
    }
}

internal data class DaemonRuntimeDependencies(
    val adapter: PlatformAdapter,
    val service: DaemonApi,
)

package com.rafambn.wgkotlin.iface

import com.rafambn.wgkotlin.daemon.protocol.TunSessionConfig
import com.rafambn.wgkotlin.session.DuplexChannelPipe

interface InterfaceCommandExecutor {
    fun openSession(
        config: TunSessionConfig,
        pipe: DuplexChannelPipe<ByteArray>,
        onFailure: (Throwable) -> Unit = {},
    ): AutoCloseable
}

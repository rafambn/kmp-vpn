package com.rafambn.wgkotlin.iface

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue

class DaemonBackedInterfaceCommandExecutorTest {

    @Test
    fun executorConstructsLazilyWithoutImmediateConnection() {
        val executor = DaemonBackedInterfaceCommandExecutor(
            host = "127.0.0.1",
            port = 65535,
            timeout = Duration.ofMillis(250),
        )

        assertTrue(executor is InterfaceCommandExecutor)
    }
}

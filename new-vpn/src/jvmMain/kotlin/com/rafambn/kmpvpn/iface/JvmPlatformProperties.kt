package com.rafambn.kmpvpn.iface

internal object JvmPlatformProperties {
    const val INTERFACE_MODE: String = "kmpvpn.platform.interface.mode"
    const val INTERFACE_MODE_IN_MEMORY: String = "in-memory"
    const val INTERFACE_MODE_PRODUCTION: String = "production"

    const val RUNTIME_MODE: String = "kmpvpn.platform.runtime.mode"
    const val RUNTIME_MODE_DISABLED: String = "disabled"
    const val RUNTIME_MODE_PRODUCTION: String = "production"

    const val DAEMON_HOST: String = "kmpvpn.daemon.host"
    const val DAEMON_PORT: String = "kmpvpn.daemon.port"
    const val DAEMON_TIMEOUT_MILLIS: String = "kmpvpn.daemon.timeoutMillis"

    const val RUNTIME_IDLE_DELAY_MILLIS: String = "kmpvpn.runtime.idleDelayMillis"
    const val RUNTIME_RECEIVE_TIMEOUT_MILLIS: String = "kmpvpn.runtime.receiveTimeoutMillis"
    const val RUNTIME_PERIODIC_INTERVAL_MILLIS: String = "kmpvpn.runtime.periodicIntervalMillis"
}

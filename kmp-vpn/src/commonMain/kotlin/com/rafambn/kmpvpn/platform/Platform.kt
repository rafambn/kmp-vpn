package com.rafambn.kmpvpn.platform

/**
 * Enum representing different operating systems
 */
enum class OperatingSystem {
    LINUX,
    MACOS,
    WINDOWS,
    UNKNOWN
}

/**
 * Expect declaration for platform detection
 */
expect object Platform {
    val currentOS: OperatingSystem
}

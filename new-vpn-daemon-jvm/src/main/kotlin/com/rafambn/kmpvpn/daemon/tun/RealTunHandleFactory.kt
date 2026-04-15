package com.rafambn.kmpvpn.daemon.tun

/**
 * Factory for creating real TUN device handles.
 *
 * This factory creates RealTunHandle instances that use the native TUN device
 * through the tun-rs Rust library, providing cross-platform support for Linux,
 * macOS, and Windows.
 */
internal class RealTunHandleFactory(
    private val ipv4Address: String = "10.0.0.1",
    private val prefixLength: UByte = 24u,
) : TunHandleFactory {

    private val logger = org.slf4j.LoggerFactory.getLogger(RealTunHandleFactory::class.java)

    override fun open(interfaceName: String): TunHandle {
        logger.info("Creating real TUN handle for interface: $interfaceName")

        val handle = RealTunHandle(
            interfaceName = interfaceName,
            ipv4Address = ipv4Address,
            prefixLength = prefixLength,
        )

        // Note: The actual device opening happens in packetIO() after interface
        // is created by the daemon, as the interface needs to exist first.

        return handle
    }
}

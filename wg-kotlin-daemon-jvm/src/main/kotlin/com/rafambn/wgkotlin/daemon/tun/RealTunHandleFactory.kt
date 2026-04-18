package com.rafambn.wgkotlin.daemon.tun

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

        return RealTunHandle(
            requestedInterfaceName = interfaceName,
            ipv4Address = ipv4Address,
            prefixLength = prefixLength,
        )
    }

    companion object {
        fun fromConfig(config: com.rafambn.wgkotlin.daemon.protocol.TunSessionConfig): RealTunHandleFactory {
            val ipv4Address = config.addresses
                .map { address -> address.substringBefore("/") to address.substringAfter("/", "") }
                .firstOrNull { (ip, prefix) -> ip.isNotBlank() && !ip.contains(":") && prefix.isNotBlank() }
                ?: throw IllegalArgumentException("Tun session requires at least one IPv4 address")

            return RealTunHandleFactory(
                ipv4Address = ipv4Address.first,
                prefixLength = ipv4Address.second.toUByte(),
            )
        }
    }
}

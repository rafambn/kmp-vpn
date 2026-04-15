package com.rafambn.kmpvpn.daemon.tun

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.new_vpn.TunDevice

/**
 * Real TUN device handle implementation using tun-rs via Rust FFI.
 *
 * This implementation uses the Rust tun-rs library through uniffi bindings
 * to provide cross-platform TUN device support for Linux, macOS, and Windows.
 */
internal class RealTunHandle(
    override val interfaceName: String,
    private val ipv4Address: String,
    private val prefixLength: UByte = 24u,
) : TunHandle {

    private val logger = org.slf4j.LoggerFactory.getLogger(RealTunHandle::class.java)

    // The actual Rust TUN device via uniffi
    private var tunDevice: TunDevice? = null
    private var isClosed = false

    suspend fun openDevice() {
        logger.info("Opening TUN device: $interfaceName with IP $ipv4Address/$prefixLength")

        // Load WinTUN DLL on Windows before attempting to open device
        WindowsDllLoader.loadWinTun()

        withContext(Dispatchers.IO) {
            try {
                // Create the TUN device via uniffi bindings
                tunDevice = TunDevice(interfaceName)

                // Open the device with the specified IPv4 address
                tunDevice?.open(ipv4Address, prefixLength)

                logger.info("TUN device opened successfully: $interfaceName")
            } catch (e: Exception) {
                logger.error("Failed to open TUN device: $interfaceName", e)
                isClosed = true
                throw e
            }
        }
    }

    override suspend fun readPacket(): ByteArray? {
        if (isClosed || tunDevice == null) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // Read a packet from the Rust TUN device
                val packet = tunDevice?.readPacket()
                logger.trace("Read ${packet?.size ?: 0} bytes from TUN device")
                packet
            } catch (e: Exception) {
                logger.error("Failed to read packet from TUN device", e)
                null
            }
        }
    }

    override suspend fun writePacket(packet: ByteArray) {
        if (isClosed || tunDevice == null) {
            logger.warn("Attempted to write to closed TUN device")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Write packet to the Rust TUN device
                tunDevice?.writePacket(packet)
                logger.trace("Wrote ${packet.size} bytes to TUN device")
            } catch (e: Exception) {
                logger.error("Failed to write packet to TUN device", e)
                throw e
            }
        }
    }

    override fun close() {
        if (isClosed) {
            return
        }

        isClosed = true
        logger.info("Closing TUN device: $interfaceName")

        try {
            // Close the Rust TUN device via uniffi
            tunDevice?.close()
            tunDevice = null
        } catch (e: Exception) {
            logger.error("Error closing TUN device", e)
        }
    }
}

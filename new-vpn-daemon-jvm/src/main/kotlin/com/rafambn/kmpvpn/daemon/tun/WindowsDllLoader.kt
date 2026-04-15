package com.rafambn.kmpvpn.daemon.tun

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object WindowsDllLoader {
    private val logger = org.slf4j.LoggerFactory.getLogger(WindowsDllLoader::class.java)

    fun loadWinTun() {
        if (!isWindows()) {
            logger.debug("Not running on Windows, skipping WinTUN DLL load")
            return
        }

        try {
            val arch = detectArchitecture()
            val dllName = when (arch) {
                Architecture.X86 -> "wintun-x86.dll"
                Architecture.X64 -> "wintun-x64.dll"
                Architecture.ARM64 -> "wintun-arm64.dll"
            }

            logger.info("Loading WinTUN DLL for architecture: $arch ($dllName)")
            loadDllFromResources(dllName)
        } catch (e: Exception) {
            logger.error("Failed to load WinTUN DLL", e)
            throw e
        }
    }

    private fun detectArchitecture(): Architecture {
        val arch = System.getProperty("os.arch").lowercase()
        val bits = System.getProperty("sun.arch.data.model")

        return when {
            arch.contains("amd64") || arch.contains("x86_64") -> Architecture.X64
            arch.contains("x86") || arch.contains("i386") || arch.contains("i486") || arch.contains("i586") || arch.contains("i686") -> Architecture.X86
            arch.contains("aarch64") || arch.contains("arm64") -> Architecture.ARM64
            bits == "64" && (arch.contains("arm") || arch.contains("aarch")) -> Architecture.ARM64
            else -> throw IllegalStateException("Unsupported Windows architecture: $arch (bits: $bits)")
        }
    }

    private fun loadDllFromResources(dllName: String) {
        // First try to load from system PATH
        try {
            System.loadLibrary(dllName.removeSuffix(".dll"))
            logger.info("Successfully loaded WinTUN DLL from system PATH")
            return
        } catch (e: UnsatisfiedLinkError) {
            logger.debug("WinTUN DLL not found in system PATH, attempting to load from resources")
        }

        // Load from resources
        val resourcePath = "/wintun/$dllName"
        val resource = WindowsDllLoader::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("WinTUN DLL not found in resources: $resourcePath")

        val tempFile = Files.createTempFile("wintun", ".dll")
        tempFile.toFile().deleteOnExit()

        resource.use { input ->
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
        }

        logger.debug("Extracted WinTUN DLL to temporary location: ${tempFile.toAbsolutePath()}")

        // Load the DLL from the temporary location
        System.load(tempFile.toAbsolutePath().toString())
        logger.info("Successfully loaded WinTUN DLL from resources")
    }

    private fun isWindows(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        return osName.contains("win")
    }

    private enum class Architecture {
        X86,
        X64,
        ARM64,
    }
}

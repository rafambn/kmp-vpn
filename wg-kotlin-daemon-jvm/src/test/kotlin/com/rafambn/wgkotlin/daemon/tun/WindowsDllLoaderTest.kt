package com.rafambn.wgkotlin.daemon.tun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsDllLoaderTest {

    @Test
    fun detectArchitectureMapsKnownValues() {
        assertEquals(
            WindowsDllLoader.Architecture.X64,
            WindowsDllLoader.detectArchitecture(archProperty = "amd64", bitsProperty = "64"),
        )
        assertEquals(
            WindowsDllLoader.Architecture.X64,
            WindowsDllLoader.detectArchitecture(archProperty = "x86_64", bitsProperty = "64"),
        )
        assertEquals(
            WindowsDllLoader.Architecture.X86,
            WindowsDllLoader.detectArchitecture(archProperty = "x86", bitsProperty = "32"),
        )
        assertEquals(
            WindowsDllLoader.Architecture.ARM64,
            WindowsDllLoader.detectArchitecture(archProperty = "aarch64", bitsProperty = "64"),
        )
        assertEquals(
            WindowsDllLoader.Architecture.ARM64,
            WindowsDllLoader.detectArchitecture(archProperty = "arm", bitsProperty = "64"),
        )
    }

    @Test
    fun prepareWinTunDllPathSkipsOnNonWindowsHosts() {
        val originalOsName = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Linux")
            assertNull(WindowsDllLoader.prepareWinTunDllPath())
        } finally {
            System.setProperty("os.name", originalOsName)
        }
    }
}

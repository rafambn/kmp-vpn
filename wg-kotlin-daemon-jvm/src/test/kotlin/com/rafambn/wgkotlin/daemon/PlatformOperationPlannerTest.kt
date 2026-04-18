package com.rafambn.wgkotlin.daemon

import com.rafambn.wgkotlin.daemon.planner.ApplyDns
import com.rafambn.wgkotlin.daemon.planner.ApplyMtu
import com.rafambn.wgkotlin.daemon.planner.ApplyRoutes
import com.rafambn.wgkotlin.daemon.command.CommandBinary
import com.rafambn.wgkotlin.daemon.planner.CreateInterface
import com.rafambn.wgkotlin.daemon.planner.InterfaceExists
import com.rafambn.wgkotlin.daemon.planner.LinuxOperationPlanner
import com.rafambn.wgkotlin.daemon.planner.MacOsOperationPlanner
import com.rafambn.wgkotlin.daemon.planner.PlatformOperationPlanner
import com.rafambn.wgkotlin.daemon.planner.ReadInterfaceInformation
import com.rafambn.wgkotlin.daemon.planner.SetInterfaceState
import com.rafambn.wgkotlin.daemon.planner.WindowsOperationPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlatformOperationPlannerTest {

    @Test
    fun factorySelectsPlannerByOsName() {
        assertIs<LinuxOperationPlanner>(PlatformOperationPlanner.fromOs("Linux"))
        assertIs<MacOsOperationPlanner>(PlatformOperationPlanner.fromOs("Mac OS X"))
        assertIs<WindowsOperationPlanner>(PlatformOperationPlanner.fromOs("Windows 11"))
    }

    @Test
    fun macPlannerProducesIfconfigAndScutilSteps() {
        val planner = MacOsOperationPlanner()
        val exists = planner.plan(InterfaceExists("wg0"))
        val setUp = planner.plan(SetInterfaceState(interfaceName = "wg0", up = true))
        val route = planner.plan(
            ApplyRoutes(
                interfaceName = "wg0",
                routes = listOf("10.0.0.0/24"),
            ),
        )
        val dns = planner.plan(
            ApplyDns(
                interfaceName = "utun7",
                dnsDomainPool = (
                    listOf("corp.local", "dev.local") to
                        listOf("10.0.0.53", "10.0.0.54")
                    ),
            ),
        )

        assertEquals(CommandBinary.IFCONFIG, exists.steps.single().invocation.binary)
        assertEquals(listOf("wg0"), exists.steps.single().invocation.arguments)
        assertEquals(CommandBinary.IFCONFIG, setUp.steps.single().invocation.binary)
        assertEquals(listOf("wg0", "up"), setUp.steps.single().invocation.arguments)
        assertEquals(CommandBinary.ROUTE, route.steps.first().invocation.binary)
        assertEquals(CommandBinary.SCUTIL, dns.steps.first().invocation.binary)
        assertTrue(dns.steps.first().invocation.stdin.orEmpty().contains("remove State:/Network/Service/utun7/DNS"))
        assertEquals(CommandBinary.SCUTIL, dns.steps[1].invocation.binary)
        assertTrue(dns.steps[1].invocation.stdin.orEmpty().contains("d.add SupplementalMatchDomains corp.local dev.local"))
        assertEquals(3, dns.steps.size)
    }

    @Test
    fun windowsPlannerProducesNetshReadAndWriteSteps() {
        val planner = WindowsOperationPlanner()
        val exists = planner.plan(InterfaceExists("wg0"))
        val setDown = planner.plan(SetInterfaceState(interfaceName = "wg0", up = false))
        val mtu = planner.plan(ApplyMtu(interfaceName = "wg0", mtu = 1420))
        val dns = planner.plan(
            ApplyDns(
                interfaceName = "wg0",
                dnsDomainPool = (
                    listOf("corp.local", "dev.local") to
                        listOf("1.1.1.1", "9.9.9.9")
                    ),
            ),
        )
        val interfaceInfo = planner.plan(ReadInterfaceInformation(interfaceName = "wg0"))

        assertEquals(CommandBinary.NETSH, exists.steps.single().invocation.binary)
        assertEquals(
            listOf("interface", "show", "interface", "name=wg0"),
            exists.steps.single().invocation.arguments,
        )
        assertEquals(CommandBinary.NETSH, setDown.steps.single().invocation.binary)
        assertEquals(
            listOf("interface", "set", "interface", "name=wg0", "admin=DISABLED"),
            setDown.steps.single().invocation.arguments,
        )
        assertEquals(CommandBinary.NETSH, mtu.steps.single().invocation.binary)
        assertTrue(mtu.steps.single().invocation.arguments.contains("mtu=1420"))
        assertEquals(CommandBinary.POWERSHELL, dns.steps.first().invocation.binary)
        assertTrue(dns.steps.last().invocation.arguments.last().contains("Add-DnsClientNrptRule"))
        val dnsScripts = dns.steps.map { step -> step.invocation.arguments.last() }
        assertTrue(dnsScripts.any { script -> script.contains(".corp.local") && script.contains("'1.1.1.1'") && script.contains("'9.9.9.9'") })
        assertTrue(dnsScripts.any { script -> script.contains(".dev.local") && script.contains("'1.1.1.1'") && script.contains("'9.9.9.9'") })
        assertEquals(CommandBinary.NETSH, interfaceInfo.steps.single().invocation.binary)
        assertEquals(listOf("interface", "show", "interface", "name=wg0"), interfaceInfo.steps.single().invocation.arguments)
    }

    @Test
    fun readPlansUsePlatformInterfaceCommandsOnly() {
        val linux = LinuxOperationPlanner()
        val mac = MacOsOperationPlanner()
        val windows = WindowsOperationPlanner()

        val linuxReadStep = linux.plan(ReadInterfaceInformation("wg0")).steps.single().invocation
        val macReadStep = mac.plan(ReadInterfaceInformation("wg0")).steps.single().invocation
        val windowsReadStep = windows.plan(ReadInterfaceInformation("wg0")).steps.single().invocation

        assertEquals(CommandBinary.IP, linuxReadStep.binary)
        assertEquals(listOf("-details", "address", "show", "dev", "wg0"), linuxReadStep.arguments)
        assertEquals(CommandBinary.IFCONFIG, macReadStep.binary)
        assertEquals(CommandBinary.NETSH, windowsReadStep.binary)
    }

    @Test
    fun linuxCreateTreatsOnlyZeroExitAsSuccess() {
        val linux = LinuxOperationPlanner()

        val step = linux.plan(CreateInterface("wg0")).steps.single()

        assertEquals(setOf(0), step.acceptedExitCodes)
        assertEquals(CommandBinary.IP, step.invocation.binary)
        assertEquals(listOf("tuntap", "add", "dev", "wg0", "mode", "tun"), step.invocation.arguments)
    }
}

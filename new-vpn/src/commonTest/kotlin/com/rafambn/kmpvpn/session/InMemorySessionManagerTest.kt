package com.rafambn.kmpvpn.session

import com.rafambn.kmpvpn.DefaultVpnAdapterConfiguration
import com.rafambn.kmpvpn.VpnPeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemorySessionManagerTest {

    @Test
    fun ensureSessionsCreatesActiveSessionsForAllPeers() {
        val manager = InMemorySessionManager()

        manager.ensureSessions(configurationWithPeers("peer-a", "peer-b"))

        assertEquals(2, manager.sessions().size)
        assertTrue(manager.sessions().all { session -> session.isActive })
    }

    @Test
    fun reconcileSessionsRemovesMissingPeers() {
        val manager = InMemorySessionManager()

        manager.ensureSessions(configurationWithPeers("peer-a", "peer-b"))
        manager.reconcileSessions(configurationWithPeers("peer-b"))

        assertNull(manager.session("peer-a"))
        assertNotNull(manager.session("peer-b"))
    }

    @Test
    fun closeAllMarksSessionsAsInactive() {
        val manager = InMemorySessionManager()

        manager.ensureSessions(configurationWithPeers("peer-a"))
        manager.closeAll()

        val session = manager.session("peer-a")
        assertNotNull(session)
        assertFalse(session.isActive)
    }

    private fun configurationWithPeers(vararg peerKeys: String): DefaultVpnAdapterConfiguration {
        return DefaultVpnAdapterConfiguration(
            privateKey = "private-key",
            publicKey = "public-key",
            peers = peerKeys.map { key -> VpnPeer(publicKey = key) },
        )
    }
}

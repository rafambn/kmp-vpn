package com.rafambn.wgkotlin

sealed class VpnState {
    data object Stopped : VpnState()
    data object Running : VpnState()
}

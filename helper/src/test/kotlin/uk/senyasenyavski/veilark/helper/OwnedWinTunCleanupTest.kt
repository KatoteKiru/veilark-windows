package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnedWinTunCleanupTest {
  private val tunnel = ReadyTunnel(19, 14918723521478656, "Veilark", "sing-tun Tunnel")
  private val adapter = NetworkAdapter(tunnel.index, tunnel.luid, tunnel.alias, tunnel.description, false, 1280, 0, 0)

  @Test fun `cleanup identity is exact and cannot select other adapters`() {
    assertTrue(OwnedWinTunCleanup.sameAdapter(tunnel, adapter))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(index = 20)))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(luid = tunnel.luid + 1)))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(alias = "Other VPN")))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(description = "Intel Ethernet")))
    assertFalse(OwnedWinTunCleanup.eligible(tunnel.copy(alias = "Other VPN")))
    assertFalse(OwnedWinTunCleanup.eligible(tunnel.copy(description = "WireGuard Tunnel")))
    assertFalse(OwnedWinTunCleanup.eligible(tunnel.copy(index = 0)))
  }

  @Test fun `only literal WinTun GUID instances are accepted`() {
    assertTrue(OwnedWinTunCleanup.INSTANCE_ID.matches("SWD\\WINTUN\\{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}"))
    assertFalse(OwnedWinTunCleanup.INSTANCE_ID.matches("SWD\\WINTUN\\*"))
    assertFalse(OwnedWinTunCleanup.INSTANCE_ID.matches("ROOT\\NET\\0001"))
    assertFalse(OwnedWinTunCleanup.INSTANCE_ID.matches("SWD\\WINTUN\\'; Remove-Item *; '"))
    assertTrue(OwnedWinTunCleanup.GUID.matches("{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}"))
    assertFalse(OwnedWinTunCleanup.GUID.matches("{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}'"))
  }
}

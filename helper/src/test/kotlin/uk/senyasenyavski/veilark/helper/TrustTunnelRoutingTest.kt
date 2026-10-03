package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.GeoRoutingAssets
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.VpnEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrustTunnelRoutingTest {
  @Test fun `bare numeric IPv4 and IPv6 VPN overrides are normalized without DNS names`() {
    for (address in listOf("192.0.2.1", "2001:db8::1")) {
      val plan = TrustTunnelRouting.plan(profile(RoutingMode.Manual).copy(
        appliedRouting = RoutingSettings(mode = RoutingMode.Manual,
          directEntries = address, vpnEntries = address),
      ))
      assertTrue(plan.exclusions.isEmpty())
    }
  }
  @Test
  fun `Manual identical network VPN override removes direct exclusion`() {
    val plan = TrustTunnelRouting.plan(profile(RoutingMode.Manual).copy(
      appliedRouting = RoutingSettings(mode = RoutingMode.Manual,
        directEntries = "10.0.0.0/8", vpnEntries = "10.0.0.0/8"),
    ))
    assertTrue(plan.exclusions.isEmpty())
  }

  @Test
  fun `Manual overlapping domain exceptions fail instead of silently bypassing VPN`() {
    assertFailsWith<IllegalArgumentException> {
      TrustTunnelRouting.plan(profile(RoutingMode.Manual).copy(
        appliedRouting = RoutingSettings(mode = RoutingMode.Manual,
          directEntries = "example.com", vpnEntries = "private.example.com"),
      ))
    }
  }

  @Test
  fun `Manual overlapping network exceptions fail instead of silently bypassing VPN`() {
    assertFailsWith<IllegalArgumentException> {
      TrustTunnelRouting.plan(profile(RoutingMode.Manual).copy(
        appliedRouting = RoutingSettings(mode = RoutingMode.Manual,
          directEntries = "10.0.0.0/8", vpnEntries = "10.1.0.0/16"),
      ))
    }
  }
  @Test
  fun `Russia direct replaces generated routing after preserving endpoint`() {
    val profile = profile(RoutingMode.RussiaDirect)
    val plan = TrustTunnelRouting.plan(profile)

    val result = TrustTunnelRouting.apply(TOML, plan)

    assertEquals("general", plan.vpnMode)
    assertTrue(result.contains("vpn_mode = \"general\""))
    assertTrue(result.contains("exclusions_tcp_early_ack_enabled = false"))
    assertTrue(result.contains("exclusions_preresolve_enabled = true"))
    assertTrue(result.contains("exclusions_preresolve_max_queries = 50"))
    assertTrue(result.contains("change_system_dns = true"))
    assertTrue(result.contains("mtu_size = 1350"))
    assertTrue(result.contains("\"5.8.0.0/13\""))
    assertTrue(result.contains("\"*.ru\""))
    assertTrue(result.contains("password = \"test-secret\""))
    assertEquals(1, Regex("""(?m)^vpn_mode\s*=""").findAll(result).count())
    assertFalse(result.contains("old.example"))
  }

  @Test
  fun `Russia VPN uses selective mode with the same exclusions`() {
    val plan = TrustTunnelRouting.plan(profile(RoutingMode.RussiaVpn))
    val result = TrustTunnelRouting.apply(TOML, plan)

    assertEquals("selective", plan.vpnMode)
    assertTrue(result.contains("vpn_mode = \"selective\""))
    assertEquals(listOf("5.8.0.0/13", "*.ru"), plan.exclusions)
    assertFalse(plan.exclusionsTcpEarlyAckEnabled)
    assertTrue(plan.exclusionsPreresolveEnabled)
  }

  @Test
  fun `All remains general with an empty list`() {
    val plan = TrustTunnelRouting.plan(profile(RoutingMode.All))
    val result = TrustTunnelRouting.apply(TOML, plan)

    assertEquals(TrustTunnelRoutingPlan("general", emptyList(), false, false, 50), plan)
    assertTrue(result.contains("exclusions = [\n]"))
    assertTrue(result.contains("exclusions_tcp_early_ack_enabled = false"))
  }

  @Test
  fun `Manual keeps direct entries and lets explicit VPN entry win`() {
    val base = profile(RoutingMode.Manual)
    val configured = base.copy(
      appliedRouting = RoutingSettings(
        mode = RoutingMode.Manual,
        directEntries = "example.ru 10.0.0.0/8 direct.test",
        vpnEntries = "example.ru vpn.test",
      ),
    )

    val plan = TrustTunnelRouting.plan(configured)

    assertEquals("general", plan.vpnMode)
    assertEquals(listOf("10.0.0.0/8", "direct.test", "*.direct.test"), plan.exclusions)
    assertFalse(plan.exclusionsTcpEarlyAckEnabled)
    assertTrue(plan.exclusionsPreresolveEnabled)
  }

  @Test
  fun `geo mode without preflight payload fails before TOML generation`() {
    assertFailsWith<GeoRoutingUnavailableException> {
      TrustTunnelRouting.plan(profile(RoutingMode.RussiaDirect).copy(geoRoutingAssets = null))
    }
  }

  @Test
  fun `native config contract rejects an unexpanded deeplink`() {
    assertFailsWith<IllegalArgumentException> {
      TrustTunnelRouting.validateNativeConfigContract(
        """
        [endpoint]
        hostname = "vpn.example.test"
        [listener.tun]
        bound_if = ""
        tt://not-expanded
        """.trimIndent(),
      )
    }
  }

  private fun profile(mode: RoutingMode) = Profile(
    id = "trust",
    name = "TrustTunnel",
    engine = VpnEngine.TrustTunnel,
    config = "tt://opaque",
    nodes = listOf(Node("tt", "TT", "TrustTunnel")),
    sourceLabel = "test",
    appliedRouting = RoutingSettings(mode = mode),
    geoRoutingAssets = GeoRoutingAssets(
      "C:\\geo\\geoip-ru.srs",
      "C:\\geo\\geosite-category-ru.srs",
      listOf("5.8.0.0/13", "*.ru"),
    ),
  )

  private companion object {
    val TOML = """
      loglevel = "info"
      vpn_mode = "selective"
      exclusions = [
        "old.example",
        "192.0.2.0/24",
      ]

      [endpoint]
      hostname = "vpn.example.test"
      password = "test-secret"

      [listener.tun]
      bound_if = ""
      mtu_size = 1350
    """.trimIndent()
  }
}

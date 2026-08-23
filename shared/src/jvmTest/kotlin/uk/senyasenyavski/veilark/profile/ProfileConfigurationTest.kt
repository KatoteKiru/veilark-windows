package uk.senyasenyavski.veilark.profile

import com.example.veilark.profile.SubscriptionParser
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.GeoRoutingAssets
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.VpnEngine

class ProfileConfigurationTest {
  @Test
  fun `applies manual routing and TLS fragmentation to sing-box`() {
    val compiled = SubscriptionParser().compile(
      "trojan://secret@203.0.113.2:443?security=tls&sni=example.com#NL".toByteArray(),
    )
    val configured = ProfileConfiguration.apply(
      Profile(
        "id",
        "profile",
        VpnEngine.SingBox,
        compiled.json,
        compiled.nodes.map { Node(it.tag, it.name, it.protocol) },
        "test",
      ),
      RoutingSettings(
        mode = RoutingMode.Manual,
        directEntries = "example.ru 10.0.0.0/8",
        vpnEntries = "youtube.com",
        tlsFragment = true,
      ),
      selectedNodeTag = compiled.nodes.single().tag,
    )
    val root = JSONObject(configured.config)

    assertEquals(6, root.getJSONObject("route").getJSONArray("rules").length())
    assertEquals(
      compiled.nodes.single().tag,
      root.getJSONObject("route").getString("final"),
    )
    assertEquals(
      "Veilark",
      root.getJSONArray("inbounds").getJSONObject(0).getString("interface_name"),
    )
    assertTrue(
      root.getJSONArray("outbounds").getJSONObject(1)
        .getJSONObject("tls").getBoolean("fragment"),
    )
  }

  @Test
  fun `keeps TrustTunnel source opaque until the wizard has run`() {
    val profile = Profile(
      "id",
      "TrustTunnel",
      VpnEngine.TrustTunnel,
      "tt://opaque",
      listOf(Node("tt", "TT", "TrustTunnel")),
      "test",
    )

    val configured = ProfileConfiguration.apply(profile, RoutingSettings(tlsFragment = true))

    assertEquals(profile.config, configured.config)
    assertEquals(RoutingSettings(tlsFragment = true), configured.appliedRouting)
    assertFalse(profile.config.contains("fragment"))
  }

  @Test
  fun `Russia direct uses local binary rule sets`() {
    val compiled = SubscriptionParser().compile(
      "trojan://secret@203.0.113.2:443?security=tls&sni=example.com#NL".toByteArray(),
    )
    val selectedTag = compiled.nodes.single().tag
    val configured = ProfileConfiguration.apply(
      Profile(
        "id",
        "profile",
        VpnEngine.SingBox,
        compiled.json,
        compiled.nodes.map { Node(it.tag, it.name, it.protocol) },
        "test",
      ),
      RoutingSettings(mode = RoutingMode.RussiaDirect),
      selectedTag,
      geoAssets(),
    )
    val route = JSONObject(configured.config).getJSONObject("route")

    assertEquals(selectedTag, route.getString("final"))
    assertEquals(2, route.getJSONArray("rule_set").length())
    assertEquals("local", route.getJSONArray("rule_set").getJSONObject(0).getString("type"))
    assertEquals("binary", route.getJSONArray("rule_set").getJSONObject(1).getString("format"))
    assertEquals("hijack-dns", route.getJSONArray("rules").getJSONObject(1).getString("action"))
    assertEquals("direct", route.getJSONArray("rules").getJSONObject(3).getString("outbound"))
  }

  @Test
  fun `Russia VPN routes only Russian rule sets through selected node`() {
    val compiled = SubscriptionParser().compile(
      "trojan://secret@203.0.113.2:443?security=tls&sni=example.com#NL".toByteArray(),
    )
    val selectedTag = compiled.nodes.single().tag
    val configured = ProfileConfiguration.apply(
      Profile(
        "id",
        "profile",
        VpnEngine.SingBox,
        compiled.json,
        compiled.nodes.map { Node(it.tag, it.name, it.protocol) },
        "test",
      ),
      RoutingSettings(mode = RoutingMode.RussiaVpn),
      selectedTag,
      geoAssets(),
    )
    val route = JSONObject(configured.config).getJSONObject("route")

    assertEquals("direct", route.getString("final"))
    assertEquals("hijack-dns", route.getJSONArray("rules").getJSONObject(1).getString("action"))
    assertEquals(selectedTag, route.getJSONArray("rules").getJSONObject(3).getString("outbound"))
    val secureDns = JSONObject(configured.config).getJSONObject("dns").getJSONArray("servers")
      .getJSONObject(1)
    assertEquals("direct", secureDns.getString("detour"))
  }

  @Test
  fun `TrustTunnel carries validated geo payload without changing deeplink`() {
    val profile = Profile(
      "id",
      "TrustTunnel",
      VpnEngine.TrustTunnel,
      "tt://opaque",
      listOf(Node("tt", "TT", "TrustTunnel")),
      "test",
    )
    val assets = geoAssets()

    val configured = ProfileConfiguration.apply(
      profile,
      RoutingSettings(mode = RoutingMode.RussiaDirect),
      geoRoutingAssets = assets,
    )

    assertEquals("tt://opaque", configured.config)
    assertEquals(RoutingMode.RussiaDirect, configured.appliedRouting.mode)
    assertEquals(assets, configured.geoRoutingAssets)
  }

  @Test
  fun `selects requested TrustTunnel endpoint configuration`() {
    val profile = Profile(
      "id",
      "TrustTunnel",
      VpnEngine.TrustTunnel,
      "tt://first",
      listOf(
        Node("first", "Frankfurt", "TrustTunnel"),
        Node("second", "Helsinki", "TrustTunnel"),
      ),
      "test",
      endpointConfigs = mapOf(
        "first" to "tt://first",
        "second" to "tt://second",
      ),
    )

    val configured = ProfileConfiguration.apply(
      profile,
      RoutingSettings(tlsFragment = true),
      selectedNodeTag = "second",
    )

    assertEquals("tt://second", configured.config)
    assertFalse(configured.config.contains("fragment"))
  }

  private fun geoAssets() = GeoRoutingAssets(
    geoIpRuPath = "C:\\Veilark\\geo\\geoip-ru.srs",
    geoSiteCategoryRuPath = "C:\\Veilark\\geo\\geosite-category-ru.srs",
    trustTunnelExclusions = listOf("5.8.0.0/13", ".ru"),
  )
}

package uk.senyasenyavski.veilark.profile

import com.example.veilark.profile.SubscriptionParser
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
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
    val routeRules = root.getJSONObject("route").getJSONArray("rules")
    assertEquals("1s", routeRules.getJSONObject(0).getString("timeout"))
    repeat(routeRules.length()) { index ->
      val rule = routeRules.getJSONObject(index)
      if (rule.has("outbound")) assertEquals("route", rule.getString("action"))
    }
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
    assertEquals("route", route.getJSONArray("rules").getJSONObject(3).getString("action"))
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
    assertEquals("route", route.getJSONArray("rules").getJSONObject(3).getString("action"))
    val secureDns = JSONObject(configured.config).getJSONObject("dns").getJSONArray("servers")
      .getJSONObject(1)
    assertEquals(selectedTag, secureDns.getString("detour"))
    val dns = JSONObject(configured.config).getJSONObject("dns")
    assertEquals("bootstrap-dns", dns.getString("final"))
    assertEquals(
      "secure-dns",
      dns.getJSONArray("rules").getJSONObject(0).getString("server"),
    )
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

  @Test
  fun `all generated routing modes pass bundled sing-box check`() {
    val checker = System.getenv("SING_BOX_CHECKER")?.takeIf { File(it).isFile } ?: return
    val geoIp = System.getenv("VEILARK_GEOIP_RU_SRS")?.takeIf { File(it).isFile } ?: return
    val geoSite = System.getenv("VEILARK_GEOSITE_RU_SRS")?.takeIf { File(it).isFile } ?: return
    val compiled = SubscriptionParser().compile(
      "trojan://secret@203.0.113.2:443?security=tls&sni=example.com#NL".toByteArray(),
    )
    val profile = Profile(
      "id",
      "profile",
      VpnEngine.SingBox,
      compiled.json,
      compiled.nodes.map { Node(it.tag, it.name, it.protocol) },
      "test",
    )
    val assets = GeoRoutingAssets(geoIp, geoSite, listOf("5.8.0.0/13", "*.ru"))
    val modes = listOf(
      RoutingSettings(mode = RoutingMode.All),
      RoutingSettings(
        mode = RoutingMode.Manual,
        directEntries = "example.ru 10.0.0.0/8",
        vpnEntries = "youtube.com",
      ),
      RoutingSettings(mode = RoutingMode.RussiaDirect),
      RoutingSettings(mode = RoutingMode.RussiaVpn),
    )

    modes.forEach { routing ->
      val configured = ProfileConfiguration.apply(
        profile,
        routing,
        compiled.nodes.single().tag,
        assets,
      )
      val config = Files.createTempFile("veilark-${routing.mode}-", ".json").toFile()
      try {
        config.writeText(configured.config)
        val process = ProcessBuilder(checker, "check", "-c", config.path)
          .redirectErrorStream(true)
          .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "sing-box rejected ${routing.mode}: $output")
      } finally {
        config.delete()
      }
    }
  }

  private fun geoAssets() = GeoRoutingAssets(
    geoIpRuPath = "C:\\Veilark\\geo\\geoip-ru.srs",
    geoSiteCategoryRuPath = "C:\\Veilark\\geo\\geosite-category-ru.srs",
    trustTunnelExclusions = listOf("5.8.0.0/13", ".ru"),
  )
}

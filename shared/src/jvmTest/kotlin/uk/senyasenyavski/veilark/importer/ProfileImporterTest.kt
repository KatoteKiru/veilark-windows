package uk.senyasenyavski.veilark.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.VpnEngine

class ProfileImporterTest {
  @Test
  fun `subscription request identifies the SFA client like Android`() {
    val userAgent = ProfileImporter.subscriptionUserAgent()

    assertTrue(userAgent.startsWith("SFA/1.13.14 Veilark/"))
    assertTrue(userAgent.contains(uk.senyasenyavski.veilark.update.UpdateClient.CURRENT_VERSION_NAME))
  }

  @Test
  fun `raw TrustTunnel link creates a TrustTunnel profile`() {
    val result = assertIs<ImportResult.Success>(
      ProfileImporter().fromText("tt://opaque-profile", "TrustTunnel"),
    )

    assertEquals(VpnEngine.TrustTunnel, result.profile.engine)
    assertEquals("tt://opaque-profile", result.profile.config)
    assertEquals("TrustTunnel", result.profile.nodes.single().protocol)
  }

  @Test
  fun `all TrustTunnel links are retained as selectable endpoints`() {
    val result = assertIs<ImportResult.Success>(
      ProfileImporter().fromText(
        "tt://first#Frankfurt\ntt://second#Helsinki",
        "Subscription",
      ),
    )

    assertEquals(2, result.profile.nodes.size)
    assertEquals(listOf("Frankfurt", "Helsinki"), result.profile.nodes.map { it.name })
    assertEquals(2, result.profile.endpointConfigs.size)
  }

  @Test
  fun `mixed subscription creates profiles for both engines`() {
    val result = assertIs<ImportResult.Success>(
      ProfileImporter().fromText(
        "vless://00000000-0000-0000-0000-000000000001@example.com:443?security=tls&type=tcp#sing\ntt://opaque#trust",
        "Mixed",
      ),
    )

    assertEquals(setOf(VpnEngine.SingBox, VpnEngine.TrustTunnel), result.profiles.map { it.engine }.toSet())
    assertEquals("trust", result.profiles.single { it.engine == VpnEngine.TrustTunnel }.nodes.single().name)
  }

  @Test
  fun `live HTTPS import retains encrypted refresh source when enabled`() {
    val url = System.getenv("VEILARK_TEST_SUBSCRIPTION")?.takeIf(String::isNotBlank)
      ?: return
    val result = assertIs<ImportResult.Success>(ProfileImporter().fromHttps(url))

    result.profiles.forEach { assertEquals(url, it.sourceUrl) }
  }

  @Test
  fun `TrustTunnel endpoint TOML creates a TrustTunnel profile`() {
    val result = assertIs<ImportResult.Success>(
      ProfileImporter().fromText(
        """
        [endpoint]
        addresses = ["vpn.example.com:443"]
        username = "user"
        """.trimIndent(),
        "endpoint.toml",
      ),
    )

    assertEquals(VpnEngine.TrustTunnel, result.profile.engine)
  }
}

package uk.senyasenyavski.veilark.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.VpnEngine

class ProfileImporterTest {
  @Test
  fun `malformed subscription URL cannot expose bearer or fragment`() {
    val secret = "synthetic-private-subscription-secret"
    val result = assertIs<ImportResult.Failure>(ProfileImporter().fromHttps(
      "https://example.invalid/$secret bad#k=$secret",
    ))
    assertEquals("Адрес подписки некорректен", result.safeMessage)
    assertFalse(result.safeMessage.contains(secret))
  }

  @Test
  fun `transport exception and nested cause cannot expose subscription secrets`() {
    val secret = "synthetic-private-key"
    for (failure in listOf(
      IllegalArgumentException("client_static_private_base64=$secret"),
      IllegalStateException("https://example.invalid/$secret", IllegalArgumentException(secret)),
    )) {
      val importer = ProfileImporter(fetchSubscription = { throw failure })
      val result = assertIs<ImportResult.Failure>(importer.fromHttps("https://example.invalid/subscription"))
      assertFalse(result.safeMessage.contains(secret))
      assertFalse(result.safeMessage.contains("example.invalid"))
      assertTrue(result.safeMessage.contains("Проверьте"))
    }
  }

  @Test
  fun `actionable entitlement errors remain readable without raw transport details`() {
    for (message in listOf(
      "Лимит устройств подписки исчерпан (HTTP 409)",
      "Подписка удалена или истекла (HTTP 410)",
      "Слишком много запросов к подписке (HTTP 429)",
    )) {
      val importer = ProfileImporter(fetchSubscription = { error(message) })
      val result = assertIs<ImportResult.Failure>(importer.fromHttps("https://example.invalid/subscription"))
      assertEquals(message, result.safeMessage)
    }
  }

  @Test
  fun `reserved Core profile cannot become sing-box even with proxy outbounds`() {
    val malformedCore = """{"schema":"veilark-argon-profile-v1","identity":{"client_static_private_base64":"synthetic-secret"},"outbounds":[{"type":"vless","tag":"wrong-engine","server":"example.invalid","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001"}]}"""
    for (allowCore in listOf(false, true)) {
      val result = assertIs<ImportResult.Failure>(
        ProfileImporter(allowCoreProfiles = allowCore).fromText(malformedCore),
        "Reserved Core documents must not fall back to sing-box (allowCore=$allowCore)",
      )
      if (!allowCore) assertTrue(result.safeMessage.contains("Fairway"))
      assertFalse(result.safeMessage.contains("synthetic-secret"))
      assertFalse(result.safeMessage.contains("example.invalid"))
    }
  }

  @Test
  fun `ambiguous Core schema cannot bypass engine discrimination`() {
    val outbounds = """"outbounds":[{"type":"vless","tag":"wrong-engine","server":"example.invalid","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001"}]"""
    for (schemas in listOf(
      """"schema":"veilark-argon-profile-v1","schema":"other"""",
      """"schema":"other","schema":"veilark-argon-profile-v1"""",
      """"schema":"other","\u0073chema":"veilark-argon-profile-v1"""",
    )) {
      assertIs<ImportResult.Failure>(ProfileImporter().fromText("{$schemas,$outbounds}"))
    }
  }

  @Test
  fun `subscription request identifies the SFA client like Android`() {
    val userAgent = ProfileImporter.subscriptionUserAgent()

    assertTrue(userAgent.startsWith("SFA/1.13.21 Veilark/"))
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

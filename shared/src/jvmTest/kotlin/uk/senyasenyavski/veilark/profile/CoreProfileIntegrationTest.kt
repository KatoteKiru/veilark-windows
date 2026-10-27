package uk.senyasenyavski.veilark.profile

import java.util.Base64
import kotlin.test.*
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.model.*

class CoreProfileIntegrationTest {
  private fun document(): String {
    val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    val pin = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    return """{"schema":"veilark-argon-profile-v1","engine":"argon-v1","beta":true,"name":"Fairway test","endpoint":{"ipv4":"192.0.2.1","port":443},"identity":{"client_static_private_base64":"$key","server_static_public_base64":"$pin"},"network":{"mtu":1360,"dns_ipv4":["1.1.1.1"]}}"""
  }

  @Test fun `Core import follows compiled release decision and explicit preparation yields its own engine`() {
    assertEquals(FairwayRelease.accepted, VpnEngine.VeilarkCore.availableInProduct)
    val defaultImport = ProfileImporter().fromText(document())
    if (FairwayRelease.accepted) {
      assertEquals(VpnEngine.VeilarkCore, assertIs<ImportResult.Success>(defaultImport).profile.engine)
      assertTrue(FairwayRelease.nativeSize > 0)
      assertTrue(FairwayRelease.nativeSha256.matches(Regex("[0-9a-f]{64}")))
    } else {
      assertIs<ImportResult.Failure>(defaultImport)
      assertEquals(0L, FairwayRelease.nativeSize)
      assertEquals("", FairwayRelease.nativeSha256)
    }
    val profile = assertIs<ImportResult.Success>(ProfileImporter(allowCoreProfiles = true).fromText(document())).profile
    assertEquals(VpnEngine.VeilarkCore, profile.engine)
    assertEquals("Fairway test", profile.name)
    assertEquals(document(), profile.config)
    assertEquals("Fairway", profile.nodes.single().protocol)
    assertTrue(profile.endpointConfigs.isEmpty())
  }

  @Test fun `malformed or other wire profiles never fall through into legacy parsing`() {
    val importer = ProfileImporter(allowCoreProfiles = true)
    for (invalid in listOf(
      document().replace("192.0.2.1", "example.invalid"),
      document().replace("profile-v1", "profile-v2").replace("\"engine\":", "\"wire_profile\":{\"id\":2,\"feedback_version\":1},\"engine\":"),
      document().replace("\"schema\":", "\"schema\":\"other\",\"schema\":"),
    )) assertIs<ImportResult.Failure>(importer.fromText(invalid))
  }

  @Test fun `Core routing retains exact credential envelope and never writes sing box fields`() {
    val original = assertIs<ImportResult.Success>(ProfileImporter(allowCoreProfiles = true).fromText(document())).profile
    val geo = GeoRoutingAssets("unused", "unused", listOf("5.8.0.0/24", "ozon.ru"))
    val result = ProfileConfiguration.apply(original, RoutingSettings(RoutingMode.RussiaDirect), geoRoutingAssets = geo)
    assertEquals(document(), result.config)
    assertEquals(RoutingMode.RussiaDirect, result.appliedRouting.mode)
    assertNull(result.geoRoutingAssets) // Fairway never consumes TrustTunnel/SRS payloads.
    assertFalse(result.config.contains("inbounds"))
    val withoutLegacyGeo = ProfileConfiguration.apply(original, RoutingSettings(RoutingMode.RussiaDirect))
    assertEquals(result, withoutLegacyGeo)
    assertFailsWith<IllegalArgumentException> { ProfileConfiguration.apply(original, RoutingSettings(RoutingMode.Manual)) }
    assertFailsWith<IllegalArgumentException> { ProfileConfiguration.apply(original, RoutingSettings(tlsFragment = true)) }
  }
}

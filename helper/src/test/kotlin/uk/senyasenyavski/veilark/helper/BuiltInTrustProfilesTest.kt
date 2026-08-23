package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

class BuiltInTrustProfilesTest {
  @Test
  fun `installs both built in endpoints into an empty store`() {
    val installed = BuiltInTrustProfiles.install(StoredProfiles(), BUILT_INS)
    val trust = installed.profiles.single()
    val subscription = installed.subscriptions.single()

    assertEquals(VpnEngine.TrustTunnel, trust.engine)
    assertEquals(2, trust.nodes.size)
    assertEquals(2, trust.endpointConfigs.size)
    assertEquals(SubscriptionOrigin.BuiltIn, subscription.origin)
    assertEquals(SubscriptionRecord.BUILT_IN_TRUST_ID, subscription.id)
    assertEquals(trust.nodes.first().tag, installed.selectedNodeTags[VpnEngine.TrustTunnel])
  }

  @Test
  fun `merges built ins without replacing a custom subscription`() {
    val custom = trustProfile("custom", "tt://custom.example/profile#Custom")
    val stored = StoredProfiles(
      profiles = listOf(custom, singBoxProfile()),
      selectedEngine = VpnEngine.SingBox,
      selectedNodeTags = mapOf(VpnEngine.TrustTunnel to custom.nodes.single().tag),
    )

    val installed = BuiltInTrustProfiles.install(stored, BUILT_INS)
    val trust = installed.profiles.first { it.engine == VpnEngine.TrustTunnel }

    assertEquals("custom", trust.id)
    assertEquals("Custom subscription", trust.name)
    assertEquals("https://example.com/sub", trust.sourceUrl)
    assertEquals(1, trust.nodes.size)
    assertEquals(3, installed.subscriptions.size)
    assertEquals(1, installed.subscriptions.count { it.origin == SubscriptionOrigin.BuiltIn })
    assertEquals(VpnEngine.SingBox, installed.selectedEngine)
    assertEquals(custom.nodes.single().tag, installed.selectedNodeTags[VpnEngine.TrustTunnel])
  }

  @Test
  fun `splits built ins out of a legacy merged Trust profile`() {
    val builtIn = BuiltInTrustProfiles.canonicalProfile(BUILT_INS)
    val custom = trustProfile("custom", "tt://custom.example/profile#Custom")
    val legacyMerged = custom.copy(
      nodes = custom.nodes + builtIn.nodes,
      endpointConfigs = custom.endpointConfigs + builtIn.endpointConfigs,
    )

    val installed = BuiltInTrustProfiles.install(
      StoredProfiles(profiles = listOf(legacyMerged)),
      BUILT_INS,
    )

    assertEquals(2, installed.subscriptions.size)
    val user = installed.subscriptions.single { it.origin == SubscriptionOrigin.User }
    assertEquals(custom.endpointConfigs, user.profiles.single().endpointConfigs)
    val protected = installed.subscriptions.single { it.origin == SubscriptionOrigin.BuiltIn }
    assertEquals(builtIn.endpointConfigs, protected.profiles.single().endpointConfigs)
  }

  @Test
  fun `legacy selected built in endpoint remains selected after split`() {
    val builtIn = BuiltInTrustProfiles.canonicalProfile(BUILT_INS)
    val custom = trustProfile("custom", "tt://custom.example/profile#Custom")
    val selectedBuiltIn = builtIn.nodes.last()
    val legacyMerged = custom.copy(
      nodes = custom.nodes + builtIn.nodes,
      endpointConfigs = custom.endpointConfigs + builtIn.endpointConfigs,
    )

    val installed = BuiltInTrustProfiles.install(
      StoredProfiles(
        profiles = listOf(legacyMerged),
        selectedEngine = VpnEngine.TrustTunnel,
        selectedNodeTags = mapOf(VpnEngine.TrustTunnel to selectedBuiltIn.tag),
      ),
      BUILT_INS,
    )

    assertEquals(
      SubscriptionRecord.BUILT_IN_TRUST_ID,
      installed.selectedSubscriptionIds[VpnEngine.TrustTunnel],
    )
    assertEquals(
      selectedBuiltIn.tag,
      installed.selectedNodeTags[VpnEngine.TrustTunnel],
    )
  }

  @Test
  fun `installation is idempotent`() {
    val once = BuiltInTrustProfiles.install(StoredProfiles(), BUILT_INS)
    val twice = BuiltInTrustProfiles.install(once, BUILT_INS)

    assertEquals(once, twice)
  }

  @Test
  fun `rejects an invalid embedded payload`() {
    assertFailsWith<IllegalArgumentException> {
      BuiltInTrustProfiles.install(StoredProfiles(), "https://example.com/not-trust")
    }
  }

  private fun trustProfile(id: String, config: String): Profile {
    val node = Node("custom-node", "Custom", "TrustTunnel")
    return Profile(
      id = id,
      name = "Custom subscription",
      engine = VpnEngine.TrustTunnel,
      config = config,
      nodes = listOf(node),
      sourceLabel = "Custom",
      endpointConfigs = mapOf(node.tag to config),
      sourceUrl = "https://example.com/sub",
    )
  }

  private fun singBoxProfile() = Profile(
    id = "sing-box",
    name = "Sing-box",
    engine = VpnEngine.SingBox,
    config = "{}",
    nodes = listOf(Node("auto", "Automatic", "selector")),
    sourceLabel = "Custom sing-box",
  )

  private companion object {
    val BUILT_INS = """
      tt://built-in-one.example/profile#Netherlands
      tt://built-in-two.example/profile#Frankfurt
    """.trimIndent()
  }
}

package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ProfileSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

class ProfileLifecycleTest {
  @Test
  fun `two subscriptions for one engine coexist and selected removal falls back`() {
    val first = SubscriptionRecord.user(listOf(singBoxProfile("first")))
    val second = SubscriptionRecord.user(listOf(singBoxProfile("second")))
    val stored = SubscriptionCatalog.put(
      SubscriptionCatalog.put(StoredProfiles(), first),
      second,
    )

    assertEquals(2, stored.subscriptions.size)
    assertEquals(second.id, stored.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals("second", stored.profiles.single { it.engine == VpnEngine.SingBox }.id)

    val result = assertIs<ProfileRemovalResult.Removed>(
      ProfileLifecycle.remove(stored, second.id, BUILT_INS),
    )

    assertEquals(first.id, result.stored.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals("first", result.stored.profiles.single { it.engine == VpnEngine.SingBox }.id)
    assertTrue(result.stored.subscriptions.none { it.id == second.id })
  }

  @Test
  fun `select switches active subscription without removing another source`() {
    val first = SubscriptionRecord.user(listOf(singBoxProfile("first")))
    val second = SubscriptionRecord.user(listOf(singBoxProfile("second")))
    val stored = SubscriptionCatalog.put(
      SubscriptionCatalog.put(StoredProfiles(), first),
      second,
    )

    val selected = SubscriptionCatalog.select(stored, VpnEngine.SingBox, first.id)

    assertEquals(2, selected.subscriptions.size)
    assertEquals(first.id, selected.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals("first", selected.profiles.single().id)
    assertEquals(VpnEngine.SingBox, selected.selectedEngine)
  }

  @Test
  fun `switching subscriptions resets a colliding node tag`() {
    val firstProfile = singBoxProfile("first").copy(
      nodes = listOf(Node("shared-tag", "First", "vless")),
    )
    val secondProfile = singBoxProfile("second").copy(
      nodes = listOf(Node("shared-tag", "Second", "trojan")),
    )
    val first = SubscriptionRecord.user(listOf(firstProfile))
    val second = SubscriptionRecord.user(listOf(secondProfile))
    val stored = SubscriptionCatalog.put(
      SubscriptionCatalog.put(StoredProfiles(), first),
      second,
    ).copy(selectedNodeTags = mapOf(VpnEngine.SingBox to "shared-tag"))

    val selected = SubscriptionCatalog.select(stored, VpnEngine.SingBox, first.id)

    assertEquals(ProfileSelection.AUTOMATIC_TAG, selected.selectedNodeTags[VpnEngine.SingBox])
  }

  @Test
  fun `refresh replaces the same subscription without changing the active source`() {
    val original = SubscriptionRecord.user(listOf(singBoxProfile("original")))
    val other = SubscriptionRecord.user(listOf(singBoxProfile("other")))
    val selected = SubscriptionCatalog.select(
      SubscriptionCatalog.put(
        SubscriptionCatalog.put(StoredProfiles(), original),
        other,
      ),
      VpnEngine.SingBox,
      original.id,
    )
    val refreshed = original.copy(profiles = listOf(singBoxProfile("refreshed")))

    val stored = SubscriptionCatalog.put(selected, refreshed, select = false)

    assertEquals(2, stored.subscriptions.size)
    assertEquals(original.id, stored.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals("refreshed", stored.profiles.single().id)
    assertTrue(stored.subscriptions.none { it.profiles.any { profile -> profile.id == "original" } })
  }

  @Test
  fun `removing a dual-core subscription removes the whole source group`() {
    val dual = SubscriptionRecord.user(
      listOf(singBoxProfile("dual-sing"), customTrustProfile("dual-trust")),
    )
    val stored = SubscriptionCatalog.put(StoredProfiles(), dual)

    val result = assertIs<ProfileRemovalResult.Removed>(
      ProfileLifecycle.remove(stored, dual.id, BUILT_INS),
    )

    assertEquals(setOf(VpnEngine.SingBox, VpnEngine.TrustTunnel), result.engines)
    assertEquals(listOf(SubscriptionRecord.BUILT_IN_TRUST_ID), result.stored.subscriptions.map { it.id })
    assertEquals(listOf(VpnEngine.TrustTunnel), result.stored.profiles.map(Profile::engine))
    assertEquals(VpnEngine.TrustTunnel, result.stored.selectedEngine)
    assertEquals(setOf(VpnEngine.TrustTunnel), result.stored.selectedNodeTags.keys)
  }

  @Test
  fun `removes custom Trust subscription while retaining protected built-in`() {
    val custom = SubscriptionRecord.user(listOf(customTrustProfile()))
    val stored = SubscriptionCatalog.put(StoredProfiles(), custom)

    val result = assertIs<ProfileRemovalResult.Removed>(
      ProfileLifecycle.remove(stored, custom.id, BUILT_INS),
    )
    val trust = result.stored.profiles.single { it.engine == VpnEngine.TrustTunnel }

    assertEquals(SubscriptionRecord.BUILT_IN_TRUST_ID, result.stored.selectedSubscriptionIds[VpnEngine.TrustTunnel])
    assertEquals("Veilark Trust", trust.name)
    assertEquals(2, trust.nodes.size)
    assertEquals(trust.nodes.first().tag, result.stored.selectedNodeTags[VpnEngine.TrustTunnel])
  }

  @Test
  fun `cannot remove Veilark Trust`() {
    val installed = BuiltInTrustProfiles.install(StoredProfiles(), BUILT_INS)

    val result = assertIs<ProfileRemovalResult.Protected>(
      ProfileLifecycle.remove(installed, SubscriptionRecord.BUILT_IN_TRUST_ID, BUILT_INS),
    )

    assertEquals(installed, result.stored)
  }

  @Test
  fun `invalid embedded data fails closed without deleting user subscription`() {
    val user = SubscriptionRecord.user(listOf(singBoxProfile()))
    val stored = SubscriptionCatalog.put(StoredProfiles(), user)

    val result = assertIs<ProfileRemovalResult.Unavailable>(
      ProfileLifecycle.remove(stored, user.id, "https://invalid.example/sub"),
    )

    assertEquals(stored, result.stored)
  }

  @Test
  fun `stale id is a no-op even when embedded data is invalid`() {
    val stored = StoredProfiles(profiles = listOf(singBoxProfile()))

    val result = assertIs<ProfileRemovalResult.NotFound>(
      ProfileLifecycle.remove(stored, "stale-id", "invalid"),
    )

    assertEquals(stored, result.stored)
  }

  @Test
  fun `keeps selected engine subscription and node tags valid`() {
    val first = SubscriptionRecord.user(listOf(singBoxProfile("first")))
    val second = SubscriptionRecord.user(listOf(singBoxProfile("second")))
    val stored = SubscriptionCatalog.put(
      SubscriptionCatalog.put(StoredProfiles(), first),
      second,
    ).copy(
      selectedNodeTags = mapOf(
        VpnEngine.SingBox to "missing-node",
        VpnEngine.TrustTunnel to "stale-trust-node",
      ),
    )

    val result = assertIs<ProfileRemovalResult.Removed>(
      ProfileLifecycle.remove(stored, second.id, BUILT_INS),
    )

    assertEquals(VpnEngine.SingBox, result.stored.selectedEngine)
    assertEquals(first.id, result.stored.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals(ProfileSelection.AUTOMATIC_TAG, result.stored.selectedNodeTags[VpnEngine.SingBox])
    val trust = result.stored.profiles.single { it.engine == VpnEngine.TrustTunnel }
    assertTrue(trust.nodes.any {
      it.tag == result.stored.selectedNodeTags[VpnEngine.TrustTunnel]
    })
  }

  private fun singBoxProfile(id: String = "sing-box-user") = Profile(
    id = id,
    name = "User sing-box $id",
    engine = VpnEngine.SingBox,
    config = "{}",
    nodes = listOf(Node("proxy-$id", "Proxy", "vless")),
    sourceLabel = "User subscription $id",
    sourceUrl = "https://example.test/$id",
  )

  private fun customTrustProfile(id: String = "trust-user"): Profile {
    val config = "tt://custom.example/$id#Custom"
    val node = Node("tt-$id", "Custom", "TrustTunnel")
    return Profile(
      id = id,
      name = "User Trust $id",
      engine = VpnEngine.TrustTunnel,
      config = config,
      nodes = listOf(node),
      sourceLabel = "User subscription $id",
      endpointConfigs = mapOf(node.tag to config),
      sourceUrl = "https://example.test/$id",
    )
  }

  private companion object {
    val BUILT_INS = """
      tt://built-in-one.example/profile#Netherlands
      tt://built-in-two.example/profile#Frankfurt
    """.trimIndent()
  }
}

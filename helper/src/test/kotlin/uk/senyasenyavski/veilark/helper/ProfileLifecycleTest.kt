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
      ProfileLifecycle.remove(stored, second.id),
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
      ProfileLifecycle.remove(stored, dual.id),
    )

    assertEquals(setOf(VpnEngine.SingBox, VpnEngine.TrustTunnel), result.engines)
    assertTrue(result.stored.subscriptions.isEmpty())
    assertTrue(result.stored.profiles.isEmpty())
    assertEquals(VpnEngine.SingBox, result.stored.selectedEngine)
    assertTrue(result.stored.selectedNodeTags.isEmpty())
  }

  @Test
  fun `removes custom Trust subscription without installing a replacement`() {
    val custom = SubscriptionRecord.user(listOf(customTrustProfile()))
    val stored = SubscriptionCatalog.put(StoredProfiles(), custom)

    val result = assertIs<ProfileRemovalResult.Removed>(
      ProfileLifecycle.remove(stored, custom.id),
    )

    assertTrue(result.stored.subscriptions.isEmpty())
    assertTrue(result.stored.profiles.isEmpty())
    assertTrue(result.stored.selectedSubscriptionIds.isEmpty())
  }

  @Test
  fun `stale id is a no-op`() {
    val stored = SubscriptionCatalog.put(
      StoredProfiles(),
      SubscriptionRecord.user(listOf(singBoxProfile())),
    )

    val result = assertIs<ProfileRemovalResult.NotFound>(
      ProfileLifecycle.remove(stored, "stale-id"),
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
      ProfileLifecycle.remove(stored, second.id),
    )

    assertEquals(VpnEngine.SingBox, result.stored.selectedEngine)
    assertEquals(first.id, result.stored.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals(ProfileSelection.AUTOMATIC_TAG, result.stored.selectedNodeTags[VpnEngine.SingBox])
    assertEquals(setOf(VpnEngine.SingBox), result.stored.selectedNodeTags.keys)
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
}

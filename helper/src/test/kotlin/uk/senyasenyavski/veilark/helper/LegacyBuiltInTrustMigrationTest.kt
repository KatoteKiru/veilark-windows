package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

class LegacyBuiltInTrustMigrationTest {
  @Test
  fun `removes the retired built in record from an otherwise empty store`() {
    val stored = SubscriptionCatalog.rebuild(
      stored = StoredProfiles(selectedEngine = VpnEngine.TrustTunnel),
      subscriptions = listOf(legacyBuiltIn()),
      requestedSelections = mapOf(
        VpnEngine.TrustTunnel to SubscriptionRecord.BUILT_IN_TRUST_ID,
      ),
    )

    val migrated = LegacyBuiltInTrustMigration.remove(stored)

    assertTrue(migrated.subscriptions.isEmpty())
    assertTrue(migrated.profiles.isEmpty())
    assertTrue(migrated.selectedSubscriptionIds.isEmpty())
    assertTrue(migrated.selectedNodeTags.isEmpty())
  }

  @Test
  fun `keeps every user record and falls back from the retired selection`() {
    val first = SubscriptionRecord.user(listOf(trustProfile("first", "user-one")))
    val second = SubscriptionRecord.user(listOf(singBoxProfile("second")))
    val stored = SubscriptionCatalog.rebuild(
      stored = StoredProfiles(
        selectedEngine = VpnEngine.TrustTunnel,
        selectedNodeTags = mapOf(VpnEngine.TrustTunnel to "retired-node"),
      ),
      subscriptions = listOf(legacyBuiltIn(), first, second),
      requestedSelections = mapOf(
        VpnEngine.TrustTunnel to SubscriptionRecord.BUILT_IN_TRUST_ID,
        VpnEngine.SingBox to second.id,
      ),
    )

    val migrated = LegacyBuiltInTrustMigration.remove(stored)

    assertEquals(listOf(first, second), migrated.subscriptions)
    assertEquals(first.id, migrated.selectedSubscriptionIds[VpnEngine.TrustTunnel])
    assertEquals(second.id, migrated.selectedSubscriptionIds[VpnEngine.SingBox])
    assertEquals("first-node", migrated.selectedNodeTags[VpnEngine.TrustTunnel])
  }

  @Test
  fun `does not remove or rewrite a user profile with matching content`() {
    val userProfile = trustProfile("customer", RETIRED_CONFIG)
    val user = SubscriptionRecord.user(listOf(userProfile))
    val stored = SubscriptionCatalog.rebuild(
      stored = StoredProfiles(),
      subscriptions = listOf(legacyBuiltIn(), user),
      requestedSelections = mapOf(VpnEngine.TrustTunnel to user.id),
    )

    val migrated = LegacyBuiltInTrustMigration.remove(stored)

    assertEquals(listOf(user), migrated.subscriptions)
    assertEquals(userProfile, migrated.profiles.single())
  }

  @Test
  fun `preserves a flat legacy profile as user owned data`() {
    val userProfile = trustProfile("legacy-user", RETIRED_CONFIG)
    val stored = StoredProfiles(
      profiles = listOf(userProfile),
      selectedEngine = VpnEngine.TrustTunnel,
      selectedNodeTags = mapOf(VpnEngine.TrustTunnel to userProfile.nodes.single().tag),
    )

    val migrated = LegacyBuiltInTrustMigration.remove(stored)

    assertEquals(1, migrated.subscriptions.size)
    assertEquals(SubscriptionOrigin.User, migrated.subscriptions.single().origin)
    assertEquals(userProfile, migrated.profiles.single())
  }

  @Test
  fun `migration is idempotent`() {
    val user = SubscriptionRecord.user(listOf(singBoxProfile("user")))
    val stored = SubscriptionCatalog.rebuild(
      stored = StoredProfiles(),
      subscriptions = listOf(legacyBuiltIn(), user),
      requestedSelections = mapOf(VpnEngine.SingBox to user.id),
    )

    val once = LegacyBuiltInTrustMigration.remove(stored)
    val twice = LegacyBuiltInTrustMigration.remove(once)

    assertEquals(once, twice)
  }

  private fun legacyBuiltIn(): SubscriptionRecord = SubscriptionRecord(
    id = SubscriptionRecord.BUILT_IN_TRUST_ID,
    name = "Retired built-in",
    profiles = listOf(trustProfile("retired", RETIRED_CONFIG)),
    sourceLabel = "Retired built-in",
    origin = SubscriptionOrigin.BuiltIn,
  )

  private fun trustProfile(id: String, config: String): Profile {
    val node = Node("$id-node", id, "TrustTunnel")
    return Profile(
      id = id,
      name = id,
      engine = VpnEngine.TrustTunnel,
      config = config,
      nodes = listOf(node),
      sourceLabel = id,
      endpointConfigs = mapOf(node.tag to config),
      sourceUrl = "https://example.test/$id",
    )
  }

  private fun singBoxProfile(id: String) = Profile(
    id = id,
    name = id,
    engine = VpnEngine.SingBox,
    config = "{}",
    nodes = listOf(Node("$id-node", id, "vless")),
    sourceLabel = id,
    sourceUrl = "https://example.test/$id",
  )

  private companion object {
    const val RETIRED_CONFIG = "retired-profile-payload"
  }
}

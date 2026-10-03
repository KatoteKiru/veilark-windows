package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

class SubscriptionCatalogImportTest {
  private fun profile(id: String) = Profile(
    id = id, name = id, engine = VpnEngine.SingBox, config = "{}",
    nodes = emptyList(), sourceLabel = "test", sourceUrl = "https://example.test/$id",
  )

  @Test
  fun firstImportSelectsItsOnlySubscription() {
    val imported = SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(profile("first")))
    assertEquals("first", imported.profiles.single().id)
    assertEquals(imported.subscriptions.single().id, imported.selectedSubscriptionIds[VpnEngine.SingBox])
  }

  @Test
  fun additionalImportPreservesTheExistingSelection() {
    val first = SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(profile("first")))
    val next = SubscriptionCatalog.fromImportedProfiles(first, listOf(profile("second")))
    assertEquals(2, next.subscriptions.size)
    assertEquals(first.selectedSubscriptionIds, next.selectedSubscriptionIds)
    assertEquals(first.selectedNodeTags, next.selectedNodeTags)
    assertEquals("first", next.profiles.single().id)
  }
}

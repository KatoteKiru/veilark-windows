package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

class SubscriptionCatalogImportTest {
  @Test fun coreEnrollmentAndLegacyRefreshKeepBothEngineProfilesForOneAccountSource() {
    val legacy = profile("legacy")
    val core = legacy.copy(id = "core-one", engine = VpnEngine.VeilarkCore)
    val first = SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(legacy))
    val enrolled = SubscriptionCatalog.fromImportedProfiles(first, listOf(core))
    assertEquals(setOf(legacy, core), enrolled.subscriptions.single().profiles.toSet())
    val refreshedLegacy = legacy.copy(id = "legacy-new")
    val refreshed = SubscriptionCatalog.fromImportedProfiles(enrolled, listOf(refreshedLegacy))
    assertEquals(setOf(refreshedLegacy, core), refreshed.subscriptions.single().profiles.toSet())
  }
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

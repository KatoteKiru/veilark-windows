package uk.senyasenyavski.veilark.helper

/** Removes the retired built-in subscription without inspecting user profile contents. */
object LegacyBuiltInTrustMigration {
  fun remove(stored: StoredProfiles): StoredProfiles {
    val normalized = SubscriptionCatalog.normalize(stored)
    val retained = normalized.subscriptions.filterNot { subscription ->
      subscription.origin == SubscriptionOrigin.BuiltIn ||
        subscription.id == SubscriptionRecord.BUILT_IN_TRUST_ID
    }
    if (retained.size == normalized.subscriptions.size) return normalized

    return SubscriptionCatalog.rebuild(
      stored = normalized,
      subscriptions = retained,
      requestedSelections = normalized.selectedSubscriptionIds.filterValues { subscriptionId ->
        subscriptionId != SubscriptionRecord.BUILT_IN_TRUST_ID
      },
    )
  }
}

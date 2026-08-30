package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnEngine

/** The result of an atomic profile-removal request. */
sealed interface ProfileRemovalResult {
  val stored: StoredProfiles

  data class Removed(
    override val stored: StoredProfiles,
    val subscriptionId: String,
    val engines: Set<VpnEngine>,
  ) : ProfileRemovalResult

  /** Retained for compatibility with older callers; no current removal path returns it. */
  data class Protected(override val stored: StoredProfiles) : ProfileRemovalResult

  /** The UI held a stale profile id; storage was not changed. */
  data class NotFound(override val stored: StoredProfiles) : ProfileRemovalResult

  /** Retained for compatibility with older callers; no current removal path returns it. */
  data class Unavailable(
    override val stored: StoredProfiles,
    val safeMessage: String,
  ) : ProfileRemovalResult
}

/** Owns destructive profile operations. A subscription is removed as one source group. */
object ProfileLifecycle {
  fun remove(stored: StoredProfiles, subscriptionId: String): ProfileRemovalResult {
    val migrated = LegacyBuiltInTrustMigration.remove(stored)
    val requested = migrated.subscriptions.firstOrNull { it.id == subscriptionId }
      ?: return ProfileRemovalResult.NotFound(migrated)
    val remaining = migrated.subscriptions.filterNot { it.id == requested.id }
    return ProfileRemovalResult.Removed(
      stored = SubscriptionCatalog.rebuild(
        stored = migrated,
        subscriptions = remaining,
        requestedSelections = migrated.selectedSubscriptionIds,
      ),
      subscriptionId = requested.id,
      engines = requested.profiles.mapTo(linkedSetOf()) { it.engine },
    )
  }
}

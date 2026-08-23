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

  /** The requested profile is the built-in Veilark Trust profile and cannot be removed. */
  data class Protected(override val stored: StoredProfiles) : ProfileRemovalResult

  /** The UI held a stale profile id; storage was not changed. */
  data class NotFound(override val stored: StoredProfiles) : ProfileRemovalResult

  /** Embedded Trust data could not be verified, so removal failed closed. */
  data class Unavailable(
    override val stored: StoredProfiles,
    val safeMessage: String,
  ) : ProfileRemovalResult
}

/**
 * Owns destructive profile operations and preserves the mandatory Veilark Trust profile.
 *
 * A subscription is removed as a source group, including both engine profiles it provided.
 * Veilark Trust is a separate [SubscriptionOrigin.BuiltIn] record and cannot be removed.
 */
object ProfileLifecycle {
  fun remove(stored: StoredProfiles, subscriptionId: String): ProfileRemovalResult {
    val payload = BuiltInTrustProfiles.loadPayload()
      ?: return ProfileRemovalResult.Unavailable(
        stored,
        "Встроенная подписка Veilark Trust недоступна; удаление отменено",
      )
    return remove(stored, subscriptionId, payload)
  }

  internal fun remove(
    stored: StoredProfiles,
    subscriptionId: String,
    builtInTrustPayload: String,
  ): ProfileRemovalResult {
    val migrated = SubscriptionCatalog.normalize(stored)
    val requested = migrated.subscriptions.firstOrNull { it.id == subscriptionId }
      ?: return ProfileRemovalResult.NotFound(stored)
    if (requested.origin == SubscriptionOrigin.BuiltIn) {
      return ProfileRemovalResult.Protected(migrated)
    }
    val withBuiltIn = runCatching {
      BuiltInTrustProfiles.install(migrated, builtInTrustPayload)
    }.getOrElse {
      return ProfileRemovalResult.Unavailable(
        stored,
        "Встроенная подписка Veilark Trust повреждена; удаление отменено",
      )
    }
    // A legacy profile containing only embedded endpoints is migrated into the protected record.
    if (withBuiltIn.subscriptions.none { it.id == requested.id }) {
      return ProfileRemovalResult.Protected(withBuiltIn)
    }
    val remaining = withBuiltIn.subscriptions.filterNot { it.id == requested.id }
    return ProfileRemovalResult.Removed(
      stored = SubscriptionCatalog.rebuild(
        stored = withBuiltIn,
        subscriptions = remaining,
        requestedSelections = withBuiltIn.selectedSubscriptionIds,
      ),
      subscriptionId = requested.id,
      engines = requested.profiles.mapTo(linkedSetOf()) { it.engine },
    )
  }
}

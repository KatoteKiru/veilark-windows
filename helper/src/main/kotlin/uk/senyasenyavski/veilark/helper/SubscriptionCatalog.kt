package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ProfileSelection
import java.security.MessageDigest
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

enum class SubscriptionOrigin {
  User,
  BuiltIn,
}

/** One imported source. A source can provide a profile for either or both VPN engines. */
data class SubscriptionRecord(
  val id: String,
  val name: String,
  val profiles: List<Profile>,
  val sourceLabel: String,
  val sourceUrl: String? = null,
  val origin: SubscriptionOrigin = SubscriptionOrigin.User,
) {
  init {
    require(id.isNotBlank()) { "Идентификатор подписки пуст" }
    require(name.isNotBlank()) { "Название подписки пусто" }
    require(profiles.isNotEmpty()) { "Подписка не содержит профилей" }
    require(profiles.map(Profile::engine).distinct().size == profiles.size) {
      "Подписка содержит несколько профилей одного ядра"
    }
    if (origin == SubscriptionOrigin.BuiltIn) {
      require(id == BUILT_IN_TRUST_ID) { "Неизвестная встроенная подписка" }
      require(profiles.singleOrNull()?.engine == VpnEngine.TrustTunnel) {
        "Встроенная подписка Veilark Trust повреждена"
      }
    } else {
      require(id != BUILT_IN_TRUST_ID) { "Зарезервированный идентификатор подписки" }
    }
  }

  fun profile(engine: VpnEngine): Profile? = profiles.firstOrNull { it.engine == engine }

  companion object {
    fun user(profiles: List<Profile>): SubscriptionRecord {
      require(profiles.isNotEmpty()) { "Подписка не содержит профилей" }
      val sourceUrl = profiles.mapNotNull(Profile::sourceUrl).distinct().singleOrNull()
      val sourceLabel = profiles.first().sourceLabel
      val identity = sourceUrl?.let { "url:$it" }
        ?: profiles.sortedBy { it.engine.name }.joinToString("|") { profile ->
          "${profile.engine.name}:${profile.id}"
        }
      return SubscriptionRecord(
        id = "user-${stableId(identity)}",
        name = profiles.first().name,
        profiles = profiles,
        sourceLabel = sourceLabel,
        sourceUrl = sourceUrl,
      )
    }

    private fun stableId(value: String): String =
      MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(10)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    const val BUILT_IN_TRUST_ID = "builtin-veilark-trust"
  }
}

/** Maintains the materialized active profile for each engine and all selection invariants. */
object SubscriptionCatalog {
  fun fromImportedProfiles(
    stored: StoredProfiles,
    profiles: List<Profile>,
  ): StoredProfiles = put(stored, SubscriptionRecord.user(profiles), select = false)

  fun put(
    stored: StoredProfiles,
    subscription: SubscriptionRecord,
    select: Boolean = true,
  ): StoredProfiles {
    val migrated = normalize(stored)
    require(
      migrated.subscriptions.none {
        it.id == subscription.id && it.origin == SubscriptionOrigin.BuiltIn
      },
    ) { "Встроенную подписку нельзя заменить" }
    val subscriptions = if (migrated.subscriptions.any { it.id == subscription.id }) {
      migrated.subscriptions.map { if (it.id == subscription.id) subscription else it }
    } else {
      migrated.subscriptions + subscription
    }
    val selections = if (select) {
      migrated.selectedSubscriptionIds + subscription.profiles.associate {
        it.engine to subscription.id
      }
    } else {
      migrated.selectedSubscriptionIds
    }
    return rebuild(migrated, subscriptions, selections)
  }

  fun select(
    stored: StoredProfiles,
    engine: VpnEngine,
    subscriptionId: String,
  ): StoredProfiles {
    val migrated = normalize(stored)
    require(
      migrated.subscriptions.any {
        it.id == subscriptionId && it.profile(engine) != null
      },
    ) { "В подписке нет профиля для ядра $engine" }
    val targetProfile = migrated.subscriptions
      .first { it.id == subscriptionId }
      .profile(engine)
      ?: error("В подписке нет профиля для ядра $engine")
    return rebuild(
      stored = migrated.copy(
        selectedEngine = engine,
        // Node tags are scoped to a subscription. Reusing a tag from the
        // previous source can silently select a different endpoint.
        selectedNodeTags = migrated.selectedNodeTags + (engine to defaultTag(targetProfile)),
      ),
      requestedSelections = migrated.selectedSubscriptionIds + (engine to subscriptionId),
    )
  }

  /** Converts the v1-v5 flat profile list to one source record per legacy profile. */
  internal fun normalize(stored: StoredProfiles): StoredProfiles {
    val subscriptions = stored.subscriptions.ifEmpty {
      legacySubscriptions(stored.profiles)
    }
    require(subscriptions.map(SubscriptionRecord::id).distinct().size == subscriptions.size) {
      "Повторяющийся идентификатор подписки"
    }
    val selections = stored.selectedSubscriptionIds.toMutableMap()
    VpnEngine.entries.forEach { engine ->
      if (subscriptions.none { it.id == selections[engine] && it.profile(engine) != null }) {
        val activeProfileId = stored.profiles.firstOrNull { it.engine == engine }?.id
        val fallback = subscriptions.firstOrNull {
          it.profile(engine)?.id == activeProfileId
        }?.id ?: subscriptions.firstOrNull { it.profile(engine) != null }?.id
        if (fallback == null) selections.remove(engine) else selections[engine] = fallback
      }
    }
    return rebuild(stored, subscriptions, selections)
  }

  /**
   * v1-v5 stored one active profile per engine. Profiles produced from a mixed
   * HTTPS subscription share the same source URL and must become one dual-core
   * record. Creating one record per profile gives both records the same stable
   * ID and prevents the encrypted store from opening.
   */
  private fun legacySubscriptions(profiles: List<Profile>): List<SubscriptionRecord> {
    data class Bucket(val identity: String, val profiles: MutableList<Profile>)

    val buckets = mutableListOf<Bucket>()
    profiles.forEach { profile ->
      val identity = profile.sourceUrl
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { "url:$it" }
        ?: "profile:${profile.engine.name}:${profile.id}"
      val bucket = buckets.firstOrNull { candidate ->
        candidate.identity == identity &&
          candidate.profiles.none { it.engine == profile.engine }
      } ?: Bucket(identity, mutableListOf()).also(buckets::add)
      bucket.profiles += profile
    }

    val duplicateIds = mutableMapOf<String, Int>()
    return buckets.map { bucket ->
      val record = SubscriptionRecord.user(bucket.profiles)
      val occurrence = duplicateIds.getOrDefault(record.id, 0)
      duplicateIds[record.id] = occurrence + 1
      if (occurrence == 0) record else {
        record.copy(id = "${record.id}-legacy-${occurrence + 1}")
      }
    }
  }

  internal fun rebuild(
    stored: StoredProfiles,
    subscriptions: List<SubscriptionRecord> = stored.subscriptions,
    requestedSelections: Map<VpnEngine, String> = stored.selectedSubscriptionIds,
  ): StoredProfiles {
    require(subscriptions.map(SubscriptionRecord::id).distinct().size == subscriptions.size) {
      "Повторяющийся идентификатор подписки"
    }
    val selections = buildMap {
      VpnEngine.entries.forEach { engine ->
        val requested = requestedSelections[engine]
        val selected = subscriptions.firstOrNull {
          it.id == requested && it.profile(engine) != null
        } ?: subscriptions.firstOrNull { it.profile(engine) != null }
        if (selected != null) put(engine, selected.id)
      }
    }
    val profiles = VpnEngine.entries.mapNotNull { engine ->
      val subscriptionId = selections[engine] ?: return@mapNotNull null
      subscriptions.first { it.id == subscriptionId }.profile(engine)
    }
    val profilesByEngine = profiles.associateBy(Profile::engine)
    val selectedEngine = stored.selectedEngine.takeIf(profilesByEngine::containsKey)
      ?: profilesByEngine.keys.firstOrNull()
      ?: VpnEngine.SingBox
    val selectedNodeTags = profilesByEngine.mapValues { (engine, profile) ->
      stored.selectedNodeTags[engine].takeIf { tag -> isValidTag(profile, tag) }
        ?: defaultTag(profile)
    }
    return stored.copy(
      profiles = profiles,
      selectedEngine = selectedEngine,
      selectedNodeTags = selectedNodeTags,
      subscriptions = subscriptions,
      selectedSubscriptionIds = selections,
    )
  }

  private fun isValidTag(profile: Profile, tag: String?): Boolean =
    tag != null && (
      profile.nodes.any { it.tag == tag } ||
        (profile.engine == VpnEngine.SingBox && tag == ProfileSelection.AUTOMATIC_TAG)
      )

  private fun defaultTag(profile: Profile): String =
    if (profile.engine == VpnEngine.SingBox) {
      ProfileSelection.AUTOMATIC_TAG
    } else {
      checkNotNull(profile.nodes.firstOrNull()?.tag) { "TrustTunnel-профиль не содержит серверов" }
    }
}

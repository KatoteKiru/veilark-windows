package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

/** Installs Veilark Trust as a separate protected subscription. */
object BuiltInTrustProfiles {
  private const val RESOURCE_NAME = "builtin_trust_profiles.txt"
  private const val SOURCE_LABEL = "Veilark Trust"

  fun installFromResource(stored: StoredProfiles): StoredProfiles {
    val payload = loadPayload() ?: return stored
    return runCatching { install(stored, payload) }.getOrDefault(stored)
  }

  internal fun loadPayload(): String? {
    val classLoader = Thread.currentThread().contextClassLoader
      ?: BuiltInTrustProfiles::class.java.classLoader
    return classLoader.getResourceAsStream(RESOURCE_NAME)?.bufferedReader(Charsets.UTF_8)?.use {
      it.readText()
    }
  }

  internal fun install(stored: StoredProfiles, payload: String): StoredProfiles {
    val builtIn = canonicalProfile(payload)
    val migrated = SubscriptionCatalog.normalize(stored)
    val selectedTag = migrated.selectedNodeTags[VpnEngine.TrustTunnel]
    val selectedTrust = migrated.profiles.firstOrNull { it.engine == VpnEngine.TrustTunnel }
    val selectedConfig = selectedTag?.let { selectedTrust?.endpointConfigs?.get(it) }
      ?: selectedTrust?.config
    val selectedWasBuiltIn = selectedConfig != null && (
      selectedConfig == builtIn.config || selectedConfig in builtIn.endpointConfigs.values
      )
    val subscriptions = migrated.subscriptions
      .filterNot { it.id == SubscriptionRecord.BUILT_IN_TRUST_ID }
      .mapNotNull { stripBuiltInTrust(it, builtIn) } + SubscriptionRecord.builtInTrust(builtIn)
    val requestedSelections = if (selectedWasBuiltIn) {
      migrated.selectedSubscriptionIds + (
        VpnEngine.TrustTunnel to SubscriptionRecord.BUILT_IN_TRUST_ID
        )
    } else {
      migrated.selectedSubscriptionIds
    }
    return SubscriptionCatalog.rebuild(
      stored = migrated,
      subscriptions = subscriptions,
      requestedSelections = requestedSelections,
    )
  }

  internal fun canonicalProfile(payload: String): Profile {
    val links = payload.lineSequence()
      .map(String::trim)
      .filter(String::isNotEmpty)
      .distinct()
      .toList()
    require(links.isNotEmpty()) { "Встроенные профили TrustTunnel отсутствуют" }
    require(links.all { it.startsWith("tt://", ignoreCase = true) }) {
      "Встроенный профиль TrustTunnel повреждён"
    }

    val imported = ProfileImporter().fromText(links.joinToString("\n"), SOURCE_LABEL)
    val builtIn = (imported as? ImportResult.Success)
      ?.profiles
      ?.singleOrNull { it.engine == VpnEngine.TrustTunnel }
      ?: error("Не удалось прочитать встроенные профили TrustTunnel")
    return builtIn
  }

  private fun stripBuiltInTrust(
    subscription: SubscriptionRecord,
    builtIn: Profile,
  ): SubscriptionRecord? {
    if (subscription.origin == SubscriptionOrigin.BuiltIn) return subscription
    val profiles = subscription.profiles.mapNotNull { profile ->
      if (profile.engine == VpnEngine.TrustTunnel) {
        stripBuiltInEndpoints(profile, builtIn)
      } else {
        profile
      }
    }
    return if (profiles.isEmpty()) null else subscription.copy(profiles = profiles)
  }

  private fun stripBuiltInEndpoints(profile: Profile, builtIn: Profile): Profile? {
    val builtInConfigs = builtIn.endpointConfigs.values.toSet() + builtIn.config
    if (profile.endpointConfigs.isEmpty()) {
      return profile.takeUnless { it.config in builtInConfigs }
    }
    val endpoints = profile.endpointConfigs.filterValues { it !in builtInConfigs }
    if (endpoints.isEmpty()) return null
    val nodes = profile.nodes.filter { it.tag in endpoints }
    return profile.copy(
      config = profile.config.takeIf { it in endpoints.values } ?: endpoints.values.first(),
      nodes = nodes,
      endpointConfigs = endpoints,
    )
  }
}

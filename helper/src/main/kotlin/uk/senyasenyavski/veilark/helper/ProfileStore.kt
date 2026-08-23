package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.VpnEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class StoredProfiles(
  val profiles: List<Profile> = emptyList(),
  val selectedEngine: VpnEngine = VpnEngine.SingBox,
  val routing: RoutingSettings = RoutingSettings(),
  val selectedNodeTags: Map<VpnEngine, String> = emptyMap(),
  val subscriptions: List<SubscriptionRecord> = emptyList(),
  val selectedSubscriptionIds: Map<VpnEngine, String> = emptyMap(),
)

sealed interface ProfileLoadResult {
  data object Missing : ProfileLoadResult
  data class Loaded(val stored: StoredProfiles) : ProfileLoadResult
  data class Failed(val cause: Throwable) : ProfileLoadResult
}

interface SecretProtector {
  fun protect(value: ByteArray): ByteArray
  fun unprotect(value: ByteArray): ByteArray
}

class DpapiSecretProtector : SecretProtector {
  override fun protect(value: ByteArray): ByteArray {
    check(Platform.isWindows()) { "DPAPI доступен только в Windows" }
    return Crypt32Util.cryptProtectData(value)
  }

  override fun unprotect(value: ByteArray): ByteArray {
    check(Platform.isWindows()) { "DPAPI доступен только в Windows" }
    return Crypt32Util.cryptUnprotectData(value)
  }
}

class ProfileStore(
  private val path: Path = VeilarkPaths.profileStore,
  private val protector: SecretProtector = DpapiSecretProtector(),
  private val logger: (String) -> Unit = SafeLog::write,
) {
  @Synchronized
  fun loadResult(): ProfileLoadResult {
    val primaryExists = Files.isRegularFile(path)
    val backupExists = Files.isRegularFile(backupPath)
    if (!primaryExists && !backupExists) return ProfileLoadResult.Missing

    val primary = if (primaryExists) runCatching { decodeProtected(path) } else null
    primary?.getOrNull()?.let { return ProfileLoadResult.Loaded(it) }

    val backup = if (backupExists) runCatching { decodeProtected(backupPath) } else null
    backup?.getOrNull()?.let {
      logger("Основное хранилище профилей недоступно; использована резервная копия")
      return ProfileLoadResult.Loaded(it)
    }

    val error = primary?.exceptionOrNull()
      ?: backup?.exceptionOrNull()
      ?: IllegalStateException("Хранилище профилей отсутствует")
    logger("Защищённое хранилище профилей повреждено или недоступно")
    return ProfileLoadResult.Failed(error)
  }

  /** Compatibility API for non-mutating diagnostics. The desktop startup uses [loadResult]. */
  @Synchronized
  fun load(): StoredProfiles = when (val result = loadResult()) {
    ProfileLoadResult.Missing -> StoredProfiles()
    is ProfileLoadResult.Loaded -> result.stored
    is ProfileLoadResult.Failed -> StoredProfiles()
  }

  @Synchronized
  fun save(value: StoredProfiles) {
    Files.createDirectories(path.parent)
    val encrypted = protector.protect(encode(value))
    val temporary = path.resolveSibling("${path.fileName}.tmp")
    val backupTemporary = backupPath.resolveSibling("${backupPath.fileName}.tmp")
    try {
      Files.write(temporary, encrypted)
      // Verify with the same protector and decoder before replacing the only
      // copy of the user's subscriptions.
      decode(protector.unprotect(Files.readAllBytes(temporary)))

      val validPrimary = if (Files.isRegularFile(path)) {
        runCatching {
          decodeProtected(path)
          Files.readAllBytes(path)
        }.getOrNull()
      } else {
        null
      }
      if (validPrimary != null) {
        Files.write(backupTemporary, validPrimary)
        moveReplacing(backupTemporary, backupPath)
      }
      moveReplacing(temporary, path)
    } finally {
      Files.deleteIfExists(temporary)
      Files.deleteIfExists(backupTemporary)
    }
  }

  private fun decodeProtected(candidate: Path): StoredProfiles =
    decode(protector.unprotect(Files.readAllBytes(candidate)))

  private fun moveReplacing(source: Path, target: Path) {
    runCatching {
      Files.move(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
      )
    }.getOrElse {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private val backupPath: Path
    get() = path.resolveSibling("${path.fileName}.bak")

  private fun encode(value: StoredProfiles): ByteArray =
    ByteArrayOutputStream().use { bytes ->
      DataOutputStream(bytes).use { output ->
        val normalized = SubscriptionCatalog.normalize(value)
        output.writeInt(MAGIC)
        output.writeInt(CURRENT_VERSION)
        output.writeUTF(normalized.selectedEngine.name)
        output.writeUTF(normalized.routing.mode.name)
        output.writeString(normalized.routing.directEntries)
        output.writeString(normalized.routing.vpnEntries)
        output.writeBoolean(normalized.routing.tlsFragment)
        output.writeInt(normalized.selectedNodeTags.size)
        normalized.selectedNodeTags.forEach { (engine, tag) ->
          output.writeUTF(engine.name)
          output.writeString(tag)
        }
        output.writeInt(normalized.subscriptions.size)
        normalized.subscriptions.forEach { subscription ->
          output.writeString(subscription.id)
          output.writeString(subscription.name)
          output.writeString(subscription.sourceLabel)
          output.writeBoolean(subscription.sourceUrl != null)
          subscription.sourceUrl?.let { output.writeString(it) }
          output.writeUTF(subscription.origin.name)
          output.writeInt(subscription.profiles.size)
          subscription.profiles.forEach { output.writeProfile(it) }
        }
        output.writeInt(normalized.selectedSubscriptionIds.size)
        normalized.selectedSubscriptionIds.forEach { (engine, subscriptionId) ->
          output.writeUTF(engine.name)
          output.writeString(subscriptionId)
        }
      }
      bytes.toByteArray()
    }

  private fun decode(value: ByteArray): StoredProfiles =
    DataInputStream(ByteArrayInputStream(value)).use { input ->
      require(input.readInt() == MAGIC) { "Неверный формат хранилища" }
      val version = input.readInt()
      require(version in 1..CURRENT_VERSION) { "Неподдерживаемая версия хранилища" }
      val selected = enumValueOf<VpnEngine>(input.readUTF())
      val routing = if (version >= 2) {
        RoutingSettings(
          mode = enumValueOf<RoutingMode>(input.readUTF()),
          directEntries = input.readString(),
          vpnEntries = input.readString(),
          tlsFragment = input.readBoolean(),
        )
      } else {
        RoutingSettings()
      }
      val selectedNodeTags = if (version >= 3) {
        buildMap {
          repeat(input.readBoundedCount(VpnEngine.entries.size)) {
            put(enumValueOf<VpnEngine>(input.readUTF()), input.readString())
          }
        }
      } else {
        emptyMap()
      }
      val decoded = if (version >= 6) {
        val subscriptions = List(input.readBoundedCount(MAX_SUBSCRIPTIONS)) {
          val id = input.readString()
          val name = input.readString()
          val sourceLabel = input.readString()
          val sourceUrl = if (input.readBoolean()) input.readString() else null
          val origin = enumValueOf<SubscriptionOrigin>(input.readUTF())
          val profiles = List(input.readBoundedCount(VpnEngine.entries.size)) {
            input.readProfile(version)
          }
          SubscriptionRecord(id, name, profiles, sourceLabel, sourceUrl, origin)
        }
        val selectedSubscriptions = buildMap {
          repeat(input.readBoundedCount(VpnEngine.entries.size)) {
            put(enumValueOf<VpnEngine>(input.readUTF()), input.readString())
          }
        }
        SubscriptionCatalog.rebuild(
          stored = StoredProfiles(
            selectedEngine = selected,
            routing = routing,
            selectedNodeTags = selectedNodeTags,
            subscriptions = subscriptions,
            selectedSubscriptionIds = selectedSubscriptions,
          ),
        )
      } else {
        val profiles = List(input.readBoundedCount(MAX_LEGACY_PROFILES)) {
          input.readProfile(version)
        }
        SubscriptionCatalog.normalize(
          StoredProfiles(profiles, selected, routing, selectedNodeTags),
        )
      }
      require(input.available() == 0) { "Лишние данные в хранилище" }
      decoded
    }

  private fun DataOutputStream.writeProfile(profile: Profile) {
    writeString(profile.id)
    writeString(profile.name)
    writeUTF(profile.engine.name)
    writeString(profile.config, MAX_CONFIG_BYTES)
    writeString(profile.sourceLabel)
    writeInt(profile.nodes.size)
    profile.nodes.forEach { node ->
      writeString(node.tag)
      writeString(node.name)
      writeString(node.protocol)
    }
    writeInt(profile.endpointConfigs.size)
    profile.endpointConfigs.forEach { (tag, config) ->
      writeString(tag)
      writeString(config, MAX_CONFIG_BYTES)
    }
    writeBoolean(profile.sourceUrl != null)
    profile.sourceUrl?.let { writeString(it) }
  }

  private fun DataInputStream.readProfile(version: Int): Profile {
    val profile = Profile(
      id = readString(),
      name = readString(),
      engine = enumValueOf(readUTF()),
      config = readString(MAX_CONFIG_BYTES),
      sourceLabel = readString(),
      nodes = List(readBoundedCount(MAX_NODES)) {
        Node(
          tag = readString(),
          name = readString(),
          protocol = readString(),
        )
      },
    )
    val withEndpoints = if (version >= 4) {
      profile.copy(
        endpointConfigs = buildMap {
          repeat(readBoundedCount(MAX_NODES)) {
            put(readString(), readString(MAX_CONFIG_BYTES))
          }
        },
      )
    } else {
      profile
    }
    return if (version >= 5 && readBoolean()) {
      withEndpoints.copy(sourceUrl = readString())
    } else {
      withEndpoints
    }
  }

  private fun DataOutputStream.writeString(
    value: String,
    maxBytes: Int = MAX_TEXT_BYTES,
  ) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= maxBytes) { "Поле профиля слишком большое" }
    writeInt(bytes.size)
    write(bytes)
  }

  private fun DataInputStream.readString(maxBytes: Int = MAX_TEXT_BYTES): String {
    val size = readBoundedCount(maxBytes)
    return readNBytes(size).also {
      require(it.size == size) { "Хранилище профилей обрезано" }
    }.toString(Charsets.UTF_8)
  }

  private fun DataInputStream.readBoundedCount(maximum: Int): Int =
    readInt().also { require(it in 0..maximum) { "Недопустимый размер в хранилище" } }

  private companion object {
    const val MAGIC = 0x56454C4B
    const val CURRENT_VERSION = 6
    const val MAX_LEGACY_PROFILES = 16
    const val MAX_SUBSCRIPTIONS = 128
    const val MAX_NODES = 10_000
    const val MAX_TEXT_BYTES = 16 * 1024
    const val MAX_CONFIG_BYTES = 4 * 1024 * 1024
  }
}

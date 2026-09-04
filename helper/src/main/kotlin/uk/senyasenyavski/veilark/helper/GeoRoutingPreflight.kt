package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.senyasenyavski.veilark.model.GeoRoutingAssets
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Safe, user-facing failure. It deliberately contains no process output or URL. */
class GeoRoutingUnavailableException internal constructor(message: String) :
  IllegalStateException(message)

/**
 * The installer ships a hash-pinned RU rule-set snapshot. Connections always
 * use the last validated local generation. A network refresh happens only
 * after an explicit user action and atomically publishes both rule sets.
 */
class GeoRoutingPreflight(
  executableOverride: Path? = null,
  directory: Path = VeilarkPaths.geoDirectory,
  bundledDirectoryOverride: Path? = null,
) {
  private val cache = GeoRuleSetCache(
    directory = directory,
    downloader = WindowsGeoRuleSetDownloader,
    decompiler = NativeSingBoxRuleSetDecompiler(resolveSingBox(executableOverride)),
    bundledDirectory = resolveBundledDirectory(bundledDirectoryOverride),
  )

  suspend fun refresh(): GeoRoutingAssets = safely(UPDATE_ERROR) { cache.refresh() }

  suspend fun requirePrepared(routing: RoutingSettings): GeoRoutingAssets? {
    if (!routing.mode.requiresGeoData) return null
    return safely(MISSING_ERROR) { cache.loadValidOrSeed() }
  }

  private suspend fun <T> safely(message: String, action: suspend () -> T): T = try {
    action()
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (_: Throwable) {
    SafeLog.write("Геоданные маршрутизации не прошли локальную проверку")
    throw GeoRoutingUnavailableException(message)
  }

  private companion object {
    const val UPDATE_ERROR =
      "Не удалось обновить геоданные. Предыдущая проверенная версия сохранена."
    const val MISSING_ERROR =
      "Геоданные маршрутизации не готовы. Обновите их и повторите подключение."
    fun resolveSingBox(override: Path?): Path {
      return runCatching {
        RuntimeResourceLocator.requireFile("sing-box.exe", override, "VEILARK_SING_BOX")
      }.getOrElse { throw GeoRoutingUnavailableException(MISSING_ERROR) }
    }

    fun resolveBundledDirectory(override: Path?): Path {
      return runCatching {
        RuntimeResourceLocator.requireDirectory("geo", override)
      }.getOrElse { throw GeoRoutingUnavailableException(MISSING_ERROR) }
    }
  }
}

internal val RoutingMode.requiresGeoData: Boolean
  get() = this == RoutingMode.RussiaDirect || this == RoutingMode.RussiaVpn

internal fun interface GeoRuleSetDownloader {
  suspend fun download(urls: List<String>, destination: Path)
}

internal fun interface SingBoxRuleSetDecompiler {
  suspend fun decompile(source: Path, destination: Path)
}

internal class GeoRuleSetCache(
  private val directory: Path,
  private val downloader: GeoRuleSetDownloader,
  private val decompiler: SingBoxRuleSetDecompiler,
  private val verifyOfficialHashes: Boolean = true,
  private val bundledDirectory: Path? = null,
) {
  private val mutex = Mutex()

  suspend fun refresh(): GeoRoutingAssets = mutex.withLock {
    withContext(Dispatchers.IO) {
      Files.createDirectories(directory)
      val generation = UUID.randomUUID().toString().replace("-", "")
      val staging = directory.resolve(".staging-$generation")
      val completed = directory.resolve(generation)
      Files.createDirectory(staging)
      try {
        val geoIp = staging.resolve(GEOIP.fileName)
        val geoSite = staging.resolve(GEOSITE.fileName)
        downloader.download(GEOIP.urls, geoIp)
        validateBinary(geoIp)
        downloader.download(GEOSITE.urls, geoSite)
        validateBinary(geoSite)
        val exclusions = validateAndExtract(geoIp, geoSite, staging)
        val digests = RuleSetDigests(sha256(geoIp), sha256(geoSite))

        Files.move(staging, completed, StandardCopyOption.ATOMIC_MOVE)
        publishManifest(generation, digests)
        assets(completed, exclusions)
      } finally {
        deleteStaging(staging)
      }
    }
  }

  suspend fun loadValid(): GeoRoutingAssets = mutex.withLock {
    withContext(Dispatchers.IO) { loadValidUnlocked() }
  }

  suspend fun loadValidOrSeed(): GeoRoutingAssets = mutex.withLock {
    withContext(Dispatchers.IO) {
      runCatching { loadValidUnlocked() }.getOrElse {
        installBundledSnapshot()
      }
    }
  }

  private suspend fun loadValidUnlocked(): GeoRoutingAssets {
    val manifest = readManifest()
    val generationDirectory = directory.resolve(manifest.generation).normalize()
    check(generationDirectory.parent == directory.normalize())
    val geoIp = generationDirectory.resolve(GEOIP.fileName)
    val geoSite = generationDirectory.resolve(GEOSITE.fileName)
    validateBinary(geoIp, manifest.digests.geoIp)
    validateBinary(geoSite, manifest.digests.geoSite)
    return assets(
      generationDirectory,
      validateAndExtract(geoIp, geoSite, generationDirectory),
    )
  }

  private suspend fun installBundledSnapshot(): GeoRoutingAssets {
    val sourceDirectory = bundledDirectory ?: error("bundled geo data unavailable")
    val sourceGeoIp = sourceDirectory.resolve(GEOIP.fileName)
    val sourceGeoSite = sourceDirectory.resolve(GEOSITE.fileName)
    validateBinary(sourceGeoIp, GEOIP.bundledSha256)
    validateBinary(sourceGeoSite, GEOSITE.bundledSha256)
    Files.createDirectories(directory)
    val generation = UUID.randomUUID().toString().replace("-", "")
    val staging = directory.resolve(".staging-$generation")
    val completed = directory.resolve(generation)
    Files.createDirectory(staging)
    try {
      val geoIp = staging.resolve(GEOIP.fileName)
      val geoSite = staging.resolve(GEOSITE.fileName)
      Files.copy(sourceGeoIp, geoIp)
      Files.copy(sourceGeoSite, geoSite)
      validateBinary(geoIp, GEOIP.bundledSha256)
      validateBinary(geoSite, GEOSITE.bundledSha256)
      val exclusions = validateAndExtract(geoIp, geoSite, staging)
      val digests = RuleSetDigests(sha256(geoIp), sha256(geoSite))
      Files.move(staging, completed, StandardCopyOption.ATOMIC_MOVE)
      publishManifest(generation, digests)
      return assets(completed, exclusions)
    } finally {
      deleteStaging(staging)
    }
  }

  private suspend fun validateAndExtract(
    geoIp: Path,
    geoSite: Path,
    temporaryDirectory: Path,
  ): List<String> {
    val geoIpJson = temporaryDirectory.resolve(".validate-geoip.json")
    val geoSiteJson = temporaryDirectory.resolve(".validate-geosite.json")
    return try {
      Files.deleteIfExists(geoIpJson)
      Files.deleteIfExists(geoSiteJson)
      decompiler.decompile(geoIp, geoIpJson)
      decompiler.decompile(geoSite, geoSiteJson)
      val networks = RuleSetJson.readNetworks(geoIpJson)
      val domains = RuleSetJson.readDomains(geoSiteJson)
      check(networks.isNotEmpty() && domains.isNotEmpty())
      (networks + domains).distinct()
    } finally {
      Files.deleteIfExists(geoIpJson)
      Files.deleteIfExists(geoSiteJson)
    }
  }

  private fun assets(generationDirectory: Path, exclusions: List<String>) = GeoRoutingAssets(
    geoIpRuPath = generationDirectory.resolve(GEOIP.fileName).toAbsolutePath().normalize().toString(),
    geoSiteCategoryRuPath = generationDirectory.resolve(GEOSITE.fileName)
      .toAbsolutePath()
      .normalize()
      .toString(),
    trustTunnelExclusions = exclusions,
  )

  private fun publishManifest(generation: String, digests: RuleSetDigests) {
    val temporary = directory.resolve("$MANIFEST.tmp")
    Files.writeString(
      temporary,
      JSONObject()
        .put("version", MANIFEST_VERSION)
        .put("generation", generation)
        .put("geoip_sha256", digests.geoIp)
        .put("geosite_sha256", digests.geoSite)
        .toString(),
      Charsets.UTF_8,
    )
    Files.move(
      temporary,
      directory.resolve(MANIFEST),
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  }

  private fun readManifest(): GenerationManifest {
    val manifest = directory.resolve(MANIFEST)
    check(Files.isRegularFile(manifest) && Files.size(manifest) in 1..MAX_MANIFEST_BYTES)
    val root = JSONObject(Files.readString(manifest, Charsets.UTF_8))
    check(root.getInt("version") == MANIFEST_VERSION)
    val generation = root.getString("generation").also { check(it.matches(GENERATION)) }
    val geoIp = root.getString("geoip_sha256").also { check(it.matches(SHA256)) }
    val geoSite = root.getString("geosite_sha256").also { check(it.matches(SHA256)) }
    return GenerationManifest(generation, RuleSetDigests(geoIp, geoSite))
  }

  private fun validateBinary(path: Path, expectedSha256: String? = null) {
    check(Files.isRegularFile(path) && Files.size(path) in 1..MAX_SRS_BYTES)
    if (verifyOfficialHashes && expectedSha256 != null) {
      check(sha256(path).equals(expectedSha256, ignoreCase = true)) {
        "unexpected rule-set digest"
      }
    }
  }

  private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).buffered().use { input ->
      val buffer = ByteArray(128 * 1024)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
  }

  private fun deleteStaging(path: Path) {
    if (!Files.isDirectory(path)) return
    Files.list(path).use { files -> files.forEach(Files::deleteIfExists) }
    Files.deleteIfExists(path)
  }

  private data class Asset(
    val fileName: String,
    val urls: List<String>,
    val bundledSha256: String,
  )

  private data class RuleSetDigests(val geoIp: String, val geoSite: String)
  private data class GenerationManifest(
    val generation: String,
    val digests: RuleSetDigests,
  )

  private companion object {
    val GEOIP = Asset(
      "geoip-ru.srs",
      listOf(
        "https://nl2.senyasenyavski.uk:2096/veilark/geo/current/geoip-ru.srs",
        "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
      ),
      "1A8115AF741918FF24B37B87D3C6DA21ECCABC58F1EEC059E461DCA8BAC16FF7",
    )
    val GEOSITE = Asset(
      "geosite-category-ru.srs",
      listOf(
        "https://nl2.senyasenyavski.uk:2096/veilark/geo/current/geosite-category-ru.srs",
        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/" +
          "geosite-category-ru.srs",
      ),
      "C36E157ADF86EDF7B722B51F3ACB93BBB2A7F8083932DAE29B4B5EF2C1CED870",
    )
    val GENERATION = Regex("""[0-9a-f]{32}""")
    val SHA256 = Regex("""[0-9A-Fa-f]{64}""")
    const val MANIFEST = "current.json"
    const val MANIFEST_VERSION = 2
    const val MAX_MANIFEST_BYTES = 4_096L
    const val MAX_SRS_BYTES = 32L * 1024 * 1024
  }
}

internal object WindowsGeoRuleSetDownloader : GeoRuleSetDownloader {
  override suspend fun download(urls: List<String>, destination: Path) {
    require(urls.isNotEmpty())
    val curl = resolveCurl() ?: error("curl unavailable")
    val succeeded = urls.any { url ->
      Files.deleteIfExists(destination)
      val result = ProcessBuilder(command(curl, url, destination))
        .redirectErrorStream(true)
        .start()
        .captureCancellable(PROCESS_TIMEOUT_MILLIS, maximumOutputChars = 1_000)
      result.succeeded && Files.isRegularFile(destination)
    }
    check(succeeded) { "download failed" }
  }

  internal fun command(curl: Path, url: String, destination: Path): List<String> = listOf(
    curl.toString(),
    "--location",
    "--fail",
    "--silent",
    "--show-error",
    "--connect-timeout",
    "8",
    "--max-time",
    "60",
    "--output",
    destination.toString(),
    url,
  )

  private fun resolveCurl(): Path? {
    val systemRoot = System.getenv("SystemRoot").orEmpty()
    return buildList {
      if (systemRoot.isNotBlank()) add(Path.of(systemRoot, "System32", "curl.exe"))
      add(Path.of("C:\\Windows\\System32\\curl.exe"))
    }.firstOrNull(Files::isRegularFile)
  }

  private const val PROCESS_TIMEOUT_MILLIS = 65_000L
}

internal class NativeSingBoxRuleSetDecompiler(
  private val executable: Path,
) : SingBoxRuleSetDecompiler {
  override suspend fun decompile(source: Path, destination: Path) {
    check(Files.isRegularFile(executable))
    val result = ProcessBuilder(command(executable, source, destination))
      .redirectErrorStream(true)
      .start()
      .captureCancellable(PROCESS_TIMEOUT_MILLIS, maximumOutputChars = 1_000)
    check(result.succeeded && Files.isRegularFile(destination)) { "invalid rule-set" }
  }

  internal fun command(executable: Path, source: Path, destination: Path): List<String> = listOf(
    executable.toString(),
    "rule-set",
    "decompile",
    source.toString(),
    "-o",
    destination.toString(),
  )

  private companion object {
    const val PROCESS_TIMEOUT_MILLIS = 20_000L
  }
}

private object RuleSetJson {
  fun readNetworks(path: Path): List<String> = read(path, "ip_cidr")
    .onEach { check(it.length <= 64 && it.matches(CIDR)) }

  fun readDomains(path: Path): List<String> {
    val root = root(path)
    val result = linkedSetOf<String>()
    val rules = root.getJSONArray("rules")
    repeat(rules.length()) { index ->
      val rule = rules.getJSONObject(index)
      appendStrings(rule, "domain", result) { it }
      appendStrings(rule, "domain_suffix", result) { suffix ->
        suffix.removePrefix(".")
      }
      appendStrings(rule, "domain_suffix", result) { suffix ->
        "*.${suffix.removePrefix(".")}"
      }
    }
    check(result.size in 1..MAX_ENTRIES)
    result.forEach { check(it.length <= 255 && it.matches(DOMAIN)) }
    return result.toList()
  }

  private fun read(path: Path, key: String): List<String> {
    val root = root(path)
    val result = linkedSetOf<String>()
    val rules = root.getJSONArray("rules")
    repeat(rules.length()) { index -> appendStrings(rules.getJSONObject(index), key, result) { it } }
    check(result.size in 1..MAX_ENTRIES)
    return result.toList()
  }

  private fun root(path: Path): JSONObject {
    check(Files.isRegularFile(path) && Files.size(path) in 1..MAX_JSON_BYTES)
    return JSONObject(Files.readString(path, Charsets.UTF_8)).also {
      check(it.getInt("version") in 1..MAX_RULE_SET_VERSION)
      check(it.getJSONArray("rules").length() in 1..MAX_RULES)
    }
  }

  private fun appendStrings(
    rule: JSONObject,
    key: String,
    target: MutableSet<String>,
    transform: (String) -> String,
  ) {
    val array = rule.optJSONArray(key) ?: return
    repeat(array.length()) { index ->
      check(target.size < MAX_ENTRIES)
      target += transform(array.getString(index).lowercase())
    }
  }

  private val CIDR = Regex("""[0-9a-f:.]+/[0-9]{1,3}""")
  private val DOMAIN = Regex("""(?:\*\.)?[a-z0-9](?:[a-z0-9._-]{0,253}[a-z0-9])?""")
  private const val MAX_RULE_SET_VERSION = 4
  private const val MAX_RULES = 100_000
  private const val MAX_ENTRIES = 500_000
  private const val MAX_JSON_BYTES = 96L * 1024 * 1024
}

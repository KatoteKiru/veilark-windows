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
 * The installer ships a hash-pinned RU rule-set snapshot. Both public methods
 * are therefore local-only: selecting split routing never depends on GitHub
 * being reachable and never delays connection with a network download.
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

  suspend fun refresh(): GeoRoutingAssets = safely(MISSING_ERROR) { cache.loadValidOrSeed() }

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
    const val MISSING_ERROR =
      "Геоданные маршрутизации не готовы. Обновите их и повторите подключение."
    fun resolveSingBox(override: Path?): Path {
      val candidates = buildList {
        override?.let(::add)
        System.getenv("VEILARK_SING_BOX")?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
        System.getProperty("compose.application.resources.dir")
          ?.takeIf(String::isNotBlank)
          ?.let { add(Path.of(it, "sing-box.exe")) }
        add(Path.of("packaging", "resources", "windows", "sing-box.exe").toAbsolutePath())
      }
      return candidates.firstOrNull(Files::isRegularFile)
        ?: throw GeoRoutingUnavailableException(MISSING_ERROR)
    }

    fun resolveBundledDirectory(override: Path?): Path {
      val candidates = buildList {
        override?.let(::add)
        System.getProperty("compose.application.resources.dir")
          ?.takeIf(String::isNotBlank)
          ?.let { add(Path.of(it, "geo")) }
        add(Path.of("packaging", "resources", "windows", "geo").toAbsolutePath())
      }
      return candidates.firstOrNull(Files::isDirectory)
        ?: throw GeoRoutingUnavailableException(MISSING_ERROR)
    }
  }
}

internal val RoutingMode.requiresGeoData: Boolean
  get() = this == RoutingMode.RussiaDirect || this == RoutingMode.RussiaVpn

internal fun interface GeoRuleSetDownloader {
  suspend fun download(url: String, destination: Path)
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
        downloader.download(GEOIP.url, geoIp)
        validateBinary(geoIp, GEOIP)
        downloader.download(GEOSITE.url, geoSite)
        validateBinary(geoSite, GEOSITE)
        val exclusions = validateAndExtract(geoIp, geoSite, staging)

        Files.move(staging, completed, StandardCopyOption.ATOMIC_MOVE)
        publishManifest(generation)
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
    val generation = readGeneration()
    val generationDirectory = directory.resolve(generation).normalize()
    check(generationDirectory.parent == directory.normalize())
    val geoIp = generationDirectory.resolve(GEOIP.fileName)
    val geoSite = generationDirectory.resolve(GEOSITE.fileName)
    validateBinary(geoIp, GEOIP)
    validateBinary(geoSite, GEOSITE)
    return assets(
      generationDirectory,
      validateAndExtract(geoIp, geoSite, generationDirectory),
    )
  }

  private suspend fun installBundledSnapshot(): GeoRoutingAssets {
    val sourceDirectory = bundledDirectory ?: error("bundled geo data unavailable")
    val sourceGeoIp = sourceDirectory.resolve(GEOIP.fileName)
    val sourceGeoSite = sourceDirectory.resolve(GEOSITE.fileName)
    validateBinary(sourceGeoIp, GEOIP)
    validateBinary(sourceGeoSite, GEOSITE)
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
      validateBinary(geoIp, GEOIP)
      validateBinary(geoSite, GEOSITE)
      val exclusions = validateAndExtract(geoIp, geoSite, staging)
      Files.move(staging, completed, StandardCopyOption.ATOMIC_MOVE)
      publishManifest(generation)
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

  private fun publishManifest(generation: String) {
    val temporary = directory.resolve("$MANIFEST.tmp")
    Files.writeString(
      temporary,
      JSONObject().put("version", MANIFEST_VERSION).put("generation", generation).toString(),
      Charsets.UTF_8,
    )
    Files.move(
      temporary,
      directory.resolve(MANIFEST),
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  }

  private fun readGeneration(): String {
    val manifest = directory.resolve(MANIFEST)
    check(Files.isRegularFile(manifest) && Files.size(manifest) in 1..MAX_MANIFEST_BYTES)
    val root = JSONObject(Files.readString(manifest, Charsets.UTF_8))
    check(root.getInt("version") == MANIFEST_VERSION)
    return root.getString("generation").also { check(it.matches(GENERATION)) }
  }

  private fun validateBinary(path: Path, asset: Asset) {
    check(Files.isRegularFile(path) && Files.size(path) in 1..MAX_SRS_BYTES)
    if (verifyOfficialHashes) {
      check(sha256(path) == asset.sha256) { "unexpected rule-set digest" }
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
    val url: String,
    val sha256: String,
  )

  private companion object {
    val GEOIP = Asset(
      "geoip-ru.srs",
      "https://raw.githubusercontent.com/SagerNet/sing-geoip/" +
        "b9c5e675b4d5359d4b47f4434fa7ae77e9991306/geoip-ru.srs",
      "1A8115AF741918FF24B37B87D3C6DA21ECCABC58F1EEC059E461DCA8BAC16FF7",
    )
    val GEOSITE = Asset(
      "geosite-category-ru.srs",
      "https://raw.githubusercontent.com/SagerNet/sing-geosite/" +
        "a70ce9f1f078f129cd40500f0bc0aee6eb6d59cd/" +
        "geosite-category-ru.srs",
      "C36E157ADF86EDF7B722B51F3ACB93BBB2A7F8083932DAE29B4B5EF2C1CED870",
    )
    val GENERATION = Regex("""[0-9a-f]{32}""")
    const val MANIFEST = "current.json"
    const val MANIFEST_VERSION = 1
    const val MAX_MANIFEST_BYTES = 4_096L
    const val MAX_SRS_BYTES = 32L * 1024 * 1024
  }
}

internal object WindowsGeoRuleSetDownloader : GeoRuleSetDownloader {
  override suspend fun download(url: String, destination: Path) {
    val curl = resolveCurl() ?: error("curl unavailable")
    val result = ProcessBuilder(command(curl, url, destination))
      .redirectErrorStream(true)
      .start()
      .captureCancellable(PROCESS_TIMEOUT_MILLIS, maximumOutputChars = 1_000)
    check(result.succeeded && Files.isRegularFile(destination)) { "download failed" }
  }

  internal fun command(curl: Path, url: String, destination: Path): List<String> = listOf(
    curl.toString(),
    "--location",
    "--fail",
    "--silent",
    "--show-error",
    "--noproxy",
    "*",
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

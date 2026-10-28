package uk.senyasenyavski.veilark.helper

import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.model.FairwayRelease
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.VpnEngine
import kotlin.system.exitProcess

/** Disposable isolated-peer acceptance only. Never imports an owner's profile. */
object FairwayCiTunnelCheck {
  private const val LIMIT = 16 * 1024 * 1024
  private const val TRUSTED_REF = "refs/heads/local/windows-fairway-20261025"
  private const val WINTUN_SHA = "e5da8447dc2c320edc0fc52fa01885c103de8c118481f683643cacc3220dafce"

  @JvmStatic fun main(args: Array<String>) {
    // Refuse before even creating files on a normal workstation.
    check(System.getenv("GITHUB_ACTIONS") == "true" &&
      System.getenv("RUNNER_ENVIRONMENT") == "github-hosted" &&
      System.getenv("GITHUB_REF") == TRUSTED_REF &&
      System.getenv("VEILARK_TEST_FAIRWAY_FULL_TUN") == "1") { "Isolated trusted CI only" }
    check(System.getProperty("os.name").startsWith("Windows") && ElevationManager().isElevated())
    val tempRoot = Path.of(requireNotNull(System.getenv("RUNNER_TEMP"))).toRealPath()
    val receiptPath = Path.of(requireNotNull(System.getenv("VEILARK_CI_RECEIPT"))).toAbsolutePath().normalize()
    check(receiptPath.startsWith(tempRoot) && receiptPath.parent == tempRoot)
    val receipt = JSONObject().put("schema", "veilark-windows-isolated-full-tun-v1")
      .put("passed", false).put("nativeSha256", FairwayRelease.nativeSha256)
      .put("cycles", JSONArray()).put("cleanup", false)
    val cycles = sortedMapOf<Int, JSONObject>()
    var stage = "prepare"
    var work: Path? = null
    var passed = false
    try {
      check(FairwayRelease.accepted)
      work = Files.createTempDirectory(tempRoot, "veilark-isolated-full-tun-")
      val resources = Files.createDirectory(work.resolve("resources"))
      stage = "download"
      receipt.put("stage", stage)
      Files.writeString(receiptPath, receipt.toString(2))
      downloadBundle(resources)
      stage = "validate"
      val native = resources.resolve("veilark_windows_native.dll")
      check(Files.size(native) == FairwayRelease.nativeSize && sha(native) == FairwayRelease.nativeSha256)
      val wintun = Path.of("packaging", "resources", "windows", "wintun.dll").toAbsolutePath()
      check(sha(wintun) == WINTUN_SHA)
      Files.copy(wintun, resources.resolve("wintun.dll"), StandardCopyOption.COPY_ATTRIBUTES)
      val profilePath = resources.resolve("profile.enc")
      val raw = decryptProfile(profilePath)
      val imported = try {
        check(raw.size <= 4 * 1024 * 1024 && raw.toString(Charsets.UTF_8).trimStart().startsWith("{"))
        ProfileImporter().fromText(raw.toString(Charsets.UTF_8), "Isolated CI fixture")
      } finally { raw.fill(0); Files.deleteIfExists(profilePath) }
      check(imported is ImportResult.Success && imported.profiles.size == 1 && imported.profile.engine == VpnEngine.VeilarkCore)
      stage = "protectProfile"
      val store = work.resolve("profiles.dpapi")
      ProfileStore(store).save(StoredProfiles(profiles = imported.profiles, selectedEngine = VpnEngine.VeilarkCore,
        routing = RoutingSettings(mode = RoutingMode.RussiaDirect)))
      System.setProperty("compose.application.resources.dir", resources.toString())
      System.setProperty("veilark.check.profileStore", store.toString())
      System.setProperty("veilark.check.ci", "true")
      stage = "tunnel"
      LiveTunnelCheck.execute(arrayOf("fairway", "12", "2")) { cycle, detail ->
        cycles[cycle] = detail
        receipt.put("cycles", JSONArray(cycles.values.toList()))
        Files.writeString(receiptPath, receipt.toString(2))
      }
      check(cycles.size == 2 && cycles.values.all { it.optBoolean("passed") &&
        listOf("connected", "ownedAdapter", "boundInternet", "bidirectionalTraffic", "stopped", "adapterAbsent", "internetRecovered")
          .all(it::optBoolean) })
      passed = true
      stage = "complete"
    } catch (_: Throwable) {
      // Never print a transport cause, URL, raw config or native argument data.
      println("Isolated Fairway acceptance failed at stage=$stage; see safe receipt")
    } finally {
      var cleaned = true
      work?.let { directory ->
        try {
          check(directory.toAbsolutePath().normalize().parent == tempRoot && directory.fileName.toString().startsWith("veilark-isolated-full-tun-"))
          Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        } catch (_: Throwable) { cleaned = false }
      }
      receipt.put("stage", stage).put("cleanup", cleaned).put("passed", passed && cleaned)
      Files.writeString(receiptPath, receipt.toString(2))
      passed = passed && cleaned
    }
    println("Isolated Fairway full TUN: passed=$passed; two cycles required; supplied lab fixture only")
    exitProcess(if (passed) 0 else 1)
  }

  private fun downloadBundle(destination: Path) {
    val url = URI(requireNotNull(System.getenv("FAIRWAY_CI_BUNDLE_URL")))
    check(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.fragment == null)
    val deadline = System.nanoTime() + 600_000_000_000L
    var waitingReported = false
    while (true) {
      val connection = url.toURL().openConnection() as HttpsURLConnection
      connection.instanceFollowRedirects = false
      connection.connectTimeout = 15_000
      connection.readTimeout = 20_000
      try {
        val status = connection.responseCode
        if ((status == 404 || status == 503) && System.nanoTime() < deadline) {
          if (!waitingReported) {
            println("Waiting for isolated bundle readiness (bounded to 600 seconds)")
            waitingReported = true
          }
          Thread.sleep(5_000)
          continue
        }
        check(status == 200 && connection.contentLengthLong <= LIMIT)
        val archive = connection.inputStream.use { input -> input.readNBytes(LIMIT + 1) }
        try {
          check(archive.size in 1..LIMIT)
          val expected = setOf("veilark_windows_native.dll", "profile.enc")
          val seen = mutableSetOf<String>()
          var total = 0
          ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
              val entry = zip.nextEntry ?: break
              check(!entry.isDirectory && entry.name in expected && seen.add(entry.name))
              val body = zip.readNBytes(LIMIT - total + 1)
              try {
                total += body.size
                check(total <= LIMIT && body.isNotEmpty())
                Files.write(destination.resolve(entry.name), body, java.nio.file.StandardOpenOption.CREATE_NEW)
              } finally { body.fill(0) }
              zip.closeEntry()
            }
          }
          check(seen == expected)
        } finally { archive.fill(0) }
        return
      } finally { connection.disconnect() }
    }
  }

  private fun sha(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
    .joinToString("") { "%02x".format(it) }

  private fun decryptProfile(path: Path): ByteArray {
    val encodedKey = requireNotNull(System.getenv("FAIRWAY_CI_BUNDLE_KEY"))
    check(encodedKey.matches(Regex("[0-9a-fA-F]{64}")))
    val key = ByteArray(32) { encodedKey.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    val encrypted = Files.readAllBytes(path)
    try {
      check(encrypted.size in 29..(4 * 1024 * 1024 + 28))
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, encrypted, 0, 12))
      cipher.updateAAD("veilark-isolated-full-tun-v1".toByteArray(Charsets.UTF_8))
      return cipher.doFinal(encrypted, 12, encrypted.size - 12)
    } finally { key.fill(0); encrypted.fill(0) }
  }
}

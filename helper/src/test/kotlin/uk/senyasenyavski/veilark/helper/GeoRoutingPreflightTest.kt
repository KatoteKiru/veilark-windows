package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoRoutingPreflightTest {
  @Test fun `refresh preserves an in-use generation and unknown directory contents`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-in-use")
    try {
      val cache = GeoRuleSetCache(directory, GeoRuleSetDownloader { urls, path ->
        writeFakeDownload(urls, path)
      }, FakeDecompiler, false)
      val first = cache.refresh()
      val unknown = directory.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
      Files.createDirectory(unknown)
      Files.writeString(unknown.resolve("user-data.txt"), "preserve")
      GeoGenerationLeases.acquire(first).use {
        repeat(4) { cache.refresh() }
        assertTrue(Files.exists(Path.of(first.geoIpRuPath)))
        assertTrue(Files.exists(unknown.resolve("user-data.txt")))
      }
      cache.refresh()
      assertTrue(!Files.exists(Path.of(first.geoIpRuPath)))
    } finally { deleteTree(directory) }
  }
  @Test fun `successful refresh retains active and one rollback generation only`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-retention")
    try {
      val cache = GeoRuleSetCache(directory, GeoRuleSetDownloader { urls, path ->
        writeFakeDownload(urls, path)
      }, FakeDecompiler, false)
      val results = (1..4).map { cache.refresh() }
      assertTrue(!Files.exists(Path.of(results.first().geoIpRuPath)))
      assertTrue(Files.exists(Path.of(results[2].geoIpRuPath)))
      assertTrue(Files.exists(Path.of(results.last().geoIpRuPath)))
      assertEquals(results.last(), cache.loadValid())
    } finally { deleteTree(directory) }
  }
  @Test
  fun `refresh publishes one validated generation and local load uses no network`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-cache")
    var downloads = 0
    val downloader = GeoRuleSetDownloader { urls, destination ->
      downloads += 1
      assertTrue(urls.first().startsWith("https://sub.senyasenyavski.uk/"))
      if (destination.fileName.toString() == ".remote-manifest.json") {
        assertEquals(1, urls.size)
      } else {
        assertEquals(1, urls.size)
      }
      writeFakeDownload(urls, destination)
    }
    try {
      val cache = GeoRuleSetCache(directory, downloader, FakeDecompiler, false)

      val refreshed = cache.refresh()
      val loaded = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { _, _ -> error("network must not be used") },
        FakeDecompiler,
        false,
      ).loadValid()

      assertEquals(3, downloads)
      assertEquals(refreshed, loaded)
      assertTrue(Files.isRegularFile(Path.of(loaded.geoIpRuPath)))
      assertTrue(Files.isRegularFile(Path.of(loaded.geoSiteCategoryRuPath)))
      assertEquals(
        listOf("5.8.0.0/13", "2a00:f00::/29", "example.ru", "ru", "*.ru"),
        loaded.trustTunnelExclusions,
      )
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `unchanged generation reuses semantic validation and manifest change invalidates it`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-memory-cache")
    var revision = 1
    val publisher = GeoRuleSetCache(
      directory,
      GeoRuleSetDownloader { _, destination ->
        val geoIp = "geoip-$revision"
        val geoSite = "geosite-$revision"
        val content = when (destination.fileName.toString()) {
          ".remote-manifest.json" -> remoteManifest(
            geoIp,
            geoSite,
            if (revision == 1) "20260928T042030Z" else "20260928T042031Z",
          )
          "geoip-ru.srs" -> geoIp
          else -> geoSite
        }
        Files.writeString(destination, content)
      },
      FakeDecompiler,
      false,
    )
    var decompilations = 0
    val countingDecompiler = SingBoxRuleSetDecompiler { source, destination ->
      decompilations += 1
      FakeDecompiler.decompile(source, destination)
    }
    try {
      publisher.refresh()
      val reader = GeoRuleSetCache(
        directory,
        failingDownloader(),
        countingDecompiler,
        false,
      )

      reader.loadValid()
      reader.loadValid()
      assertEquals(2, decompilations)

      revision = 2
      publisher.refresh()
      reader.loadValid()
      assertEquals(4, decompilations)
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `failed refresh leaves previous manifest active`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-atomic")
    try {
      val initial = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { urls, destination ->
          writeFakeDownload(urls, destination)
        },
        FakeDecompiler,
        false,
      ).refresh()
      val failing = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { urls, destination ->
          if (destination.fileName.toString() == ".remote-manifest.json") {
            writeFakeDownload(urls, destination)
          } else if (urls.last().contains("geosite")) {
            error("simulated partial update")
          } else {
            Files.writeString(destination, "new geoip")
          }
        },
        FakeDecompiler,
        false,
      )

      assertFailsWith<IllegalStateException> { failing.refresh() }
      val retained = GeoRuleSetCache(
        directory,
        failingDownloader(),
        FakeDecompiler,
        false,
      ).loadValid()

      assertEquals(initial, retained)
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `refresh rejects a download that does not match the published manifest`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-digest")
    val geoIp = "expected geoip"
    val geoSite = "expected geosite"
    try {
      val cache = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { urls, destination ->
          when (destination.fileName.toString()) {
            ".remote-manifest.json" -> Files.writeString(
              destination,
              remoteManifest(geoIp, geoSite),
            )
            "geoip-ru.srs" -> Files.writeString(destination, "tampered geoip")
            else -> Files.writeString(destination, urls.last().substringAfterLast('/'))
          }
        },
        FakeDecompiler,
      )

      assertFailsWith<IllegalStateException> { cache.refresh() }
      assertTrue(Files.list(directory).use { it.findAny().isEmpty })
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `refresh retries the official source when the mirror payload fails verification`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-source-fallback")
    val geoIp = "expected geoip"
    val geoSite = "expected geosite"
    var officialFallbacks = 0
    try {
      val cache = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { urls, destination ->
          when (destination.fileName.toString()) {
            ".remote-manifest.json" -> Files.writeString(
              destination,
              remoteManifest(geoIp, geoSite),
            )
            "geoip-ru.srs" -> if (urls.single().contains("raw.githubusercontent.com")) {
              officialFallbacks += 1
              Files.writeString(destination, geoIp)
            } else {
              Files.writeString(destination, "bad mirror")
            }
            else -> Files.writeString(destination, geoSite)
          }
        },
        FakeDecompiler,
      )

      val refreshed = cache.refresh()

      assertEquals(1, officialFallbacks)
      assertTrue(Files.isRegularFile(Path.of(refreshed.geoIpRuPath)))
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `refresh uses secondary release when primary manifest is invalid`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-manifest-fallback")
    var secondaryManifestUsed = false
    try {
      val cache = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { urls, destination ->
          if (destination.fileName.toString() == ".remote-manifest.json" &&
            urls.single().startsWith("https://sub.senyasenyavski.uk/")) {
            Files.writeString(destination, "<html>cover page</html>")
          } else {
            if (destination.fileName.toString() == ".remote-manifest.json") {
              secondaryManifestUsed = true
            }
            writeFakeDownload(urls, destination)
          }
        },
        FakeDecompiler,
      )

      cache.refresh()
      assertTrue(secondaryManifestUsed)
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `refresh uses secondary release when primary assets do not match`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-release-fallback")
    var secondaryManifestUsed = false
    var primaryRelease = true
    try {
      val cache = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { urls, destination ->
          if (destination.fileName.toString() == ".remote-manifest.json") {
            primaryRelease = urls.single().startsWith("https://sub.senyasenyavski.uk/")
            secondaryManifestUsed = secondaryManifestUsed || !primaryRelease
            writeFakeDownload(urls, destination)
          } else if (primaryRelease) {
            Files.writeString(destination, "mismatched bytes")
          } else {
            writeFakeDownload(urls, destination)
          }
        },
        FakeDecompiler,
      )

      cache.refresh()
      assertTrue(secondaryManifestUsed)
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `same release timestamp with changed digests preserves last known good`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-replay")
    var revision = 1
    try {
      val cache = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { _, destination ->
          val geoIp = "geoip-$revision"
          val geoSite = "geosite-$revision"
          val content = when (destination.fileName.toString()) {
            ".remote-manifest.json" -> remoteManifest(geoIp, geoSite)
            "geoip-ru.srs" -> geoIp
            else -> geoSite
          }
          Files.writeString(destination, content)
        },
        FakeDecompiler,
      )
      val initial = cache.refresh()
      revision = 2
      assertFailsWith<IllegalStateException> { cache.refresh() }
      assertEquals(initial, cache.loadValid())
    } finally {
      deleteTree(directory)
    }
  }

  @Test
  fun `local preflight fails closed when cache is absent`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-missing")
    try {
      assertFailsWith<IllegalStateException> {
        GeoRuleSetCache(directory, failingDownloader(), FakeDecompiler, false).loadValid()
      }
      assertTrue(Files.list(directory).use { it.findAny().isEmpty })
    } finally {
      directory.deleteIfExists()
    }
  }

  @Test
  fun `bundled snapshot seeds an empty cache without network`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-seeded")
    val bundled = Files.createTempDirectory("veilark-geo-bundled")
    try {
      Files.writeString(bundled.resolve("geoip-ru.srs"), "bundled geoip")
      Files.writeString(bundled.resolve("geosite-category-ru.srs"), "bundled geosite")
      val cache = GeoRuleSetCache(
        directory,
        failingDownloader(),
        FakeDecompiler,
        false,
        bundled,
      )

      val seeded = cache.loadValidOrSeed()
      val loaded = cache.loadValid()

      assertEquals(seeded, loaded)
      assertTrue(Files.isRegularFile(directory.resolve("current.json")))
      assertEquals(
        listOf("5.8.0.0/13", "2a00:f00::/29", "example.ru", "ru", "*.ru"),
        loaded.trustTunnelExclusions,
      )
    } finally {
      deleteTree(directory)
      deleteTree(bundled)
    }
  }

  @Test
  fun `Windows downloader is bounded and uses Schannel without proxy bypass`() {
    val command = WindowsGeoRuleSetDownloader.command(
      Path.of("C:\\Windows\\System32\\curl.exe"),
      "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
      Path.of("C:\\geo\\geoip-ru.srs"),
    )

    assertEquals("C:\\Windows\\System32\\curl.exe", command.first())
    assertTrue(command.none { it == "--noproxy" })
    assertTrue(command.windowed(2).any { it == listOf("--max-time", "60") })
    assertTrue(command.windowed(2).any { it == listOf("--max-filesize", "33554432") })
    assertTrue(command.windowed(2).any { it == listOf("--output", "C:\\geo\\geoip-ru.srs") })
    assertTrue(command.windowed(2).any { it == listOf("--proto", "=https") })
    assertTrue(command.none { it == "--location" || it == "--proto-redir" })
    assertTrue(command.none { it == "-k" || it == "--insecure" })

    val manifestCommand = WindowsGeoRuleSetDownloader.command(
      Path.of("C:\\Windows\\System32\\curl.exe"),
      "https://sub.senyasenyavski.uk/veilark/geo/current/manifest.json",
      Path.of("C:\\geo\\.remote-manifest.json"),
    )
    assertTrue(manifestCommand.windowed(2).any { it == listOf("--max-filesize", "4096") })

    assertFailsWith<IllegalArgumentException> {
      WindowsGeoRuleSetDownloader.command(
        Path.of("C:\\Windows\\System32\\curl.exe"),
        "https://sub.senyasenyavski.uk/veilark/geo/current/manifest.json",
        Path.of("C:\\geo\\unbounded.bin"),
      )
    }

    assertFailsWith<IllegalArgumentException> {
      WindowsGeoRuleSetDownloader.command(
        Path.of("C:\\Windows\\System32\\curl.exe"),
        "https://example.com/geoip-ru.srs",
        Path.of("C:\\geo\\geoip-ru.srs"),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      WindowsGeoRuleSetDownloader.command(
        Path.of("C:\\Windows\\System32\\curl.exe"),
        "https://nl2.senyasenyavski.uk/veilark/geo/current/geoip-ru.srs",
        Path.of("C:\\geo\\geoip-ru.srs"),
      )
    }
  }

  private fun writeFakeDownload(urls: List<String>, destination: Path) {
    val geoIp = "geoip-ru.srs"
    val geoSite = "geosite-category-ru.srs"
    val content = when (destination.fileName.toString()) {
      ".remote-manifest.json" -> remoteManifest(geoIp, geoSite)
      "geoip-ru.srs" -> geoIp
      "geosite-category-ru.srs" -> geoSite
      else -> urls.last().substringAfterLast('/')
    }
    Files.writeString(destination, content)
  }

  private fun remoteManifest(
    geoIp: String,
    geoSite: String,
    generatedAt: String = "20260928T042030Z",
  ): String = JSONObject()
    .put("schema", 1)
    .put("generatedAt", generatedAt)
    .put(
      "assets",
      JSONObject()
        .put("geoip-ru.srs", remoteAsset(geoIp))
        .put("geosite-category-ru.srs", remoteAsset(geoSite)),
    )
    .toString()

  private fun remoteAsset(content: String): JSONObject = JSONObject()
    .put("sha256", sha256(content))
    .put("size", content.toByteArray().size)

  private fun sha256(content: String): String = MessageDigest.getInstance("SHA-256")
    .digest(content.toByteArray())
    .joinToString("") { "%02x".format(it) }

  private fun failingDownloader() = GeoRuleSetDownloader { _, _ ->
    error("network must not be used")
  }

  private fun deleteTree(directory: Path) {
    if (!Files.exists(directory)) return
    Files.walk(directory).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }

  private object FakeDecompiler : SingBoxRuleSetDecompiler {
    override suspend fun decompile(source: Path, destination: Path) {
      val json = if (source.fileName.toString().contains("geoip")) {
        """{"version":1,"rules":[{"ip_cidr":["5.8.0.0/13","2a00:f00::/29"]}]}"""
      } else {
        """{"version":1,"rules":[{"domain":["example.ru"],"domain_suffix":[".ru"]}]}"""
      }
      Files.writeString(destination, json)
    }
  }
}

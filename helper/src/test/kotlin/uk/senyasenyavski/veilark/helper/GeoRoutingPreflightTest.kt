package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoRoutingPreflightTest {
  @Test
  fun `refresh publishes one validated generation and local load uses no network`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-cache")
    var downloads = 0
    val downloader = GeoRuleSetDownloader { url, destination ->
      downloads += 1
      Files.writeString(destination, url.substringAfterLast('/'))
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

      assertEquals(2, downloads)
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
  fun `failed refresh leaves previous manifest active`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-geo-atomic")
    try {
      val initial = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { url, destination ->
          Files.writeString(destination, url.substringAfterLast('/'))
        },
        FakeDecompiler,
        false,
      ).refresh()
      val failing = GeoRuleSetCache(
        directory,
        GeoRuleSetDownloader { url, destination ->
          if (url.contains("geosite")) error("simulated partial update")
          Files.writeString(destination, "new geoip")
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
    assertTrue(command.windowed(2).any { it == listOf("--noproxy", "*") })
    assertTrue(command.windowed(2).any { it == listOf("--max-time", "60") })
    assertTrue(command.windowed(2).any { it == listOf("--output", "C:\\geo\\geoip-ru.srs") })
    assertTrue(command.none { it == "-k" || it == "--insecure" })
  }

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

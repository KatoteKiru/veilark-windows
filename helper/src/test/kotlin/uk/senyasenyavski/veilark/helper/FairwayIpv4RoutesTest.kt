package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uk.senyasenyavski.veilark.model.GeoRoutingAssets
import org.junit.jupiter.api.Assumptions.assumeTrue

class FairwayIpv4RoutesTest {
  @Test fun `shipped verified Geo snapshot fits native route budget without network`() = runBlocking {
    val resources = System.getenv("VEILARK_TEST_GEO_RESOURCES")
    assumeTrue(!resources.isNullOrBlank(), "Explicit packaged Geo resources required")
    val source = Path.of(resources!!)
    val directory = Files.createTempDirectory("veilark-fairway-geo-")
    try {
      val cache = GeoRuleSetCache(
        directory = directory,
        downloader = GeoRuleSetDownloader { _, _ -> error("Network forbidden in this case") },
        decompiler = NativeSingBoxRuleSetDecompiler(source.resolve("sing-box.exe")),
        bundledDirectory = source.resolve("geo"),
      )
      val assets = cache.loadValidOrSeed()
      val plan = FairwayIpv4Routes.fromGeoAssets(assets)
      val inputs = assets.trustTunnelExclusions.count { '/' in it && ':' !in it }
      assertEquals(plan, FairwayIpv4Routes.compact(plan))
      kotlin.test.assertTrue(plan.size in 1..FairwayIpv4Routes.MAX_DIRECT_PREFIXES)
      println("Verified shipped RU Geo: IPv4 inputs=$inputs, exact compacted prefixes=${plan.size}")
    } finally {
      Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
  }

  @Test fun `adjacent siblings overlaps and duplicates produce exact canonical union`() {
    assertEquals(listOf("10.0.0.0/23", "10.0.3.0/24"), FairwayIpv4Routes.compact(listOf(
      "10.0.1.0/24", "10.0.0.0/25", "10.0.0.128/25", "10.0.0.0/25", "10.0.3.0/24",
    )))
    assertEquals(listOf("128.0.0.0/2"), FairwayIpv4Routes.compact(listOf("128.0.0.0/3", "160.0.0.0/3")))
  }

  @Test fun `Geo adapter keeps domains and IPv6 outside IPv4 route input`() {
    val assets = GeoRoutingAssets("unused", "unused", listOf("ozon.ru", ".ru", "2a00::/16", "5.8.0.0/24", "5.8.1.0/24"))
    assertEquals(listOf("5.8.0.0/23"), FairwayIpv4Routes.fromGeoAssets(assets))
    assertFailsWith<IllegalArgumentException> {
      FairwayIpv4Routes.fromGeoAssets(assets.copy(trustTunnelExclusions = listOf("ozon.ru", "2a00::/16")))
    }
  }

  @Test fun `malformed noncanonical and unsupported routes are rejected without resolution`() {
    listOf("host.ru/24", "1.2.3.4/24", "01.2.3.4/32", "1.2.3.4/+32", "1.2.3.4/032", "1.2.3.256/32",
      "0.0.0.0/0", "0.1.2.3/32", "127.0.0.1/32", "224.0.0.0/4", "1.2.3.4/33", "1.2.3.4/32/1",
    ).forEach { invalid -> assertFailsWith<IllegalArgumentException>(invalid) { FairwayIpv4Routes.compact(listOf(invalid)) } }
  }

  @Test fun `compaction never adds an address and is idempotent regardless of input order`() {
    val random = Random(684)
    repeat(80) {
      val selected = (0..255).filter { random.nextBoolean() }.toSet()
      val source = selected.map { "10.1.2.$it/32" }.shuffled(random)
      val compact = FairwayIpv4Routes.compact(source)
      val actual = compact.flatMap { cidr ->
        val address = cidr.substringBefore('/').substringAfterLast('.').toInt()
        val size = 1 shl (32 - cidr.substringAfter('/').toInt())
        address until address + size
      }.toSet()
      assertEquals(selected, actual)
      assertEquals(compact, FairwayIpv4Routes.compact(compact))
      assertEquals(compact, FairwayIpv4Routes.compact(source.reversed() + source))
    }
  }

  @Test fun `native capacity applies after exact compaction and never silently truncates`() {
    val dense = (0 until 32_768).map { "10.0.${it / 256}.${it % 256}/32" }
    assertEquals(listOf("10.0.0.0/17"), FairwayIpv4Routes.compact(dense))
    val sparse = (0..FairwayIpv4Routes.MAX_DIRECT_PREFIXES).map { index ->
      val address = index * 2
      "10.${address / 65_536}.${address / 256 % 256}.${address % 256}/32"
    }
    assertFailsWith<IllegalArgumentException> { FairwayIpv4Routes.compact(sparse) }
  }
}

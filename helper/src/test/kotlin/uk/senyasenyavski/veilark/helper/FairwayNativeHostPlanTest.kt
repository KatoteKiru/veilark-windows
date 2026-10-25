package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class FairwayNativeHostPlanTest {
  @Test fun `new optional host API accepts immutable full RU plan without starting networking`() = runBlocking {
    val dll = System.getenv("VEILARK_NATIVE_HOST_TEST_DLL")
    val resources = System.getenv("VEILARK_TEST_GEO_RESOURCES")
    assumeTrue(Platform.isWindows() && !dll.isNullOrBlank() && !resources.isNullOrBlank(), "Explicit development DLL and Geo required")
    val directory = Files.createTempDirectory("veilark-native-host-plan-")
    try {
      val source = Path.of(resources!!)
      val assets = GeoRuleSetCache(
        directory, GeoRuleSetDownloader { _, _ -> error("Network forbidden") },
        NativeSingBoxRuleSetDecompiler(source.resolve("sing-box.exe")),
        bundledDirectory = source.resolve("geo"),
      ).loadValidOrSeed()
      val routes = FairwayIpv4Routes.fromGeoAssets(assets)
      val pin = NativeArtifactPin(34_249_486, "b45a91e8f25f76a5ead2560bc204a3b211e796cdc830e66c507c793ce0268594")
      FairwayNativeLibrary.load(Path.of(dll!!), pin).use { library ->
        assertTrue(library.supportsHostConfiguration)
        assertFalse(library.supportsFullTunnel)
        val key = ByteArray(32) { (it + 1).toByte() }
        val owner = library.create(key, ByteArray(32) { (it + 33).toByte() }, ByteArray(16) { (it + 1).toByte() },
          byteArrayOf(192.toByte(), 0, 2, 1), 443, 1360, 1)
        try {
          val nonexistentPath = directory.resolve("not-installed-кириллица-\uD83C\uDF0D.dll")
          val dns = listOf(byteArrayOf(1, 1, 1, 1), byteArrayOf(77, 88, 8, 8))
          assertFailsWith<IllegalArgumentException> { owner.configureHost(Path.of("relative.dll"), dns, routes) }
          assertFailsWith<IllegalArgumentException> { owner.configureHost(nonexistentPath, emptyList(), routes) }
          assertFailsWith<IllegalArgumentException> { owner.configureHost(nonexistentPath, listOf(dns[0], dns[0]), routes) }
          assertFailsWith<IllegalArgumentException> { owner.configureHost(nonexistentPath, dns, listOf("5.8.0.1/24")) }
          val endpointError = assertFailsWith<NativeCallException> {
            owner.configureHost(nonexistentPath, dns, listOf("192.0.2.1/32"))
          }
          assertEquals(-1, endpointError.result)
          owner.configureHost(nonexistentPath, dns, routes)
          val repeated = assertFailsWith<NativeCallException> { owner.configureHost(nonexistentPath, dns, routes) }
          assertEquals(-4, repeated.result)
          assertTrue(key.all { it == 0.toByte() })
          assertFalse(Files.exists(nonexistentPath))
          val status = owner.status()
          assertEquals(1, status.phase)
          assertFalse(status.workerAlive)
          assertEquals(0, status.resourceMask)
          assertEquals(0L, status.txBytes)
          assertEquals(0L, status.rxBytes)
          assertFailsWith<IllegalStateException> { owner.beginAuthForTunnel() }
          assertFailsWith<IllegalStateException> { owner.startHost() }
          println("Real JNA configure_host accepted ${routes.size} verified RU prefixes; AUTH-only, no host start")
        } finally { owner.release() }
      }
    } finally {
      Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
  }
}

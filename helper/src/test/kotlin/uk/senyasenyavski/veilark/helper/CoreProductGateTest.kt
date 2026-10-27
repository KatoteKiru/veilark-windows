package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import uk.senyasenyavski.veilark.model.*

class CoreProductGateTest {
  private fun core() = Profile("core", "Fairway", VpnEngine.VeilarkCore, "synthetic Core secret",
    listOf(Node("core-node", "Fairway", "Fairway")), "test")

  @Test fun `stored Core selection follows the compiled product decision`() {
    val record = SubscriptionRecord.user(listOf(core()))
    val result = SubscriptionCatalog.put(StoredProfiles(), record)
    assertEquals(record, result.subscriptions.single())
    if (FairwayRelease.accepted) {
      assertEquals(listOf(core()), result.profiles)
      assertEquals(record.id, result.selectedSubscriptionIds[VpnEngine.VeilarkCore])
      assertEquals(VpnEngine.VeilarkCore,
        SubscriptionCatalog.select(result, VpnEngine.VeilarkCore, record.id).selectedEngine)
    } else {
      assertTrue(result.profiles.isEmpty())
      assertFalse(result.selectedSubscriptionIds.containsKey(VpnEngine.VeilarkCore))
      assertEquals(VpnEngine.SingBox, result.selectedEngine)
      assertFailsWith<IllegalArgumentException> { SubscriptionCatalog.select(result, VpnEngine.VeilarkCore, record.id) }
    }
    val restored = SubscriptionCatalog.normalize(result.copy(selectedEngine = VpnEngine.VeilarkCore,
      profiles = listOf(core()), selectedSubscriptionIds = mapOf(VpnEngine.VeilarkCore to record.id)))
    assertEquals(if (FairwayRelease.accepted) result.copy(selectedEngine = VpnEngine.VeilarkCore) else result, restored)
  }

  @Test fun `Core subscription roundtrips real DPAPI without starting runtime`() {
    assumeTrue(Platform.isWindows())
    val directory = Files.createTempDirectory("veilark-core-store-")
    try {
      val path = directory.resolve("profiles.dat")
      val store = ProfileStore(path, logger = {})
      val record = SubscriptionRecord.user(listOf(core()))
      store.save(StoredProfiles(subscriptions = listOf(record), selectedEngine = VpnEngine.VeilarkCore))
      val restored = store.load()
      assertEquals(record, restored.subscriptions.single())
      assertEquals(if (FairwayRelease.accepted) listOf(core()) else emptyList(), restored.profiles)
      assertEquals(if (FairwayRelease.accepted) VpnEngine.VeilarkCore else VpnEngine.SingBox, restored.selectedEngine)
      assertFalse(Files.readAllBytes(path).toString(Charsets.UTF_8).contains("synthetic Core secret"))
    } finally {
      Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
  }

  @Test fun `Core UDP node never triggers a legacy TCP latency probe`() = runBlocking {
    val result = NodeLatencyProbe(resolver = { error("Must not resolve") },
      connector = { _, _, _ -> error("Must not connect") }).probe(core())
    assertEquals(NodeLatency(null, false), result.getValue("core-node"))
  }
}

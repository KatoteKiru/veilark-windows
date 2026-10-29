package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Exact shipped candidate through real JNA. Never calls AUTH or host start. */
class FairwayHostBetaPairingTest {
  @Test fun `shared Fairway beta accepts the complete Android policy before any OS effect`() = runBlocking {
    val dll = System.getenv("VEILARK_NATIVE_HOST_BETA_TEST_DLL")
    assumeTrue(Platform.isWindows() && !dll.isNullOrBlank(), "Explicit private host beta DLL required")
    val path = Path.of(dll!!)
    val pin = NativeArtifactPin(4_605_525, "c3a748ae6b98ca779d26770e81b6019d80161e0f3cbed051b4d9ffde86004623")
    FairwayNativeLibrary.load(path, pin).use { library ->
      assertEquals(11L, library.capabilities)
      assertTrue(library.supportsFullTunnel)
      assertTrue(library.requiresProcessRestartForUpdate)
      val secret = ByteArray(32) { (it + 1).toByte() }
      val owner = library.create(secret, ByteArray(32) { (it + 33).toByte() },
        ByteArray(16) { (it + 1).toByte() }, byteArrayOf(192.toByte(), 0, 2, 1), 443, 1360, 1)
      try {
        assertTrue(secret.all { it == 0.toByte() })
        val prefixes = CoreFairwayRuPolicy.bundledPrefixes()
        assertEquals(8662, prefixes.size)
        owner.configureHost(path.parent.resolve("not-installed-wintun.dll"),
          listOf(byteArrayOf(1, 1, 1, 1)), prefixes)
        val status = owner.status()
        assertEquals(1, status.phase)
        assertEquals(0, status.terminalOutcome)
        assertFalse(status.workerAlive)
        assertEquals(0, status.resourceMask)
        assertEquals(0L, status.txBytes)
        assertEquals(0L, status.rxBytes)
        assertEquals(0L, status.hostStage)
        assertEquals(0L, status.failedHostStage)
        owner.stopAndRelease(1_000)
        assertFalse(owner.hasOwnedResources())
      } finally { if (owner.hasOwnedResources()) owner.release() }
    }
  }
}

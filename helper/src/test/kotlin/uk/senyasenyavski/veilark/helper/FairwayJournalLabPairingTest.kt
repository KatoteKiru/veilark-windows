package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Frozen private lab pairing. No begin_auth, host start or real installation inputs. */
class FairwayJournalLabPairingTest {
  @Test fun `journal lab ABI pairs with consumer while full tunnel remains unavailable`() = runBlocking {
    val dll = System.getenv("VEILARK_NATIVE_JOURNAL_TEST_DLL")
    assumeTrue(Platform.isWindows() && !dll.isNullOrBlank(), "Explicit private journal lab DLL required")
    val path = Path.of(dll!!)
    val pin = NativeArtifactPin(40_331_361, "e33e479d42717ca75e95b52578c81ef84432c7c709573e89375917cd543b4212")
    repeat(2) {
      FairwayNativeLibrary.load(path, pin).use { library ->
        assertEquals(13L, library.capabilities)
        assertTrue(library.requiresProcessRestartForUpdate)
        assertTrue(library.supportsHostConfiguration)
        assertFalse(library.supportsFullTunnel)
        val key = ByteArray(32) { (it + 1).toByte() }
        val owner = library.create(key, ByteArray(32) { (it + 33).toByte() },
          ByteArray(16) { (it + 1).toByte() }, byteArrayOf(192.toByte(), 0, 2, 1), 443, 1360, 1)
        try {
          assertTrue(key.all { it == 0.toByte() })
          owner.configureHost(path.parent.resolve("not-installed-wintun.dll"),
            listOf(byteArrayOf(1, 1, 1, 1)), listOf("5.8.0.0/24"))
          val status = owner.status()
          assertEquals(1, status.phase)
          assertEquals(0, status.terminalOutcome)
          assertFalse(status.workerAlive)
          assertEquals(0, status.resourceMask)
          assertEquals(0L, status.txBytes)
          assertEquals(0L, status.rxBytes)
          // Consumer gates must refuse these calls before invoking native code.
          assertFailsWith<IllegalStateException> { owner.beginAuthForTunnel() }
          assertFailsWith<IllegalStateException> { owner.startHost() }
          owner.stopAndRelease(1_000)
          assertFalse(owner.hasOwnedResources())
        } finally { if (owner.hasOwnedResources()) owner.release() }
      }
    }
  }
}

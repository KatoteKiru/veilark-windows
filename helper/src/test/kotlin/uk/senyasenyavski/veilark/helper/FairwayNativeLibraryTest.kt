package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class FairwayNativeLibraryTest {
  @Test fun `frozen auth only DLL has exact memory lifecycle without network side effects`() {
    val dll = System.getenv("VEILARK_NATIVE_TEST_DLL")
    assumeTrue(Platform.isWindows() && !dll.isNullOrBlank(), "Explicit private development DLL required")
    // Fixed development artifact, NOT a shipping pin or discovery/update mechanism.
    val pin = NativeArtifactPin(32_646_791, "8fd89a1791b40feeeba06b852be8f686cf0b492d6dfb641ebbbd6139cb1d46dd")
    val library = FairwayNativeLibrary.load(Path.of(dll!!), pin)
    try {
      assertEquals(1L, library.capabilities)
      assertFalse(library.supportsFullTunnel)
      assertFalse(library.requiresProcessRestartForUpdate)
      assertFalse(library.supportsHostConfiguration)
      val invalid = ByteArray(32) { 1 }
      val failure = assertFailsWith<NativeCallException> { create(library, invalid, ByteArray(32)) }
      assertEquals(-1, failure.result)
      assertTrue(invalid.all { it == 0.toByte() })

      val shortKey = byteArrayOf(1)
      assertFailsWith<IllegalArgumentException> { create(library, shortKey) }
      assertEquals(0, shortKey[0].toInt())

      val key = ByteArray(32) { (it + 1).toByte() }
      val owner = create(library, key)
      assertTrue(key.all { it == 0.toByte() })
      try {
        assertTrue(owner.hasOwnedResources())
        val status = owner.status()
        assertEquals(1, status.phase)
        assertFalse(status.workerAlive)
        assertTrue(status.ownerAlive)
        assertTrue(status.canRelease)
        assertFailsWith<IllegalStateException> {
          owner.configureHost(Path.of("C:\\synthetic\\wintun.dll"), listOf(byteArrayOf(1, 1, 1, 1)), emptyList())
        }
        assertEquals(0, status.resourceMask)
        assertEquals(0L, status.txBytes)
        assertEquals(0L, status.rxBytes)
        assertContentEquals(ByteArray(4), status.internalIpv4())
        status.internalIpv4().fill(99)
        assertContentEquals(ByteArray(4), status.internalIpv4())
        assertFailsWith<IllegalStateException> { library.close() }
        // Guard prevents begin_auth, hence no endpoint bypass, socket or route changes.
        assertFailsWith<IllegalStateException> { owner.beginAuthForTunnel() }
        assertFailsWith<IllegalStateException> { owner.startHost() }
        assertEquals(1, owner.status().phase)

        val pool = Executors.newFixedThreadPool(3)
        try {
          val tasks = listOf(
            pool.submit { repeat(20) { owner.requestStop() } },
            pool.submit { repeat(20) { assertTrue(owner.join(0)) } },
            pool.submit { repeat(20) { assertTrue(owner.status().ownerAlive) } },
          )
          tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        assertTrue(owner.hasOwnedResources()) // Stopped worker still retains native owner.
        assertEquals(0, owner.status().resourceMask)
        assertTrue(owner.join(0))
      } finally { owner.release() }
      assertFalse(owner.hasOwnedResources())
      owner.release() // Local retry after successful release is harmless.
      assertFailsWith<IllegalStateException> { owner.status() }
    } finally { library.close() }
    library.close()
  }

  private fun create(library: FairwayNativeLibrary, key: ByteArray,
    pin: ByteArray = ByteArray(32) { (it + 33).toByte() },
  ): FairwayNativeLibrary.Owner = library.create(
    key, pin, ByteArray(16) { (it + 1).toByte() },
    byteArrayOf(192.toByte(), 0, 2, 1), 443, 1360, 1L,
  )
}

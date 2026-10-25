package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class FairwayAdapterIdentityStoreTest {
  @Test fun `GUID memory layout matches Windows fields and never exposes mutable state`() {
    val identity = FairwayAdapterIdentity(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))
    val expected = "33221100554477668899aabbccddeeff".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    assertContentEquals(expected, identity.windowsGuidBytes())
    identity.windowsGuidBytes().fill(0)
    assertContentEquals(expected, identity.windowsGuidBytes())
    assertEquals("FairwayAdapterIdentity([redacted])", identity.toString())
    assertFailsWith<IllegalArgumentException> { FairwayAdapterIdentity(UUID(0, 0)) }
  }

  @Test fun `independent store instances keep one installation GUID`() = directory { path ->
    val protector = SyntheticProtector()
    val first = FairwayAdapterIdentityStore(path, protector).loadOrCreate("installation-one").windowsGuidBytes()
    repeat(3) {
      assertContentEquals(first, FairwayAdapterIdentityStore(path, protector).loadOrCreate("installation-one").windowsGuidBytes())
    }
    assertTrue(first.any { it != 0.toByte() })
    assertFalse(Files.readAllBytes(path).toString(Charsets.UTF_8).contains("installation-one"))
  }

  @Test fun `corruption and installation mismatch never replace metadata`() = directory { path ->
    val protector = SyntheticProtector()
    val store = FairwayAdapterIdentityStore(path, protector)
    store.loadOrCreate("installation-one")
    val original = Files.readAllBytes(path)
    assertFailsWith<IllegalArgumentException> { store.loadOrCreate("installation-two") }
    assertContentEquals(original, Files.readAllBytes(path))
    val corrupt = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
    Files.write(path, corrupt)
    assertFailsWith<Exception> { store.loadOrCreate("installation-one") }
    assertContentEquals(corrupt, Files.readAllBytes(path))
  }

  @Test fun `protection failure cannot return an unpersisted random fallback`() = directory { path ->
    val failing = object : SecretProtector {
      override fun protect(value: ByteArray): ByteArray = error("synthetic persistence failure")
      override fun unprotect(value: ByteArray): ByteArray = error("not reached")
    }
    assertFailsWith<IllegalStateException> { FairwayAdapterIdentityStore(path, failing).loadOrCreate("installation-one") }
    assertFalse(Files.exists(path))
  }

  @Test fun `concurrent creators all use the same fully published record`() = directory { path ->
    val protector = SyntheticProtector()
    val start = CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(6)
    try {
      val tasks = (0 until 6).map {
        pool.submit<ByteArray> {
          start.await()
          FairwayAdapterIdentityStore(path, protector).loadOrCreate("installation-one").windowsGuidBytes()
        }
      }
      start.countDown()
      val ids = tasks.map { it.get(10, TimeUnit.SECONDS) }
      ids.forEach { assertContentEquals(ids.first(), it) }
      assertContentEquals(ids.first(), FairwayAdapterIdentityStore(path, protector).loadOrCreate("installation-one").windowsGuidBytes())
      Files.list(path.parent).use { assertEquals(listOf(path), it.toList()) }
    } finally { pool.shutdownNow() }
  }

  @Test fun `real Windows DPAPI preserves GUID and binding`() = directory { path ->
    assumeTrue(System.getProperty("os.name").startsWith("Windows", true))
    val first = FairwayAdapterIdentityStore(path).loadOrCreate("installation-one").windowsGuidBytes()
    assertContentEquals(first, FairwayAdapterIdentityStore(path).loadOrCreate("installation-one").windowsGuidBytes())
    assertFailsWith<IllegalArgumentException> { FairwayAdapterIdentityStore(path).loadOrCreate("installation-two") }
  }

  private fun directory(test: (Path) -> Unit) {
    val directory = Files.createTempDirectory("veilark-adapter-test-")
    try { test(directory.resolve("adapter.dpapi")) } finally {
      check(directory.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
      check(directory.fileName.toString().startsWith("veilark-adapter-test-"))
      Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }

  private class SyntheticProtector : SecretProtector {
    private val key = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES")
    override fun protect(value: ByteArray): ByteArray {
      val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
      return nonce + Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)); doFinal(value)
      }
    }
    override fun unprotect(value: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
      init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, value.copyOfRange(0, 12)))
      doFinal(value, 12, value.size - 12)
    }
  }
}

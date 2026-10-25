package uk.senyasenyavski.veilark.helper

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FairwayDeviceKeyStoreTest {
  @Test
  fun `stable reload preserves the key and interoperates with a separate JDK peer`() = directory { path ->
    val protector = SyntheticProtector()
    val first = FairwayDeviceKeyStore(path, protector).loadOrCreate("scope-one", "installation-one")
    val public = first.publicKey()
    val private = first.copyPrivateKey()
    FairwayDeviceKeyStore(path, protector).loadOrCreate("scope-one", "installation-one").use { second ->
      assertContentEquals(public, second.publicKey())
      assertContentEquals(private, second.copyPrivateKey())
      val factory = KeyFactory.getInstance("X25519")
      val peer = KeyPairGenerator.getInstance("X25519").generateKeyPair()
      val ourPrivate = factory.generatePrivate(XECPrivateKeySpec(NamedParameterSpec.X25519, private))
      val ourPublic = factory.generatePublic(XECPublicKeySpec(NamedParameterSpec.X25519, BigInteger(1, public.reversedArray())))
      fun agree(secret: java.security.PrivateKey, remote: java.security.PublicKey) =
        KeyAgreement.getInstance("X25519").run { init(secret); doPhase(remote, true); generateSecret() }
      val left = agree(ourPrivate, peer.public)
      val right = agree(peer.private, ourPublic)
      try { assertContentEquals(left, right) } finally { left.fill(0); right.fill(0) }
    }
    first.close()
    assertTrue(private.all { it == 0.toByte() })
    assertTrue(protector.observedPlaintexts.all { bytes -> bytes.all { it == 0.toByte() } })
  }

  @Test
  fun `different scopes have different durable keys and opaque filenames`() = directory { path ->
    val store = FairwayDeviceKeyStore(path, SyntheticProtector())
    store.loadOrCreate("scope-one", "installation-one").use { first ->
      store.loadOrCreate("scope-two", "installation-one").use { second ->
        assertFalse(first.publicKey().contentEquals(second.publicKey()))
      }
    }
    Files.list(path).use { files ->
      val names = files.map { it.fileName.toString() }.toList()
      assertEquals(2, names.size)
      assertTrue(names.all { it.matches(Regex("[0-9a-f]{64}\\.dpapi")) })
    }
    assertFailsWith<IllegalArgumentException> { store.loadOrCreate("https://example.test/bearer", "installation-one") }
  }

  @Test
  fun `identity mismatch never replaces the existing key`() = directory { path ->
    val protector = SyntheticProtector()
    val store = FairwayDeviceKeyStore(path, protector)
    store.loadOrCreate("scope-one", "installation-one").close()
    val file = onlyFile(path)
    val original = Files.readAllBytes(file)
    assertFailsWith<IllegalArgumentException> { store.loadOrCreate("scope-one", "different-installation") }
    assertContentEquals(original, Files.readAllBytes(file))
    store.loadOrCreate("scope-one", "installation-one").close()
  }

  @Test
  fun `corrupt and foreign protector records fail closed without replacement`() = directory { path ->
    val protector = SyntheticProtector()
    FairwayDeviceKeyStore(path, protector).loadOrCreate("scope-one", "installation-one").close()
    val file = onlyFile(path)
    val original = Files.readAllBytes(file)
    assertFailsWith<Exception> {
      FairwayDeviceKeyStore(path, SyntheticProtector()).loadOrCreate("scope-one", "installation-one")
    }
    assertContentEquals(original, Files.readAllBytes(file))
    val corrupt = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
    Files.write(file, corrupt)
    assertFailsWith<Exception> { FairwayDeviceKeyStore(path, protector).loadOrCreate("scope-one", "installation-one") }
    assertContentEquals(corrupt, Files.readAllBytes(file))
  }

  @Test
  fun `valid encryption with a mismatched public key still fails closed`() = directory { path ->
    val protector = SyntheticProtector()
    val store = FairwayDeviceKeyStore(path, protector)
    store.loadOrCreate("scope-one", "installation-one").close()
    val file = onlyFile(path)
    val plaintext = protector.unprotect(Files.readAllBytes(file))
    val corrupt = try {
      plaintext[plaintext.lastIndex] = (plaintext.last().toInt() xor 1).toByte()
      protector.protect(plaintext)
    } finally { plaintext.fill(0) }
    Files.write(file, corrupt)
    assertFailsWith<IllegalArgumentException> { store.loadOrCreate("scope-one", "installation-one") }
    assertContentEquals(corrupt, Files.readAllBytes(file))
  }

  @Test
  fun `failed protection leaves no durable or staging file and wipes plaintext`() = directory { path ->
    var observed: ByteArray? = null
    val failing = object : SecretProtector {
      override fun protect(value: ByteArray): ByteArray { observed = value; error("synthetic persistence failure") }
      override fun unprotect(value: ByteArray): ByteArray = error("not reached")
    }
    assertFailsWith<IllegalStateException> { FairwayDeviceKeyStore(path, failing).loadOrCreate("scope-one", "installation-one") }
    assertTrue(checkNotNull(observed).all { it == 0.toByte() })
    Files.list(path).use { assertEquals(0L, it.count()) }
  }

  @Test
  fun `close wipes issued private copies and rejects later access without leaking to strings`() = directory { path ->
    val key = FairwayDeviceKeyStore(path, SyntheticProtector()).loadOrCreate("scope-one", "installation-one")
    val first = key.copyPrivateKey()
    val second = key.copyPrivateKey()
    assertFalse(key.toString().contains(first.joinToString("") { "%02x".format(it) }))
    assertEquals("FairwayDeviceKey([redacted])", key.toString())
    key.close()
    key.close()
    assertTrue(first.all { it == 0.toByte() } && second.all { it == 0.toByte() })
    assertFailsWith<IllegalStateException> { key.copyPrivateKey() }
    assertFailsWith<IllegalStateException> { key.publicKey() }
  }

  @Test
  fun `concurrent creators return the same fully durable winner`() = directory { path ->
    val protector = SyntheticProtector()
    val start = CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(6)
    try {
      val futures = (0 until 6).map {
        pool.submit<ByteArray> {
          start.await()
          FairwayDeviceKeyStore(path, protector).loadOrCreate("scope-one", "installation-one").use { it.publicKey() }
        }
      }
      start.countDown()
      val publicKeys = futures.map { it.get(20, TimeUnit.SECONDS) }
      assertTrue(publicKeys.all { it.contentEquals(publicKeys.first()) })
      FairwayDeviceKeyStore(path, protector).loadOrCreate("scope-one", "installation-one").use {
        assertContentEquals(publicKeys.first(), it.publicKey())
      }
      Files.list(path).use { assertEquals(1L, it.count()) }
    } finally { pool.shutdownNow() }
  }

  private fun onlyFile(directory: Path): Path = Files.list(directory).use { it.toList().single() }

  private fun directory(test: (Path) -> Unit) {
    val path = Files.createTempDirectory("fairway-device-key-test")
    try { test(path) } finally {
      Files.walk(path).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }

  /** Authenticated synthetic protector; never used by production code. */
  private class SyntheticProtector : SecretProtector {
    private val key = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES")
    val observedPlaintexts = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    override fun protect(value: ByteArray): ByteArray {
      observedPlaintexts.add(value)
      val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
      return nonce + Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)); doFinal(value)
      }
    }
    override fun unprotect(value: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
      init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, value.copyOfRange(0, 12)))
      doFinal(value, 12, value.size-12)
    }.also(observedPlaintexts::add)
  }
}

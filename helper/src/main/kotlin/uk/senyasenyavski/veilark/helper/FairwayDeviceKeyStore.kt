package uk.senyasenyavski.veilark.helper

import java.io.Closeable
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.XECPrivateKey
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.KeyAgreement

/** Local preparation only. Enrollment must send [publicKey] and never [copyPrivateKey]. */
class FairwayDeviceKey internal constructor(privateKey: ByteArray, publicKey: ByteArray) : Closeable {
  private var secret: ByteArray? = privateKey.copyOf()
  private val publicBytes = publicKey.copyOf()
  private val issuedCopies = mutableListOf<ByteArray>()

  @Synchronized
  fun publicKey(): ByteArray {
    check(secret != null) { "Device key is closed" }
    return publicBytes.copyOf()
  }

  /** Borrowed lifetime: this copy is wiped when this owner closes. Do not retain it. */
  @Synchronized
  fun copyPrivateKey(): ByteArray = checkNotNull(secret) { "Device key is closed" }
    .copyOf().also(issuedCopies::add)

  @Synchronized
  override fun close() {
    secret?.fill(0)
    secret = null
    issuedCopies.forEach { it.fill(0) }
    issuedCopies.clear()
  }

  override fun toString(): String = "FairwayDeviceKey([redacted])"
}

/**
 * One DPAPI-protected X25519 key per opaque entitlement scope, bound to the caller's
 * installation identity. Corruption and identity conflicts never rotate a key.
 * A fully written encrypted staging file is published with a create-only hard link:
 * competing creators load the same durable winner and never replace an existing file.
 */
class FairwayDeviceKeyStore(
  private val directory: Path = VeilarkPaths.dataDirectory.resolve("fairway-device-keys"),
  private val protector: SecretProtector = DpapiSecretProtector(),
) {
  fun loadOrCreate(bindingScope: String, installationId: String): FairwayDeviceKey {
    require(bindingScope.matches(Regex("[A-Za-z0-9_-]{8,128}"))) { "Opaque device binding scope required" }
    require(installationId.isNotBlank() && installationId.length <= 512) { "Installation identity required" }
    val scope = digest(bindingScope)
    val installation = digest(installationId)
    Files.createDirectories(directory)
    require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { "Device key directory is not a directory" }
    val destination = directory.resolve(scope.joinToString("") { "%02x".format(it) } + ".dpapi")
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) return load(destination, scope, installation)

    val pair = KeyPairGenerator.getInstance("X25519").generateKeyPair()
    val privateBytes = (pair.private as XECPrivateKey).scalar.orElseThrow()
    var plaintext: ByteArray? = null
    var protected: ByteArray? = null
    var staging: Path? = null
    try {
      val publicBytes = derivePublic(privateBytes)
      val clear = ByteBuffer.allocate(RECORD_BYTES)
        .putInt(MAGIC).putInt(1).put(scope).put(installation).put(privateBytes).put(publicBytes).array()
      plaintext = clear
      val encrypted = protector.protect(clear)
      protected = encrypted
      require(encrypted !== clear && encrypted.size in 1..MAX_BYTES) { "Invalid protected device key" }
      val checked = protector.unprotect(encrypted)
      try {
        require(checked !== encrypted && MessageDigest.isEqual(checked, clear)) { "Device key protection verification failed" }
      } finally {
        if (checked !== encrypted) checked.fill(0)
      }
      staging = Files.createTempFile(directory, ".device-key-", ".pending")
      FileChannel.open(staging, StandardOpenOption.WRITE).use { channel ->
        val bytes = ByteBuffer.wrap(encrypted)
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
      }
      try {
        // No REPLACE_EXISTING/ATOMIC_MOVE fallback: some providers overwrite on rename.
        Files.createLink(destination, staging)
      } catch (_: FileAlreadyExistsException) {
        // The winning record still must decrypt and match both identities below.
      }
      return load(destination, scope, installation)
    } finally {
      privateBytes.fill(0)
      plaintext?.fill(0)
      protected?.fill(0)
      runCatching { pair.private.destroy() }
      staging?.let(Files::deleteIfExists)
    }
  }

  private fun load(path: Path, scope: ByteArray, installation: ByteArray): FairwayDeviceKey {
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Device key file is invalid" }
    val encrypted = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
      val size = channel.size()
      require(size in 1..MAX_BYTES.toLong()) { "Device key file size is invalid" }
      val bytes = ByteBuffer.allocate(size.toInt())
      while (bytes.hasRemaining()) check(channel.read(bytes) > 0) { "Device key file is truncated" }
      check(channel.read(ByteBuffer.allocate(1)) == -1) { "Device key file changed while reading" }
      bytes.array()
    }
    var plaintext: ByteArray? = null
    val privateBytes = ByteArray(32)
    try {
      plaintext = protector.unprotect(encrypted)
      require(plaintext !== encrypted && plaintext.size == RECORD_BYTES) { "Invalid protected device key record" }
      val bytes = ByteBuffer.wrap(plaintext)
      require(bytes.int == MAGIC && bytes.int == 1) { "Unknown device key record" }
      val storedScope = ByteArray(32).also(bytes::get)
      val storedInstallation = ByteArray(32).also(bytes::get)
      require(MessageDigest.isEqual(storedScope, scope) && MessageDigest.isEqual(storedInstallation, installation)) {
        "Device key identity mismatch"
      }
      bytes.get(privateBytes)
      val publicBytes = ByteArray(32).also(bytes::get)
      require(MessageDigest.isEqual(publicBytes, derivePublic(privateBytes))) { "Device key pair mismatch" }
      return FairwayDeviceKey(privateBytes, publicBytes)
    } finally {
      plaintext?.fill(0)
      encrypted.fill(0)
      privateBytes.fill(0)
    }
  }

  private fun digest(value: String): ByteArray {
    val bytes = value.toByteArray(Charsets.UTF_8)
    return try { MessageDigest.getInstance("SHA-256").digest(bytes) } finally { bytes.fill(0) }
  }

  private fun derivePublic(scalar: ByteArray): ByteArray {
    val factory = KeyFactory.getInstance("X25519")
    val privateKey = factory.generatePrivate(XECPrivateKeySpec(NamedParameterSpec.X25519, scalar))
    try {
      val base = factory.generatePublic(XECPublicKeySpec(NamedParameterSpec.X25519, BigInteger.valueOf(9)))
      return KeyAgreement.getInstance("X25519").run {
        init(privateKey)
        doPhase(base, true)
        generateSecret()
      }
    } finally {
      runCatching { privateKey.destroy() }
    }
  }

  private companion object {
    const val MAGIC = 0x56444b31
    const val RECORD_BYTES = 136
    const val MAX_BYTES = 4096
  }
}

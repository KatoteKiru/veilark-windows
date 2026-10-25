package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ClientInstallationIdentity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** Installation metadata, not a client credential or hardware identity. */
class FairwayAdapterIdentity internal constructor(private val guid: UUID) {
  init { require(guid.mostSignificantBits != 0L || guid.leastSignificantBits != 0L) { "Nonzero adapter GUID required" } }

  /** Windows GUID memory layout: DWORD/WORD/WORD little endian, final8 bytes unchanged. */
  fun windowsGuidBytes(): ByteArray = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
    .putInt((guid.mostSignificantBits ushr 32).toInt())
    .putShort((guid.mostSignificantBits ushr 16).toShort())
    .putShort(guid.mostSignificantBits.toShort())
    .order(ByteOrder.BIG_ENDIAN).putLong(guid.leastSignificantBits).array()

  override fun toString(): String = "FairwayAdapterIdentity([redacted])"
}

/**
 * One durable GUID per installation, independent of subscriptions and private keys.
 * DPAPI protects record integrity and installation binding. A create-only hard link
 * publishes a fully written record; concurrent creators must load the same winner.
 * Failure/corruption never falls back to a random transient GUID or rotates metadata.
 */
class FairwayAdapterIdentityStore(
  private val path: Path = VeilarkPaths.dataDirectory.resolve("fairway-adapter.dpapi"),
  private val protector: SecretProtector = DpapiSecretProtector(),
) {
  fun loadOrCreate(installationId: String = ClientInstallationIdentity.get()): FairwayAdapterIdentity {
    require(installationId.isNotBlank() && installationId.length <= 512) { "Installation identity required" }
    val input = installationId.toByteArray(Charsets.UTF_8)
    val binding = try { MessageDigest.getInstance("SHA-256").digest(input) } finally { input.fill(0) }
    val parent = path.toAbsolutePath().parent
    Files.createDirectories(parent)
    require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) { "Invalid adapter metadata directory" }
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return load(binding)
    val guid = UUID.randomUUID()
    val clear = ByteBuffer.allocate(RECORD_BYTES).putInt(MAGIC).putInt(1).put(binding)
      .putLong(guid.mostSignificantBits).putLong(guid.leastSignificantBits).array()
    var encrypted: ByteArray? = null
    var staging: Path? = null
    try {
      val protected = protector.protect(clear)
      encrypted = protected
      require(protected !== clear && protected.size in 1..MAX_BYTES) { "Invalid protected adapter identity" }
      val checked = protector.unprotect(protected)
      try {
        require(checked !== protected && MessageDigest.isEqual(checked, clear)) { "Adapter protection verification failed" }
      } finally { if (checked !== protected) checked.fill(0) }
      staging = Files.createTempFile(parent, ".adapter-id-", ".pending")
      FileChannel.open(staging, StandardOpenOption.WRITE).use { channel ->
        val bytes = ByteBuffer.wrap(protected)
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
      }
      try { Files.createLink(path, staging) } catch (_: FileAlreadyExistsException) {
        // Only the fully published winner is authoritative, including its binding.
      }
      return load(binding)
    } finally {
      clear.fill(0)
      encrypted?.fill(0)
      staging?.let(Files::deleteIfExists)
    }
  }

  private fun load(binding: ByteArray): FairwayAdapterIdentity {
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Invalid adapter identity file" }
    val encrypted = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
      val size = channel.size()
      require(size in 1..MAX_BYTES.toLong()) { "Invalid adapter identity size" }
      val bytes = ByteBuffer.allocate(size.toInt())
      while (bytes.hasRemaining()) check(channel.read(bytes) > 0) { "Truncated adapter identity" }
      check(channel.read(ByteBuffer.allocate(1)) == -1) { "Adapter identity changed while reading" }
      bytes.array()
    }
    var clear: ByteArray? = null
    try {
      val record = protector.unprotect(encrypted)
      clear = record
      require(record !== encrypted && record.size == RECORD_BYTES) { "Invalid protected adapter record" }
      val bytes = ByteBuffer.wrap(record)
      require(bytes.int == MAGIC && bytes.int == 1) { "Unknown adapter identity record" }
      require(MessageDigest.isEqual(ByteArray(32).also(bytes::get), binding)) { "Adapter installation mismatch" }
      return FairwayAdapterIdentity(UUID(bytes.long, bytes.long))
    } finally {
      clear?.fill(0)
      encrypted.fill(0)
    }
  }

  private companion object {
    const val MAGIC = 0x56414731
    const val RECORD_BYTES = 56
    const val MAX_BYTES = 4096
  }
}

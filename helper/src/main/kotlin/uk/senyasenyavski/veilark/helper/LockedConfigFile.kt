package uk.senyasenyavski.veilark.helper

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * A credential-bearing native-core configuration written by the (usually
 * elevated) app into the user-writable runtime directory.
 *
 * The file gets a fresh random name and is created with `CREATE_NEW`, so a
 * pre-planted file or link is never followed. On Windows the creating handle
 * denies other writers and deleters (`FILE_SHARE_READ` only) and stays open
 * until [close], so an unelevated process of the same user cannot replace the
 * bytes between `sing-box check`/wizard validation and the elevated core
 * reading them. Readers (sing-box, TrustTunnel) share read+write access and
 * are therefore unaffected.
 *
 * Residual risk (documented in docs/SECURITY_HARDENING_2026-09-30.md): the
 * profile store itself is DPAPI current-user data, so a same-user process can
 * already alter profiles; this class closes the file-swap race only.
 */
internal class LockedConfigFile private constructor(
  val path: Path,
  private val channel: FileChannel,
  /** True when the Windows share-mode lock is active for this handle. */
  val writeLocked: Boolean,
) : AutoCloseable {
  @Volatile
  private var closed = false

  /** Releases the lock and removes the credential-bearing file. Idempotent. */
  override fun close() {
    if (closed) return
    closed = true
    runCatching { channel.close() }
    runCatching { Files.deleteIfExists(path) }.onFailure {
      SafeLog.write("Не удалось удалить временную конфигурацию ядра: ${it::class.simpleName}")
    }
  }

  companion object {
    fun create(directory: Path, prefix: String, suffix: String, content: String): LockedConfigFile {
      require(NAME_PART.matches(prefix) && NAME_PART.matches(suffix)) { "Invalid config name" }
      Files.createDirectories(directory)
      removeStale(directory, prefix, suffix)
      val path = directory.resolve("$prefix${UUID.randomUUID()}$suffix")
      val shareLock = windowsShareLock
      val options = buildSet<OpenOption> {
        add(StandardOpenOption.CREATE_NEW)
        add(StandardOpenOption.WRITE)
        add(LinkOption.NOFOLLOW_LINKS)
        addAll(shareLock)
      }
      val channel = FileChannel.open(path, options)
      try {
        val bytes = ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
      } catch (error: Throwable) {
        runCatching { channel.close() }
        runCatching { Files.deleteIfExists(path) }
        throw error
      }
      return LockedConfigFile(path, channel, writeLocked = shareLock.isNotEmpty())
    }

    /**
     * Deletes leftovers of an abnormally terminated earlier run. A file still
     * locked by a live core start cannot be deleted and is simply skipped.
     */
    internal fun removeStale(directory: Path, prefix: String, suffix: String) {
      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return
      Files.newDirectoryStream(directory).use { entries ->
        entries.forEach { candidate ->
          val name = candidate.fileName.toString()
          if (name.startsWith(prefix) && name.endsWith(suffix) &&
            RANDOM_PART.matches(name.removePrefix(prefix).removeSuffix(suffix))
          ) {
            runCatching { Files.deleteIfExists(candidate) }
          }
        }
      }
    }

    private val NAME_PART = Regex("[0-9A-Za-z._-]{1,32}")
    private val RANDOM_PART = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    private val isWindows: Boolean =
      System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /**
     * `com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE/NOSHARE_DELETE`
     * (module jdk.unsupported). Resolved reflectively so a runtime image
     * without that module still starts the VPN, only without the lock.
     */
    internal val windowsShareLock: List<OpenOption> by lazy {
      if (!isWindows) return@lazy emptyList()
      runCatching {
        val type = Class.forName("com.sun.nio.file.ExtendedOpenOption")
        listOf("NOSHARE_WRITE", "NOSHARE_DELETE").map { name ->
          type.enumConstants.first { (it as Enum<*>).name == name } as OpenOption
        }
      }.getOrElse {
        SafeLog.write("Блокировка конфигурации ядра недоступна: ${it::class.simpleName}")
        emptyList()
      }
    }
  }
}

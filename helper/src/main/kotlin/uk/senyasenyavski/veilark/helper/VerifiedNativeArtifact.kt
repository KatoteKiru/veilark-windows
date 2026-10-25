package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/** Release metadata must come from the packaged application, never a sibling DLL sidecar. */
internal class NativeArtifactPin(val size: Long, val sha256: String) {
  init {
    require(size > 0)
    require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
  }
}

/**
 * Checks a supplied artifact without executing it. On Windows, a read handle denies
 * concurrent writes/deletion through verification and the synchronous loader callback.
 * The callback must finish loading before returning, not schedule a deferred path load.
 * This is not an ACL, Authenticode, dependency-search or native ABI verification policy.
 */
internal object VerifiedNativeArtifact {
  fun <T> withVerifiedPath(path: Path, pin: NativeArtifactPin, load: (Path) -> T): T {
    check(Platform.isWindows()) { "Native artifact verification requires Windows" }
    require(path.isAbsolute) { "Native artifact path must be absolute" }
    require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Native artifact must be a regular file" }
    val canonical = path.toRealPath()
    val kernel = Kernel32.INSTANCE
    val handle = kernel.CreateFile(
      canonical.toString(), WinNT.GENERIC_READ, WinNT.FILE_SHARE_READ,
      null, WinNT.OPEN_EXISTING, WinNT.FILE_ATTRIBUTE_NORMAL, null,
    )
    check(handle != null && handle != WinBase.INVALID_HANDLE_VALUE) { "Cannot lock native artifact" }
    var failure: Throwable? = null
    try {
      check(Files.size(canonical) == pin.size) { "Native artifact size mismatch" }
      val digest = MessageDigest.getInstance("SHA-256")
      Files.newInputStream(canonical).use { input ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          total += count
          check(total <= pin.size) { "Native artifact size mismatch" }
          digest.update(buffer, 0, count)
        }
        check(total == pin.size) { "Native artifact size mismatch" }
      }
      val expected = pin.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
      check(MessageDigest.isEqual(expected, digest.digest())) { "Native artifact digest mismatch" }
      return load(canonical)
    } catch (error: Throwable) {
      failure = error
      throw error
    } finally {
      if (!kernel.CloseHandle(handle)) {
        val closeError = IllegalStateException("Cannot close native artifact lock")
        if (failure != null) failure.addSuppressed(closeError) else throw closeError
      }
    }
  }
}

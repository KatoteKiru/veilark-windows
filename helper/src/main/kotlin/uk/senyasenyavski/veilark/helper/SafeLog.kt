package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

object SafeLog {
  private val secretPatterns = listOf(
    // Covers TOML/query `key = value` as well as JSON `"key": "value"`.
    Regex("""(?i)(uuid|password|token|private_key|pre_shared_key|short_id|server_name)["']?\s*[:=]\s*["']?[^"',\s]+"""),
    Regex("""(?i)(vless|vmess|trojan|ss|hysteria2|hy2|tuic|anytls)://\S+"""),
    Regex("""(?i)tt://\S+"""),
    Regex("""https://[^\s/?#]+/[^\s]+"""),
    Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])"""),
  )

  /**
   * Never throws: the journal is diagnostics only, and a full disk or a locked
   * log file must not abort a core start or, worse, a core stop.
   */
  @Synchronized
  fun write(message: String) {
    runCatching { writeTo(VeilarkPaths.logFile, message) }
  }

  @Synchronized
  fun writeThrowable(context: String, error: Throwable) {
    val summary = buildString {
      append(context)
      append(": ")
      append(error::class.qualifiedName ?: error::class.simpleName ?: "Throwable")
      error.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
      error.stackTrace.take(MAX_STACK_FRAMES).forEach { frame ->
        appendLine()
        append("  at ")
        append(frame.className)
        append('.')
        append(frame.methodName)
        append('(')
        append(frame.fileName ?: "Unknown")
        if (frame.lineNumber >= 0) append(':').append(frame.lineNumber)
        append(')')
      }
    }
    runCatching { writeTo(VeilarkPaths.logFile, summary) }
  }

  internal fun writeTo(path: java.nio.file.Path, message: String) {
    rotateIfNeeded(path)
    val safe = redact(message)
    Files.writeString(
      path,
      "${Instant.now()} $safe${System.lineSeparator()}",
      Charsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND,
    )
  }

  internal fun redact(message: String): String =
    secretPatterns.fold(message.take(2_000)) { value, pattern ->
      pattern.replace(value, "[скрыто]")
    }

  private fun rotateIfNeeded(path: java.nio.file.Path) {
    Files.createDirectories(path.parent)
    if (!Files.exists(path) ||
      Files.size(path) < MAX_LOG_BYTES
    ) {
      return
    }
    val rotatedName = path.fileName.toString().let { name ->
      if (name.endsWith(".log", ignoreCase = true)) {
        "${name.dropLast(4)}.1.log"
      } else {
        "$name.1"
      }
    }
    Files.move(
      path,
      path.resolveSibling(rotatedName),
      StandardCopyOption.REPLACE_EXISTING,
    )
  }

  private const val MAX_STACK_FRAMES = 32
  private const val MAX_LOG_BYTES = 2L * 1024 * 1024
}

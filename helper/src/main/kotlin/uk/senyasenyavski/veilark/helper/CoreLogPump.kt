package uk.senyasenyavski.veilark.helper

import java.util.ArrayDeque
import kotlin.concurrent.thread

/**
 * Streams a VPN core's console output into the redacted journal and watches it
 * for the two events the session needs to react to immediately: the core
 * announcing that it is serving traffic, and the core reporting a fatal
 * configuration or permission error.
 *
 * Reacting to a fatal line turns a 45-second readiness timeout into a message
 * the user sees at once, and keeps the real reason instead of a generic
 * "interface was not created".
 */
internal class CoreLogPump(
  private val process: Process,
  private val engineName: String,
  private val readyMarkers: List<String>,
  private val fatalMarkers: List<String>,
  private val diagnosticOnly: Boolean = false,
  private val logger: (String) -> Unit = SafeLog::write,
) {
  @Volatile
  private var readySeen = false

  @Volatile
  private var fatalLine: String? = null
  private val recent = ArrayDeque<String>()

  val ready: Boolean get() = readySeen
  val fatal: String? get() = fatalLine

  /** The most recent core output, newest last, for diagnostics in error text. */
  fun tail(): List<String> = synchronized(recent) { recent.toList() }

  fun start() {
    thread(name = "veilark-core-log-$engineName", isDaemon = true) {
      runCatching {
        process.inputStream.bufferedReader().useLines { lines ->
          lines.forEach(::consume)
        }
      }
    }
  }

  internal fun consume(rawLine: String) {
    val line = ANSI_ESCAPE.replace(rawLine, "").trim()
    if (line.isEmpty()) return
    val nativeRecord = if (diagnosticOnly) SING_BOX_RECORD.find(line) else null
    val severity = nativeRecord?.groupValues?.get(1)
    val message = nativeRecord?.groupValues?.get(2)
    // Per-connection output can contain attacker-controlled host names/tags.
    // Those strings must never impersonate a startup or fatal event.
    val isReady = if (diagnosticOnly) {
      severity == "INFO" && message != null && SING_BOX_STARTED.matches(message)
    } else {
      readyMarkers.any { line.contains(it, ignoreCase = true) }
    }
    val isFatal = if (diagnosticOnly) {
      severity == "FATAL"
    } else {
      fatalMarkers.any { line.contains(it, ignoreCase = true) }
    }
    // State detection must not depend on disk space or journal permissions.
    if (isReady) readySeen = true
    if (fatalLine == null && isFatal) fatalLine = SafeLog.redact(line).take(300)
    // The sing-box process uses INFO for its startup marker, but ordinary
    // per-connection INFO output must not fill disk, UI logs or the tail.
    if (diagnosticOnly && !isReady && severity !in DIAGNOSTIC_SEVERITIES) return
    val safeLine = SafeLog.redact(line)
    synchronized(recent) {
      recent.addLast(safeLine)
      while (recent.size > MAX_RECENT_LINES) recent.removeFirst()
    }
    // Keep draining the pipe even if diagnostics cannot be written. Otherwise
    // a full pipe can stall a healthy core after an unrelated logging failure.
    runCatching { logger(safeLine) }
  }

  private companion object {
    val ANSI_ESCAPE = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
    val SING_BOX_RECORD = Regex("""(?:^|\s)(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)(?:\[[^\]]*])?\s+(.*)$""")
    val SING_BOX_STARTED = Regex("""sing-box started \([^\r\n]*\)""")
    val DIAGNOSTIC_SEVERITIES = setOf("WARN", "ERROR", "FATAL")
    const val MAX_RECENT_LINES = 40
  }
}

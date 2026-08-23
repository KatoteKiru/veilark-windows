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

  private fun consume(rawLine: String) {
    val line = ANSI_ESCAPE.replace(rawLine, "").trim()
    if (line.isEmpty()) return
    synchronized(recent) {
      recent.addLast(line)
      while (recent.size > MAX_RECENT_LINES) recent.removeFirst()
    }
    SafeLog.write(line)
    if (!readySeen && readyMarkers.any { line.contains(it, ignoreCase = true) }) {
      readySeen = true
    }
    if (fatalLine == null && fatalMarkers.any { line.contains(it, ignoreCase = true) }) {
      fatalLine = line.take(300)
    }
  }

  private companion object {
    val ANSI_ESCAPE = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
    const val MAX_RECENT_LINES = 40
  }
}

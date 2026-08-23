package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal data class CapturedProcess(
  val exitCode: Int?,
  val output: String,
  val timedOut: Boolean,
) {
  val succeeded: Boolean get() = !timedOut && exitCode == 0
}

internal fun Process.capture(
  timeoutMillis: Long,
  maximumOutputChars: Int = 32_000,
): CapturedProcess {
  val output = StringBuilder()
  val reader = thread(name = "veilark-process-capture", isDaemon = true) {
    runCatching {
      inputStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
          synchronized(output) {
            if (output.length < maximumOutputChars) {
              val remaining = maximumOutputChars - output.length
              output.append(line.take(remaining)).append('\n')
            }
          }
        }
      }
    }
  }
  val finished = waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
  if (!finished) {
    destroyForcibly()
    waitFor(2, TimeUnit.SECONDS)
  }
  reader.join(2_000)
  return CapturedProcess(
    exitCode = runCatching { exitValue() }.getOrNull(),
    output = synchronized(output) { output.toString() },
    timedOut = !finished,
  )
}

/** Waits for a short-lived helper without making Stop wait for its timeout. */
internal suspend fun Process.captureCancellable(
  timeoutMillis: Long,
  maximumOutputChars: Int = 32_000,
): CapturedProcess = try {
  runInterruptible(Dispatchers.IO) { capture(timeoutMillis, maximumOutputChars) }
} catch (cancellation: CancellationException) {
  destroy()
  if (!waitFor(500, TimeUnit.MILLISECONDS)) destroyForcibly()
  throw cancellation
}

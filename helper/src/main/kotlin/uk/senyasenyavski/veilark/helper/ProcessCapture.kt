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
  require(timeoutMillis > 0) { "Тайм-аут процесса должен быть положительным" }
  require(maximumOutputChars >= 0) { "Лимит вывода не может быть отрицательным" }
  val output = StringBuilder()
  val reader = thread(name = "veilark-process-capture", isDaemon = true) {
    runCatching {
      inputStream.bufferedReader().use { input ->
        // readLine allocates an entire native diagnostic before the output cap
        // can be applied. Drain in fixed chunks even after the cap is reached,
        // so an oversized line cannot exhaust memory or block the child pipe.
        val buffer = CharArray(4_096)
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          synchronized(output) {
            if (output.length < maximumOutputChars) {
              val remaining = maximumOutputChars - output.length
              output.append(buffer, 0, minOf(count, remaining))
            }
          }
        }
      }
    }
  }
  val finished = try {
    waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
  } catch (interrupted: InterruptedException) {
    terminateCapturedProcess(gracefulMillis = 500)
    runCatching { inputStream.close() }
    runCatching { reader.join(2_000) }
    throw interrupted
  }
  if (!finished) {
    terminateCapturedProcess(gracefulMillis = 0)
  }
  if (isAlive) runCatching { inputStream.close() }
  try {
    reader.join(2_000)
  } catch (interrupted: InterruptedException) {
    terminateCapturedProcess(gracefulMillis = 0)
    runCatching { inputStream.close() }
    throw interrupted
  }
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
  terminateCapturedProcess(gracefulMillis = 500)
  runCatching { inputStream.close() }
  throw cancellation
}

private fun Process.terminateCapturedProcess(gracefulMillis: Long) {
  if (!isAlive) return
  if (gracefulMillis > 0) {
    runCatching { destroy() }
    val stopped = runCatching { waitFor(gracefulMillis, TimeUnit.MILLISECONDS) }
      .getOrDefault(false)
    if (stopped) return
  }
  runCatching { destroyForcibly() }
  runCatching { waitFor(2, TimeUnit.SECONDS) }
}

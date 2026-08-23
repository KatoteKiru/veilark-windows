package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class ProcessCaptureTest {
  @Test
  fun `cancelling capture terminates the helper process`() = runBlocking {
    val powershell = Path.of(
      System.getenv("SystemRoot") ?: "C:\\Windows",
      "System32",
      "WindowsPowerShell",
      "v1.0",
      "powershell.exe",
    )
    if (!Files.isRegularFile(powershell)) return@runBlocking

    val process = ProcessBuilder(
      powershell.toString(),
      "-NoLogo",
      "-NoProfile",
      "-NonInteractive",
      "-Command",
      "Start-Sleep -Seconds 30",
    ).redirectErrorStream(true).start()
    val capture = async { process.captureCancellable(30_000) }
    try {
      // Let the IO worker enter Process.waitFor before delivering cancellation.
      delay(200)
      capture.cancelAndJoin()

      withTimeout(3_000) {
        while (process.isAlive) delay(10)
      }
      assertFalse(process.isAlive)
    } finally {
      if (process.isAlive) process.destroyForcibly()
    }
  }
}

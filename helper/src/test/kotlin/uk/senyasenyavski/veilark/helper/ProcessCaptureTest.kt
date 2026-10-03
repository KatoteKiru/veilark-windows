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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessCaptureTest {
  @Test
  fun `capture strictly bounds retained output and drains oversized lines`() {
    val stream = java.io.ByteArrayInputStream(("a".repeat(200_000) + "\nend\n").toByteArray())
    val process = completedProcess(stream)
    val result = process.capture(1_000, maximumOutputChars = 17)
    assertTrue(result.succeeded)
    assertEquals("a".repeat(17), result.output)
    assertEquals(0, stream.available(), "Output must still be drained after the capture limit")
  }

  @Test
  fun `zero output limit drains without retaining text`() {
    val stream = java.io.ByteArrayInputStream("first\nsecond\n".toByteArray())
    val result = completedProcess(stream).capture(1_000, maximumOutputChars = 0)
    assertEquals("", result.output)
    assertEquals(0, stream.available())
  }

  private fun completedProcess(stream: java.io.InputStream): Process = object : Process() {
    override fun getInputStream() = stream
    override fun getErrorStream() = java.io.InputStream.nullInputStream()
    override fun getOutputStream() = java.io.OutputStream.nullOutputStream()
    override fun waitFor() = 0
    override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit) = true
    override fun exitValue() = 0
    override fun destroy() = Unit
    override fun isAlive() = false
  }

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

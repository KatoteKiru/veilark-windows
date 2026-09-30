package uk.senyasenyavski.veilark.helper

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreProcessJobTest {
  private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

  @Test
  fun `non-windows job is inert and never throws`() {
    if (isWindows) return
    CoreProcessJob.create().use { job ->
      assertFalse(job.active)
      val process = ProcessBuilder("sleep", "5").start()
      try {
        assertFalse(job.assign(process))
      } finally {
        process.destroyForcibly()
      }
    }
  }

  @Test
  fun `closing the job terminates assigned processes on windows`() {
    if (!isWindows) return
    val job = CoreProcessJob.create()
    assertTrue(job.active, "Job object must be available on Windows")
    val powershell = System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
    val process = ProcessBuilder(powershell, "-NoProfile", "-Command", "Start-Sleep -Seconds 60").start()
    try {
      assertTrue(job.assign(process))
      assertTrue(process.isAlive)
      job.close()
      assertTrue(process.waitFor(10, TimeUnit.SECONDS), "assigned process must end with its job")
    } finally {
      process.destroyForcibly()
    }
  }
}

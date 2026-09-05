package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

/** Explicit opt-in only on an isolated, elevated GitHub Windows runner. */
class SingBoxNativeTunTest {
  @Test
  fun `real Windows adapter reaches readiness without installing a default route`() = runBlocking {
    assumeTrue(System.getenv("GITHUB_ACTIONS") == "true")
    assumeTrue(System.getenv("VEILARK_TEST_NATIVE_TUN") == "1")
    assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    assertTrue(ElevationManager().isElevated(), "Opt-in native TUN job must run elevated")
    val executable = Path.of(requireNotNull(System.getenv("SING_BOX_CHECKER")))
    assertTrue(Files.isRegularFile(executable), "Native test executable must exist")
    // Refuse to exercise janitor code alongside a pre-existing VPN even on CI.
    assertTrue(WindowsNetwork.tunnels().isEmpty(), "Native test requires an isolated runner")
    val profile = Profile(
      id = "ci-synthetic-tun",
      name = "CI synthetic TUN",
      engine = VpnEngine.SingBox,
      config = """{
        "log":{"level":"warn"},
        "inbounds":[{"type":"tun","interface_name":"Veilark",
          "address":["198.18.252.1/30"],"auto_route":false,"strict_route":false,
          "stack":"system","mtu":1280}],
        "outbounds":[{"type":"direct","tag":"direct"}],
        "route":{"final":"direct"}
      }""".trimIndent(),
      nodes = emptyList(),
      sourceLabel = "synthetic CI fixture",
    )
    val controller = SingBoxProcessController(executableOverride = executable)
    val startedAt = System.nanoTime()
    try {
      val health = withTimeout(35_000) { controller.start(profile) }
      assertEquals(EngineHealth.Healthy, health, "Native TUN startup must report readiness")
      assertTrue(controller.isAlive(), "Native TUN process must remain alive after readiness")
      val startupMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
      assertTrue(startupMillis < 30_000, "Native TUN startup took ${startupMillis}ms; must finish before the 30s deadline")
      assertEquals(0, defaultRoutesOnTestAdapter(), "Synthetic TUN must not take over Internet routing")
    } finally {
      withTimeout(15_000) { controller.stop() }
    }
    assertFalse(controller.isAlive(), "Native TUN process must stop")
    assertFalse(Files.exists(VeilarkPaths.activeConfig), "Transient native configuration must be removed")
    val remaining = WindowsNetwork.matching { it.alias == "Veilark" }
    val cleanupEvidence = if (remaining.isNotEmpty()) buildString {
      appendLine("Native cleanup remaining: " + remaining.joinToString { "index=${it.index}, luid=${it.luid}, alias=${it.alias}, description=${it.description}, operational=${it.operational}" })
      appendLine("Native cleanup PnP: " + cleanupDiagnostics())
      if (Files.isRegularFile(VeilarkPaths.logFile)) {
        Files.readAllLines(VeilarkPaths.logFile).takeLast(15).forEach { appendLine("Native cleanup journal: ${SafeLog.redact(it)}") }
      }
    } else ""
    assertTrue(remaining.isEmpty(), "Owned adapter cleanup failed\n$cleanupEvidence")
  }

  private fun cleanupDiagnostics(): String {
    val script = "Get-NetAdapter -IncludeHidden -ErrorAction SilentlyContinue | " +
      "Where-Object { \$_.Name -eq 'Veilark' } | Select-Object Name, InterfaceDescription, InterfaceIndex, InterfaceGuid, PnPDeviceID, Status | ConvertTo-Json -Compress; " +
      "Get-PnpDevice -Class Net -ErrorAction SilentlyContinue | Where-Object { \$_.InstanceId -like 'SWD\\WINTUN\\*' } | " +
      "Select-Object Status, FriendlyName, InstanceId | ConvertTo-Json -Compress; " +
      "Get-Command Remove-PnpDevice -ErrorAction SilentlyContinue | Select-Object Name, Source | ConvertTo-Json -Compress"
    return runCatching {
      ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
        .redirectErrorStream(true).start().capture(10_000, maximumOutputChars = 4_000).output
    }.getOrElse { "Diagnostic read failed: ${it.javaClass.simpleName}" }
  }

  private fun defaultRoutesOnTestAdapter(): Int {
    val script = "@(Get-NetRoute -InterfaceAlias 'Veilark' -ErrorAction SilentlyContinue | " +
      "Where-Object { \$_.DestinationPrefix -in @('0.0.0.0/0','::/0') }).Count"
    val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
      .redirectErrorStream(true).start()
    try {
      check(process.waitFor(10, TimeUnit.SECONDS)) { "Route inspection timed out" }
      check(process.exitValue() == 0) { "Route inspection failed" }
      return process.inputStream.bufferedReader().readText().trim().toInt()
    } finally {
      if (process.isAlive) process.destroyForcibly().waitFor()
    }
  }
}

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
    assertTrue(Files.isRegularFile(executable))
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
      assertEquals(EngineHealth.Healthy, health)
      assertTrue(controller.isAlive())
      assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) < 30_000)
      assertEquals(0, defaultRoutesOnTestAdapter(), "Synthetic TUN must not take over Internet routing")
    } finally {
      withTimeout(15_000) { controller.stop() }
    }
    assertFalse(controller.isAlive())
    assertFalse(Files.exists(VeilarkPaths.activeConfig))
    assertTrue(WindowsNetwork.matching { it.alias == "Veilark" }.isEmpty(), "Owned adapter cleanup failed")
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

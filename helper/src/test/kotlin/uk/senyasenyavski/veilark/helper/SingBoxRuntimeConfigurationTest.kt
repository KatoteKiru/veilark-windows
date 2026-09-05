package uk.senyasenyavski.veilark.helper

import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingBoxRuntimeConfigurationTest {
  @Test
  fun `all imported log settings become the controlled stdout startup contract`() {
    val settings = listOf(
      null,
      JSONObject().put("level", "warn"),
      JSONObject().put("level", "error"),
      JSONObject().put("disabled", true),
      JSONObject().put("output", "untrusted-output.log").put("level", "debug"),
    )
    settings.forEach { log ->
      val original = JSONObject().put("route", JSONObject().put("final", "direct"))
      if (log != null) original.put("log", log)
      val before = original.toString()
      val runtime = JSONObject(SingBoxRuntimeConfiguration.forStartup(before))

      assertEquals(before, original.toString())
      assertTrue(original.getJSONObject("route").similar(runtime.getJSONObject("route")))
      assertEquals("info", runtime.getJSONObject("log").getString("level"))
      assertFalse(runtime.getJSONObject("log").optBoolean("disabled"))
      assertFalse(runtime.getJSONObject("log").has("output"))
    }
  }

  @Test
  fun `native loopback core serves SOCKS with warn but only normalized logging emits readiness`() {
    val checker = System.getenv("SING_BOX_CHECKER")
    assumeTrue(!checker.isNullOrBlank() && File(checker).isFile)
    for (normalized in listOf(false, true)) {
      val port = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
      val config = """{"log":{"level":"warn"},"inbounds":[{"type":"mixed","listen":"127.0.0.1","listen_port":$port}],"outbounds":[{"type":"direct"}]}"""
      val input = if (normalized) SingBoxRuntimeConfiguration.forStartup(config) else config
      val process = ProcessBuilder(checker, "run", "-c", "stdin").redirectErrorStream(true).start()
      var replied = false
      try {
        process.outputStream.use { it.write(input.toByteArray()) }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < deadline && !replied) {
          replied = runCatching {
            Socket().use { socket ->
              socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 100)
              socket.soTimeout = 500
              socket.getOutputStream().write(byteArrayOf(5, 1, 0))
              socket.getInputStream().read() == 5 && socket.getInputStream().read() == 0
            }
          }.getOrDefault(false)
          if (!replied) Thread.sleep(25)
        }
        assertTrue(replied, "The owned loopback SOCKS listener must start without any TUN adapter")
        assertTrue(process.isAlive)
      } finally {
        process.destroy()
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
      }
      val output = process.inputStream.bufferedReader().readText()
      assertEquals(normalized, output.contains("sing-box started"))
      val pump = CoreLogPump(
        process = process,
        engineName = "native-test",
        readyMarkers = listOf("sing-box started"),
        fatalMarkers = listOf("FATAL"),
        diagnosticOnly = true,
        logger = {},
      )
      output.lineSequence().forEach(pump::consume)
      assertEquals(normalized, pump.ready, "Parse the actual native output, not only a synthetic marker")
    }
  }
}

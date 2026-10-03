package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.runBlocking
import uk.senyasenyavski.veilark.model.VpnStatusCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TunnelReadinessTest {
  @Test
  fun `early process exit reports numeric code without querying Windows adapters`() = runBlocking {
    val process = exitedProcess(193)
    val logPump = CoreLogPump(
      process = process,
      engineName = "readiness-test",
      readyMarkers = emptyList(),
      fatalMarkers = emptyList(),
    )

    val error = assertFailsWith<VpnStartException> {
      TunnelReadiness.await(
        process = process,
        logPump = logPump,
        matcher = TunnelMatcher("TrustTunnel") { false },
      )
    }

    assertEquals(VpnStatusCode.CORE_EXITED, error.code)
    assertTrue(error.message.orEmpty().contains("exit=0x000000C1"))
    assertEquals("TrustTunnel", error.detail)
  }

  private fun exitedProcess(code: Int) = object : Process() {
    override fun getOutputStream() = ByteArrayOutputStream()
    override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
    override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
    override fun waitFor() = code
    override fun exitValue() = code
    override fun destroy() = Unit
    override fun isAlive() = false
  }
}

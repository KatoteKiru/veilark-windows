package uk.senyasenyavski.veilark.helper

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunnelTrafficVerifierTest {
  @Test
  fun `native probe accepts a completed HTTP response`() {
    assertNull(
      WindowsCurlInternetProbe.failure(
        CapturedProcess(exitCode = 0, output = "204", timedOut = false),
      ),
    )
    assertNull(
      WindowsCurlInternetProbe.failure(
        CapturedProcess(exitCode = 0, output = "200\r\n", timedOut = false),
      ),
    )
  }

  @Test
  fun `native probe reports safe transport and certificate failures`() {
    val certificateFailure = WindowsCurlInternetProbe.failure(
      CapturedProcess(
        exitCode = 60,
        output = "curl: (60) schannel: certificate validation failed\n000",
        timedOut = false,
      ),
    )
    assertEquals("Windows не подтвердила защищённое соединение", certificateFailure)
    assertEquals(
      "тайм-аут проверки",
      WindowsCurlInternetProbe.failure(
        CapturedProcess(exitCode = null, output = "", timedOut = true),
      ),
    )
  }

  @Test
  fun `native probe never exposes raw process output`() {
    assertEquals(
      "DNS не отвечает",
      WindowsCurlInternetProbe.failure(
        CapturedProcess(exitCode = 6, output = "curl: (6) secret host details", timedOut = false),
      ),
    )
    assertEquals(
      "проверка интернета не выполнена",
      WindowsCurlInternetProbe.failure(
        CapturedProcess(exitCode = 99, output = "private raw transport exception", timedOut = false),
      ),
    )
  }

  @Test
  fun `native probe uses Windows curl without a bypassing proxy`() {
    val command = WindowsCurlInternetProbe.command(
      Path.of("C:\\Windows\\System32\\curl.exe"),
      "https://example.test/health",
    )
    assertEquals("C:\\Windows\\System32\\curl.exe", command.first())
    assertTrue(command.windowed(2).any { it == listOf("--noproxy", "*") })
    assertTrue(command.windowed(2).any { it == listOf("--max-time", "6") })
    assertEquals("https://example.test/health", command.last())
    assertFalse(command.any { it.contains("--insecure") || it == "-k" })
  }

  @Test
  fun `probe targets verify DNS and browser compatible HTTPS`() {
    assertEquals(2, WindowsCurlInternetProbe.TARGETS.size)
    assertTrue(WindowsCurlInternetProbe.TARGETS.all { it.startsWith("https://") })
    assertTrue(WindowsCurlInternetProbe.TARGETS.all { target ->
      target.removePrefix("https://").substringBefore('/').any(Char::isLetter)
    })
  }

  @Test
  fun `probe failures carry stable language-independent codes`() {
    fun code(exit: Int?, timedOut: Boolean = false) = WindowsCurlInternetProbe.classify(
      CapturedProcess(exitCode = exit, output = "000", timedOut = timedOut),
    )?.code
    assertEquals(ProbeFailure.TIMEOUT, code(null, timedOut = true))
    assertEquals(ProbeFailure.TIMEOUT, code(28))
    assertEquals(ProbeFailure.DNS, code(6))
    assertEquals(ProbeFailure.UNREACHABLE, code(7))
    assertEquals(ProbeFailure.TLS, code(60))
    assertEquals(ProbeFailure.FAILED, code(1))
  }
}

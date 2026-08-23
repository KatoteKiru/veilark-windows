package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeLogTest {
  @Test
  fun `log file is created without touching the user log`() {
    val directory = Files.createTempDirectory("veilark-safe-log-test")
    val log = directory.resolve("veilark.log")
    try {
      SafeLog.writeTo(log, "обычное безопасное сообщение")
      assertTrue(log.toFile().isFile)
    } finally {
      Files.deleteIfExists(log)
      Files.deleteIfExists(directory)
    }
  }

  @Test
  fun `credentials and complete addresses are redacted`() {
    val source =
      "vless://secret@example.com uuid=11111111-1111-1111-1111-111111111111 " +
        "server 203.0.113.42"
    val result = SafeLog.redact(source)
    assertFalse(result.contains("vless://"))
    assertFalse(result.contains("11111111-1111-1111-1111-111111111111"))
    assertFalse(result.contains("203.0.113.42"))
  }
}

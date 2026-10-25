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

  @Test
  fun `TrustTunnel deep links are redacted`() {
    val link = "tt://user-secret@example.invalid?token=secret-value"

    val result = SafeLog.redact("wizard rejected $link")

    assertFalse(result.contains(link))
    assertFalse(result.contains("secret-value"))
  }

  @Test
  fun `Fairway private keys and install identity are hidden including JSON keys`() {
    for (value in listOf(
      "client_static_private_base64=synthetic-private-key",
      "\"client_static_private_base64\":\"synthetic-private-key\"",
      "'private_key': 'synthetic-private-key'",
      "X-Veilark-Install-Id: synthetic-private-key",
    )) assertFalse(SafeLog.redact(value).contains("synthetic-private-key"))
  }

  @Test
  fun `opaque enrollment and import bearers are hidden`() {
    for (value in listOf(
      "veilark://enroll/v2?t=synthetic-token#k=synthetic-key",
      "VEILARK://import?t=synthetic-token",
    )) {
      val result = SafeLog.redact("Import rejected: $value")
      assertFalse(result.contains("synthetic-token"))
      assertFalse(result.contains("synthetic-key"))
    }
  }
}

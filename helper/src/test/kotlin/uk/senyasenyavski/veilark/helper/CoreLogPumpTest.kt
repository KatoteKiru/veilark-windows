package uk.senyasenyavski.veilark.helper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreLogPumpTest {
  @Test
  fun `line length boundary accepts complete record but discards oversized record entirely`() {
    val prefix = "INFO sing-box started ("
    val record = prefix + "x".repeat(8_192 - prefix.length - 1) + ")"
    val closed = CountDownLatch(1)
    val written = mutableListOf<String>()
    val pump = CoreLogPump(
      process = outputProcess(record + "\r\n" + record + "x\r\n", closed), engineName = "boundary-test",
      readyMarkers = listOf("sing-box started"), fatalMarkers = listOf("FATAL"),
      diagnosticOnly = true, logger = written::add,
    )
    pump.start()
    assertTrue(closed.await(2, TimeUnit.SECONDS))
    assertTrue(pump.ready)
    assertEquals(1, written.size)
    assertEquals(SafeLog.redact(record), written.single())
  }

  @Test
  fun `oversized unterminated native record cannot spoof readiness or fatal state`() {
    listOf(true, false).forEach { diagnosticOnly ->
      val closed = CountDownLatch(1)
      val written = mutableListOf<String>()
      val record = "INFO sing-box started (" + "x".repeat(200_000) + ")"
      val pump = CoreLogPump(
        process = outputProcess(record, closed), engineName = "oversized-test",
        readyMarkers = listOf("sing-box started"), fatalMarkers = listOf("FATAL"),
        diagnosticOnly = diagnosticOnly, logger = written::add,
      )
      pump.start()
      assertTrue(closed.await(2, TimeUnit.SECONDS))
      assertFalse(pump.ready)
      assertEquals(null, pump.fatal)
      assertTrue(pump.tail().isEmpty())
      assertTrue(written.isEmpty())
    }
  }

  @Test
  fun `oversized lines are drained and complete CRLF CR and EOF records still work`() {
    val closed = CountDownLatch(1)
    val written = mutableListOf<String>()
    val records = "FATAL password=discarded " + "x".repeat(200_000) +
      "\r\nINFO sing-box started (0.10s)\rWARN password=secret-value\r\nWARN last diagnostic"
    val pump = CoreLogPump(
      process = outputProcess(records, closed), engineName = "framing-test",
      readyMarkers = listOf("sing-box started"), fatalMarkers = listOf("FATAL"),
      diagnosticOnly = true, logger = written::add,
    )
    pump.start()
    assertTrue(closed.await(2, TimeUnit.SECONDS))
    assertTrue(pump.ready)
    assertEquals(null, pump.fatal)
    assertEquals(3, written.size)
    assertEquals(3, pump.tail().size)
    assertFalse(written.any { it.contains("secret-value") || it.contains("discarded") })
    assertEquals("WARN last diagnostic", written.last())
  }

  @Test
  fun `startup marker survives journal failure and later lines are still consumed`() {
    var attempts = 0
    val pump = pump { attempts++; throw IOException("disk full") }

    pump.consume("INFO[0000] sing-box started (0.10s)")
    pump.consume("WARN[0001] diagnostic message")

    assertTrue(pump.ready)
    assertEquals(2, attempts)
    assertEquals(2, pump.tail().size)
  }

  @Test
  fun `traffic info does not enter journal or retained tail`() {
    val written = mutableListOf<String>()
    val pump = pump(written::add)

    repeat(1_000) { pump.consume("INFO[0001] inbound connection to private.example:443") }
    pump.consume("DEBUG[0001] private.example connection detail")

    assertTrue(written.isEmpty())
    assertTrue(pump.tail().isEmpty())
    assertFalse(pump.ready)
  }

  @Test
  fun `traffic names cannot impersonate fatal or readiness markers`() {
    val written = mutableListOf<String>()
    val pump = pump(written::add)
    pump.consume("INFO[0001] outbound connection to FATAL.example:443")
    pump.consume("INFO[0001] outbound/socks[sing-box started (0.01s)]: connection to example.org")
    pump.consume("INFO[0001] inbound connection with tag level=fatal")
    pump.consume("INFO[0001] inbound connection to ERROR.example:443")
    assertFalse(pump.ready)
    assertEquals(null, pump.fatal)
    assertTrue(pump.tail().isEmpty())
    assertTrue(written.isEmpty())

    pump.consume("+0300 2026-09-05 18:00:00 INFO sing-box started (0.004s)")
    assertTrue(pump.ready)
    pump.consume("+0300 2026-09-05 18:00:01 FATAL start service: failure")
    assertTrue(pump.fatal.orEmpty().contains("start service"))
  }

  @Test
  fun `fatal and retained diagnostic messages are redacted before exposure`() {
    val written = mutableListOf<String>()
    val pump = pump(written::add)

    pump.consume("\u001B[31mFATAL\u001B[0m[0000] password=secret-value connect 203.0.113.1")

    assertEquals(1, written.size)
    assertFalse(pump.fatal.orEmpty().contains("secret-value"))
    assertFalse(pump.tail().single().contains("203.0.113.1"))
    assertFalse(written.single().contains("\u001B"))
  }

  @Test
  fun `TrustTunnel keeps its readiness marker and bounded diagnostic tail`() {
    val written = mutableListOf<String>()
    val pump = CoreLogPump(
      process = emptyProcess(),
      engineName = "trust-test",
      readyMarkers = listOf("Successfully connected to endpoint"),
      fatalMarkers = listOf("FATAL"),
      logger = written::add,
    )
    pump.consume("INFO TRUSTTUNNEL_CLIENT_APP Successfully connected to endpoint")
    repeat(50) { pump.consume("INFO diagnostic $it") }

    assertTrue(pump.ready)
    assertEquals(51, written.size)
    assertEquals(40, pump.tail().size)
    assertEquals("INFO diagnostic 49", pump.tail().last())
  }

  private fun pump(logger: (String) -> Unit) = CoreLogPump(
    process = emptyProcess(),
    engineName = "test",
    readyMarkers = listOf("sing-box started"),
    fatalMarkers = listOf("FATAL", "level=fatal"),
    diagnosticOnly = true,
    logger = logger,
  )

  private fun emptyProcess() = object : Process() {
    override fun getOutputStream() = ByteArrayOutputStream()
    override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
    override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
    override fun waitFor() = 0
    override fun exitValue() = 0
    override fun destroy() = Unit
  }

  private fun outputProcess(text: String, closed: CountDownLatch) = object : Process() {
    private val output = object : ByteArrayInputStream(text.toByteArray()) {
      override fun close() { super.close(); closed.countDown() }
    }
    override fun getOutputStream() = ByteArrayOutputStream()
    override fun getInputStream() = output
    override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
    override fun waitFor() = 0
    override fun exitValue() = 0
    override fun destroy() = Unit
  }
}

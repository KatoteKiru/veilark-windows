package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.model.ImportResult
import kotlin.test.assertIs

class NodeLatencyProbeTest {
  @Test
  fun `probes a sing-box outbound without starting VPN`() = runBlocking {
    loopbackServer().use { server ->
      val profile = Profile(
        id = "test",
        name = "test",
        engine = VpnEngine.SingBox,
        config = JSONObject().put(
          "outbounds",
          JSONArray().put(
            JSONObject()
              .put("tag", "node")
              .put("server", "127.0.0.1")
              .put("server_port", server.localPort),
          ),
        ).toString(),
        nodes = listOf(Node("node", "Local", "VLESS")),
        sourceLabel = "test",
      )

      val result = NodeLatencyProbe(timeoutMillis = 1_000).probe(profile)["node"]

      assertTrue(requireNotNull(result).available)
      assertNotNull(result.millis)
    }
  }

  @Test
  fun `probes a TrustTunnel TOML endpoint`() = runBlocking {
    loopbackServer().use { server ->
      val config = """
        [endpoint]
        addresses = ["127.0.0.1:${server.localPort}"]
      """.trimIndent()
      val profile = Profile(
        id = "test",
        name = "test",
        engine = VpnEngine.TrustTunnel,
        config = config,
        nodes = listOf(Node("node", "Local", "TrustTunnel")),
        sourceLabel = "test",
        endpointConfigs = mapOf("node" to config),
      )

      assertTrue(requireNotNull(NodeLatencyProbe(1_000).probe(profile)["node"]).available)
    }
  }

  @Test
  fun `DNS and TCP share one hard timeout without opening a real socket`() = runBlocking {
    val connectorCalls = AtomicInteger()
    val connectorTimeoutMillis = AtomicInteger()
    val nowNanos = AtomicLong()
    val profile = Profile(
      id = "timeout",
      name = "timeout",
      engine = VpnEngine.SingBox,
      config = JSONObject().put(
        "outbounds",
        JSONArray().put(
          JSONObject()
            .put("tag", "node")
            .put("server", "delayed.invalid")
            .put("server_port", 443),
        ),
      ).toString(),
      nodes = listOf(Node("node", "Delayed", "VLESS")),
      sourceLabel = "test",
    )
    val probe = NodeLatencyProbe(
      timeoutMillis = 500,
      concurrency = 1,
      resolver = {
        nowNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(100))
        arrayOf(InetAddress.getLoopbackAddress())
      },
      connector = { _, _, remainingTimeoutMillis ->
        connectorCalls.incrementAndGet()
        connectorTimeoutMillis.set(remainingTimeoutMillis)
        throw TimeoutException("simulated TCP timeout")
      },
      nanoTime = nowNanos::get,
    )
    val result = requireNotNull(probe.probe(profile)["node"])

    assertFalse(result.available)
    assertEquals(1, connectorCalls.get())
    assertEquals(400, connectorTimeoutMillis.get())
  }

  @Test
  fun `live subscription nodes respond when enabled`() = runBlocking {
    val url = System.getenv("VEILARK_TEST_SUBSCRIPTION")?.takeIf(String::isNotBlank)
      ?: return@runBlocking
    val imported = assertIs<ImportResult.Success>(ProfileImporter().fromHttps(url))
    imported.profiles.forEach { profile ->
      val result = NodeLatencyProbe().probe(profile)
      assertTrue(
        result.values.any(NodeLatency::available),
        "No ${profile.engine} endpoint accepted a TCP connection",
      )
    }
  }

  private fun loopbackServer(): ServerSocket = ServerSocket().apply {
    bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
  }
}

package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class NodeLatency(
  val millis: Int?,
  val available: Boolean,
)

class NodeLatencyProbe(
  private val timeoutMillis: Int = 4_000,
  private val concurrency: Int = 6,
) {
  suspend fun probe(profile: Profile): Map<String, NodeLatency> = coroutineScope {
    if (profile.nodes.isEmpty()) return@coroutineScope emptyMap()

    // Resolve sing-box endpoints once. Previously every node parsed the same
    // JSON document in its own coroutine, which became quadratic work for a
    // large subscription.
    val singBoxTargets = if (profile.engine == VpnEngine.SingBox) {
      runCatching { singBoxTargets(profile.config) }.getOrDefault(emptyMap())
    } else {
      emptyMap()
    }
    val work = Channel<Node>(capacity = concurrency.coerceAtLeast(1))
    val results = ConcurrentHashMap<String, NodeLatency>()
    val workers = List(concurrency.coerceIn(1, profile.nodes.size)) {
      launch(Dispatchers.IO) {
        for (node in work) {
          results[node.tag] = probeNode(profile, node, singBoxTargets)
        }
      }
    }

    try {
      profile.nodes.forEach { work.send(it) }
    } finally {
      work.close()
    }
    workers.joinAll()

    profile.nodes.associate { node ->
      node.tag to (results[node.tag] ?: NodeLatency(millis = null, available = false))
    }
  }

  private suspend fun probeNode(
    profile: Profile,
    node: Node,
    singBoxTargets: Map<String, Pair<String, Int>>,
  ): NodeLatency {
    return try {
      val target = when (profile.engine) {
        VpnEngine.SingBox -> singBoxTargets[node.tag]
        VpnEngine.TrustTunnel -> runInterruptible(Dispatchers.IO) {
          trustTunnelTarget(profile.endpointConfigs[node.tag] ?: profile.config)
        }
      } ?: return NodeLatency(millis = null, available = false)
      val started = System.nanoTime()
      runInterruptible(Dispatchers.IO) {
        Socket().use { socket ->
          socket.tcpNoDelay = true
          socket.connect(InetSocketAddress(target.first, target.second), timeoutMillis)
        }
      }
      NodeLatency(
        millis = ((System.nanoTime() - started) / 1_000_000L)
          .coerceAtLeast(1)
          .coerceAtMost(Int.MAX_VALUE.toLong())
          .toInt(),
        available = true,
      )
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      NodeLatency(millis = null, available = false)
    }
  }

  private fun singBoxTargets(config: String): Map<String, Pair<String, Int>> {
    val outbounds = JSONObject(config).getJSONArray("outbounds")
    val targets = linkedMapOf<String, Pair<String, Int>>()
    repeat(outbounds.length()) { index ->
      val outbound = outbounds.optJSONObject(index) ?: return@repeat
      val tag = outbound.optString("tag").trim()
      val server = outbound.optString("server").trim()
      val port = outbound.optInt("server_port")
      if (tag.isNotBlank() && server.isNotBlank() && port in 1..65535) {
        targets.putIfAbsent(tag, server to port)
      }
    }
    return targets
  }

  private fun trustTunnelTarget(source: String): Pair<String, Int>? {
    val config = if (source.trimStart().startsWith("tt://", ignoreCase = true)) {
      compileTrustTunnel(source)
    } else {
      source
    }
    val address = Regex(
      """(?m)^\s*addresses\s*=\s*\[\s*"([^"]+)"""",
    ).find(config)?.groupValues?.get(1)
      ?: Regex("""(?m)^\s*address\s*=\s*"([^"]+)"""")
        .find(config)?.groupValues?.get(1)
      ?: return null
    return splitHostPort(address)
  }

  private fun compileTrustTunnel(link: String): String {
    val wizard = resolveWizard()
    val settings = Files.createTempFile(VeilarkPaths.runtimeDirectory, "latency-", ".toml")
    return try {
      val process = ProcessBuilder(
        wizard.toString(),
        "--mode",
        "non-interactive",
        "--deeplink",
        link.trim(),
        "--settings",
        settings.toString(),
      )
        .directory(wizard.parent.toFile())
        .redirectErrorStream(true)
        .start()
        .capture(20_000)
      check(process.succeeded) {
        if (process.timedOut) {
          "Тайм-аут setup wizard"
        } else {
          process.output.lineSequence().lastOrNull().orEmpty().take(160)
        }
      }
      Files.readString(settings, Charsets.UTF_8)
    } finally {
      Files.deleteIfExists(settings)
    }
  }

  private fun resolveWizard(): Path {
    val candidates = buildList {
      System.getenv("VEILARK_TRUSTTUNNEL_WIZARD")
        ?.takeIf(String::isNotBlank)
        ?.let { add(Path.of(it)) }
      System.getProperty("compose.application.resources.dir")
        ?.takeIf(String::isNotBlank)
        ?.let { add(Path.of(it, "setup_wizard.exe")) }
      add(
        Path.of("packaging", "resources", "windows", "setup_wizard.exe")
          .toAbsolutePath(),
      )
    }
    return candidates.firstOrNull(Files::isRegularFile)
      ?: error("Не найден setup_wizard.exe")
  }

  private fun splitHostPort(value: String): Pair<String, Int>? {
    val trimmed = value.trim()
    val host: String
    val portValue: String
    if (trimmed.startsWith("[")) {
      val closing = trimmed.indexOf(']')
      if (closing < 2 || closing + 2 > trimmed.length || trimmed[closing + 1] != ':') {
        return null
      }
      host = trimmed.substring(1, closing)
      portValue = trimmed.substring(closing + 2)
    } else {
      val separator = trimmed.lastIndexOf(':')
      if (separator <= 0) return null
      host = trimmed.substring(0, separator)
      portValue = trimmed.substring(separator + 1)
    }
    val port = portValue.toIntOrNull() ?: return null
    return (host to port).takeIf { host.isNotBlank() && port in 1..65535 }
  }
}

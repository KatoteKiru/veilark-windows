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
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Callable
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

data class NodeLatency(
  val millis: Int?,
  val available: Boolean,
)

class NodeLatencyProbe internal constructor(
  private val timeoutMillis: Int = 4_000,
  private val concurrency: Int = 6,
  private val resolver: (String) -> Array<InetAddress>,
  private val connector: (InetAddress, Int, Int) -> Unit,
) {
  constructor(
    timeoutMillis: Int = 4_000,
    concurrency: Int = 6,
  ) : this(
    timeoutMillis = timeoutMillis,
    concurrency = concurrency,
    resolver = InetAddress::getAllByName,
    connector = { address, port, connectTimeoutMillis ->
      Socket().use { socket ->
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
      }
    },
  )

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
        VpnEngine.TrustTunnel ->
          trustTunnelTarget(profile.endpointConfigs[node.tag] ?: profile.config)
      } ?: return NodeLatency(millis = null, available = false)
      val started = System.nanoTime()
      probeTcp(target.first, target.second)
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

  /** Bounds DNS resolution and TCP connect by one wall-clock deadline. */
  private suspend fun probeTcp(host: String, port: Int) {
    require(timeoutMillis > 0) { "Тайм-аут проверки должен быть положительным" }
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
    val future = PROBE_EXECUTOR.submit(
      Callable {
        val address = resolver(host).firstOrNull()
          ?: error("DNS не вернул адрес")
        connector(address, port, remainingMillis(deadlineNanos))
      },
    )
    try {
      runInterruptible(Dispatchers.IO) {
        future.get(remainingMillis(deadlineNanos).toLong(), TimeUnit.MILLISECONDS)
      }
    } catch (timeout: TimeoutException) {
      future.cancel(true)
      throw timeout
    } finally {
      if (!future.isDone) future.cancel(true)
    }
  }

  private fun remainingMillis(deadlineNanos: Long): Int {
    val remainingNanos = deadlineNanos - System.nanoTime()
    if (remainingNanos <= 0L) throw TimeoutException("Истёк тайм-аут проверки узла")
    return ((remainingNanos + NANOS_PER_MILLISECOND - 1L) / NANOS_PER_MILLISECOND)
      .coerceAtMost(Int.MAX_VALUE.toLong())
      .toInt()
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

  private suspend fun trustTunnelTarget(source: String): Pair<String, Int>? {
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

  private suspend fun compileTrustTunnel(link: String): String {
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
        .captureCancellable(20_000)
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

  private companion object {
    const val NANOS_PER_MILLISECOND = 1_000_000L
    const val MAX_PROBE_THREADS = 32
    val PROBE_THREAD_SEQUENCE = AtomicInteger()
    val PROBE_EXECUTOR = ThreadPoolExecutor(
      0,
      MAX_PROBE_THREADS,
      30L,
      TimeUnit.SECONDS,
      SynchronousQueue(),
      ThreadFactory { task ->
        Thread(task, "veilark-node-probe-${PROBE_THREAD_SEQUENCE.incrementAndGet()}").apply {
          isDaemon = true
        }
      },
      ThreadPoolExecutor.AbortPolicy(),
    )
  }
}

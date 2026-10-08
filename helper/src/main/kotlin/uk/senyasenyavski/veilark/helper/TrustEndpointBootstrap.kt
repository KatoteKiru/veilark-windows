package uk.senyasenyavski.veilark.helper

import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal data class TrustDnsAddresses(val addresses: List<String>, val ttlMillis: Long = 30_000)

/** Resolves only public endpoint addresses; native TLS still authenticates the original hostname. */
internal class TrustEndpointBootstrap(
  private val resolve: (String, Long) -> TrustDnsAddresses = TrustBootstrapDns::resolve,
  private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
  private data class Cached(val answer: TrustDnsAddresses, val expires: Long)
  private val cache = LinkedHashMap<String, Cached>()

  @Synchronized
  fun prepare(source: String): String {
    // Unknown TOML representations are left to the official native parser.
    val section = ENDPOINT.find(source) ?: return source
    val end = NEXT_TABLE.find(source, section.range.last + 1)?.range?.first ?: source.length
    val body = source.substring(section.range.last + 1, end)
    val assignment = ADDRESSES.find(body) ?: return source
    val open = assignment.range.last
    val close = arrayEnd(body, open) ?: return source
    val addresses = parseArray(body.substring(open + 1, close)) ?: return source
    if (addresses.isEmpty() || addresses.size > 8) return source
    val allowIpv6 = !Regex("""(?m)^\s*has_ipv6\s*=\s*false\s*(?:#.*)?$""").containsMatchIn(body)
    val deadline = now() + 3_000
    val transformed = addresses.flatMap { original ->
      val endpoint = parseEndpoint(original)
      if (endpoint == null || isNumericIp(endpoint.first)) return@flatMap listOf(original)
      val (host, port) = endpoint
      val existing = cache[host]?.takeIf { it.expires > now() }
      val answer = existing?.answer ?: run {
        cache.remove(host)
        val budget = deadline - now()
        if (budget <= 0) return@flatMap listOf(original)
        val resolved = runCatching { resolve(host, budget) }.getOrNull()
          ?: return@flatMap listOf(original)
        val validated = resolved.copy(addresses = resolved.addresses.filter(::isNumericIp).distinct().take(8))
        if (validated.addresses.isEmpty()) return@flatMap listOf(original)
        val ttl = validated.ttlMillis.coerceIn(0, 300_000)
        if (ttl > 0) {
          cache[host] = Cached(validated, now() + ttl)
          while (cache.size > 128) cache.remove(cache.keys.first())
        }
        validated
      }
      val selected = answer.addresses.filter { allowIpv6 || ':' !in it }
      if (selected.isEmpty()) listOf(original)
      else selected.map { ip -> if (':' in ip) "[$ip]:$port" else "$ip:$port" }
    }.distinct()
    if (transformed == addresses) return source
    val replacement = transformed.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    val start = section.range.last + 1 + open
    return source.replaceRange(start, section.range.last + 1 + close + 1, replacement)
  }

  private fun arrayEnd(text: String, open: Int): Int? {
    var quote: Char? = null
    var comment = false
    for (i in open + 1 until text.length) {
      val c = text[i]
      if (comment) { if (c == '\n') comment = false; continue }
      if (quote != null) {
        if (c == '\\') return null // Escaped/complex TOML is kept unchanged.
        if (c == quote) quote = null
      } else when (c) {
        '"', '\'' -> quote = c
        '#' -> comment = true
        ']' -> return i
        '[' -> return null
      }
    }
    return null
  }

  private fun parseArray(text: String): List<String>? {
    var remainder = text
    val result = mutableListOf<String>()
    while (true) {
      remainder = remainder.replace(Regex("""^\s*(?:#[^\n]*(?:\n|$)\s*)*"""), "")
      if (remainder.isEmpty()) return result
      val match = ARRAY_STRING.find(remainder) ?: return null
      result += match.groupValues[1].ifEmpty { match.groupValues[2] }
      remainder = remainder.substring(match.range.last + 1)
        .replace(Regex("""^\s*(?:#[^\n]*(?:\n|$)\s*)*"""), "")
      if (remainder.isEmpty()) return result
      if (!remainder.startsWith(',')) return null
      remainder = remainder.substring(1)
    }
  }

  private fun parseEndpoint(value: String): Pair<String, Int>? = runCatching {
    if (value.startsWith('|')) return null // Relay syntax remains native-owned.
    val uri = URI("tcp://$value")
    val host = uri.host?.removeSurrounding("[", "]")?.lowercase() ?: return null
    if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
      !uri.rawPath.isNullOrEmpty() || uri.port !in 1..65535) return null
    if (!isNumericIp(host) && (!host.contains('.') || host.length > 253 ||
      host.split('.').any { !DNS_LABEL.matches(it) })) return null
    host to uri.port
  }.getOrNull()

  companion object {
    private val ENDPOINT = Regex("""(?m)^\s*\[endpoint]\s*(?:#[^\n]*)?$""")
    private val NEXT_TABLE = Regex("""(?m)^\s*\[.*]\s*(?:#[^\n]*)?$""")
    private val ADDRESSES = Regex("""(?m)^\s*addresses\s*=\s*\[""")
    private val ARRAY_STRING = Regex("""^(?:"([^"\\\r\n]*)"|'([^'\r\n]*)')""")
    private val DNS_LABEL = Regex("""[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?""")

    internal fun isNumericIp(value: String): Boolean {
      if (':' in value) return value.matches(Regex("[0-9a-fA-F:]+")) &&
        runCatching { InetAddress.getByName(value).address.size == 16 }.getOrDefault(false)
      val octets = value.split('.')
      return octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) &&
        it.toIntOrNull() in 0..255 }
    }
  }
}

internal object TrustBootstrapDns {
  // No queue: two stuck OS lookups cannot create unbounded tasks or threads.
  private val systemPool = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
    SynchronousQueue(), { task -> Thread(task, "trust-endpoint-dns").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }

  fun resolve(host: String, budgetMillis: Long): TrustDnsAddresses {
    val started = System.nanoTime()
    val task = runCatching { systemPool.submit<List<String>> {
      InetAddress.getAllByName(host).map { it.hostAddress }.filter(TrustEndpointBootstrap::isNumericIp)
    } }.getOrNull()
    val system = try { task?.get(minOf(1_000, budgetMillis), TimeUnit.MILLISECONDS).orEmpty() }
      catch (_: Exception) { emptyList() } finally { task?.cancel(true) }
    if (system.isNotEmpty()) return TrustDnsAddresses(system)
    val remaining = budgetMillis - (System.nanoTime() - started) / 1_000_000
    if (remaining <= 0 || !isDohEligible(host)) return TrustDnsAddresses(emptyList())
    val curl = Path.of(System.getenv("SystemRoot") ?: "C:\\Windows", "System32", "curl.exe")
    if (!Files.isRegularFile(curl)) return TrustDnsAddresses(emptyList())
    // A private helper output file avoids the generic process-capture reader's
    // additional two-second join allowance. It contains only the DNS response.
    return runCatching {
      val response = Files.createTempFile("veilark-trust-dns-", ".json")
      var process: Process? = null
      try {
        val limit = remaining.coerceAtMost(2_000)
        process = ProcessBuilder(command(curl, host, limit) +
          listOf("--output", response.toString(), "--max-filesize", "16384"))
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val waitBudget = budgetMillis - (System.nanoTime() - started) / 1_000_000
        if (waitBudget <= 0 || !process.waitFor(minOf(limit, waitBudget), TimeUnit.MILLISECONDS) ||
          process.exitValue() != 0 || Files.size(response) > 16_384) TrustDnsAddresses(emptyList())
        else parseDoh(Files.readString(response), host)
      } finally {
        if (process?.isAlive == true) process.destroyForcibly()
        runCatching { Files.deleteIfExists(response) }
      }
    }.getOrDefault(TrustDnsAddresses(emptyList()))
  }

  internal fun command(curl: Path, host: String, timeoutMillis: Long): List<String> = listOf(
    curl.toString(), "--noproxy", "*", "--silent", "--show-error", "--fail",
    "--resolve", "cloudflare-dns.com:443:1.1.1.1", "--connect-timeout", "1",
    "--max-time", (timeoutMillis / 1000.0).toString(), "--header", "Accept: application/dns-json",
    "https://cloudflare-dns.com/dns-query?name=$host&type=A",
  )

  internal fun isDohEligible(host: String): Boolean =
    host.length <= 253 && host.contains('.') &&
      host.split('.').all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) } &&
      listOf("localhost", "local", "internal", "test", "invalid", "example", "home.arpa")
        .none { host == it || host.endsWith(".$it") }

  internal fun parseDoh(text: String, host: String): TrustDnsAddresses = runCatching {
    val root = JSONObject(text)
    if (root.optInt("Status", -1) != 0 || root.optBoolean("TC", false)) return TrustDnsAddresses(emptyList())
    val question = root.optJSONArray("Question")?.optJSONObject(0)
    if (question?.optString("name")?.trimEnd('.')?.lowercase() != host || question.optInt("type") != 1)
      return TrustDnsAddresses(emptyList())
    val answers = root.optJSONArray("Answer") ?: return TrustDnsAddresses(emptyList())
    val all = (0 until minOf(answers.length(), 64)).mapNotNull { answers.optJSONObject(it) }
    val owners = linkedSetOf(host)
    repeat(8) {
      all.filter { it.optInt("type") == 5 && it.optString("name").trimEnd('.').lowercase() in owners }
        .forEach { owners += it.optString("data").trimEnd('.').lowercase() }
    }
    val records = all.filter { it.optString("name").trimEnd('.').lowercase() in owners &&
      it.optInt("type") == 1 && TrustEndpointBootstrap.isNumericIp(it.optString("data")) &&
      ':' !in it.optString("data") }
    val ttlRecords = all.filter { it.optString("name").trimEnd('.').lowercase() in owners &&
      it.optInt("type") in listOf(1, 5) }
    TrustDnsAddresses(records.map { it.getString("data") }.distinct().take(8),
      (ttlRecords.minOfOrNull { it.optLong("TTL", 0) } ?: 0).coerceIn(0, 300) * 1000)
  }.getOrDefault(TrustDnsAddresses(emptyList()))
}

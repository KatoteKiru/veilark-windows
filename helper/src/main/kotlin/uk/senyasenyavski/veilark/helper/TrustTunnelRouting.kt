package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ProfileSelection
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import java.nio.file.Files
import java.nio.file.Path

internal data class TrustTunnelRoutingPlan(
  val vpnMode: String,
  val exclusions: List<String>,
  val exclusionsTcpEarlyAckEnabled: Boolean = false,
  val exclusionsPreresolveEnabled: Boolean = false,
  val exclusionsPreresolveMaxQueries: Int = 50,
)

/** Converts Veilark routing semantics to TrustTunnel 1.1.5 TOML settings. */
internal object TrustTunnelRouting {
  fun plan(profile: Profile): TrustTunnelRoutingPlan = when (profile.appliedRouting.mode) {
    RoutingMode.All -> TrustTunnelRoutingPlan("general", emptyList())
    RoutingMode.RussiaDirect -> TrustTunnelRoutingPlan(
      vpnMode = "general",
      exclusions = requiredGeoExclusions(profile),
      exclusionsTcpEarlyAckEnabled = false,
      exclusionsPreresolveEnabled = true,
    )
    RoutingMode.RussiaVpn -> TrustTunnelRoutingPlan(
      vpnMode = "selective",
      exclusions = requiredGeoExclusions(profile),
      exclusionsTcpEarlyAckEnabled = false,
      exclusionsPreresolveEnabled = true,
    )
    RoutingMode.Manual -> {
      val direct = ProfileSelection.routingEntries(profile.appliedRouting.directEntries)
      val vpn = ProfileSelection.routingEntries(profile.appliedRouting.vpnEntries)
      require(
        direct.domains.isNotEmpty() || direct.networks.isNotEmpty() ||
          vpn.domains.isNotEmpty() || vpn.networks.isNotEmpty(),
      ) {
        "Добавьте хотя бы один домен или IP-диапазон"
      }
      // Manual routing has VPN as its default, just like the sing-box config.
      // Explicit VPN entries therefore only provide priority over an otherwise
      // direct entry; non-overlapping direct entries are TrustTunnel exclusions.
      val retainedDomains = direct.domains.filterNot { candidate ->
        vpn.domains.any { candidate == it || candidate.endsWith(".$it") }
      }
      require(retainedDomains.none { candidate -> vpn.domains.any { it.endsWith(".$candidate") } }) {
        "TrustTunnel: VPN-поддомен пересекается с прямым правилом. Уточните прямые правила или используйте sing-box. / Overlapping domain rules require sing-box."
      }
      val vpnNetworks = vpn.networks.map(::network)
      val retainedNetworks = direct.networks.filterNot { candidate ->
        val directNetwork = network(candidate)
        vpnNetworks.any { it.contains(directNetwork) }
      }
      require(retainedNetworks.none { candidate ->
        val directNetwork = network(candidate)
        vpnNetworks.any { directNetwork.contains(it) }
      }) {
        "TrustTunnel: VPN-сеть находится внутри прямой сети. Разделите диапазоны или используйте sing-box. / Overlapping network rules require sing-box."
      }
      TrustTunnelRoutingPlan(
        vpnMode = "general",
        exclusions = (
          retainedNetworks + retainedDomains.flatMap(::domainWithSubdomains)
        ).distinct(),
        // Let known exclusion candidates use TrustTunnel's SNI inspection,
        // but do not send every foreign HTTPS connection through the fake
        // upstream. The latter delays and can stall the default VPN branch.
        exclusionsTcpEarlyAckEnabled = false,
        exclusionsPreresolveEnabled = retainedDomains.isNotEmpty(),
      )
    }
  }

  private data class Network(val bytes: ByteArray, val prefix: Int) {
    fun contains(other: Network): Boolean {
      if (bytes.size != other.bytes.size || prefix > other.prefix) return false
      repeat(prefix) { bit ->
        val mask = 1 shl (7 - bit % 8)
        if ((bytes[bit / 8].toInt() and mask) != (other.bytes[bit / 8].toInt() and mask)) return false
      }
      return true
    }
  }

  private fun network(value: String): Network {
    val literal = value.substringBefore('/')
    // Routing parser has already validated numeric input; never resolve a hostname here.
    require(literal.matches(Regex("[0-9a-fA-F:.]+")))
    val bytes = java.net.InetAddress.getByName(literal).address
    val prefix = value.substringAfter('/').toInt()
    require(prefix in 0..bytes.size * 8)
    return Network(bytes, prefix)
  }

  fun apply(path: Path, plan: TrustTunnelRoutingPlan) {
    val source = Files.readString(path, Charsets.UTF_8)
    Files.writeString(path, apply(source, plan), Charsets.UTF_8)
  }

  internal fun apply(source: String, plan: TrustTunnelRoutingPlan): String {
    require(plan.vpnMode == "general" || plan.vpnMode == "selective")
    validateNativeConfigContract(source)
    val lines = source.replace("\r\n", "\n").split('\n')
    val firstSection = lines.indexOfFirst { TABLE_HEADER.matches(it) }
    check(firstSection >= 0)
    val topLevel = removeAssignments(lines.take(firstSection), SETTINGS)
      .dropLastWhile(String::isBlank)
    val routingBlock = buildList {
      add("vpn_mode = \"${plan.vpnMode}\"")
      add("exclusions_tcp_early_ack_enabled = ${plan.exclusionsTcpEarlyAckEnabled}")
      // Keep pre-resolution bounded. It populates the address suspect cache for
      // QUIC/secure-DNS clients, while the 50-query cap prevents thousands of
      // RU entries from becoming an unbounded background DNS burst.
      add("exclusions_preresolve_enabled = ${plan.exclusionsPreresolveEnabled}")
      add("exclusions_preresolve_max_queries = ${plan.exclusionsPreresolveMaxQueries}")
      add("exclusions = [")
      plan.exclusions.forEach { add("  \"${tomlString(it)}\",") }
      add("]")
      add("")
    }
    val routed = (topLevel + routingBlock + lines.drop(firstSection)).joinToString("\n")
      .trimEnd() + "\n"
    // Preserve the endpoint's MTU. TrustTunnel 1.1.5 defaults to 1350 and
    // imported subscriptions may deliberately choose another tested value.
    val result = ensureTableAssignment(routed, "[listener.tun]", "change_system_dns", "true")
    validateNativeConfigContract(result)
    return result
  }

  /**
   * TrustTunnel 1.1.5 has no check-only CLI command. Keep this mandatory
   * contract before any adapter cleanup, while the official client remains
   * the final native TOML parser when it is started below.
   */
  internal fun validateNativeConfigContract(source: String) {
    val normalized = source.replace("\r\n", "\n")
    require(normalized.lineSequence().count { it.trim() == "[endpoint]" } == 1) {
      "TrustTunnel не создал единственную секцию endpoint"
    }
    require(normalized.lineSequence().count { it.trim() == "[listener.tun]" } == 1) {
      "TrustTunnel не создал единственную TUN-конфигурацию"
    }
    require(normalized.lineSequence().none { it.trimStart().startsWith("tt://") }) {
      "TrustTunnel deeplink не был преобразован setup wizard"
    }
    require(normalized.lineSequence().any { line ->
      line.trimStart().startsWith("endpoint =") ||
        line.trim().startsWith("hostname =") ||
        line.trim().startsWith("addresses =")
    }) {
      "TrustTunnel endpoint не содержит адрес"
    }
  }

  private fun requiredGeoExclusions(profile: Profile): List<String> =
    profile.geoRoutingAssets?.trustTunnelExclusions?.takeIf(List<String>::isNotEmpty)
      ?: throw GeoRoutingUnavailableException(
        "Геоданные маршрутизации не готовы. Обновите их и повторите подключение.",
      )

  private fun domainWithSubdomains(domain: String): List<String> =
    listOf(domain, "*.$domain")

  private fun removeAssignments(lines: List<String>, keys: Set<String>): List<String> {
    val result = mutableListOf<String>()
    var skippingArray = false
    var bracketDepth = 0
    lines.forEach { line ->
      if (skippingArray) {
        bracketDepth += bracketDelta(line)
        if (bracketDepth <= 0) skippingArray = false
        return@forEach
      }
      val assignment = ASSIGNMENT.matchEntire(line)
      if (assignment == null || assignment.groupValues[1] !in keys) {
        result += line
        return@forEach
      }
      bracketDepth = bracketDelta(assignment.groupValues[2])
      skippingArray = bracketDepth > 0
    }
    return result
  }

  private fun ensureTableAssignment(
    source: String,
    table: String,
    key: String,
    value: String,
  ): String {
    val lines = source.trimEnd().split('\n').toMutableList()
    val tableIndex = lines.indexOfFirst { it.trim() == table }
    require(tableIndex >= 0) { "TrustTunnel не создал секцию $table" }
    val nextTable = lines.indexOfFirstFrom(tableIndex + 1) { TABLE_HEADER.matches(it) }
      .let { if (it < 0) lines.size else it }
    for (index in nextTable - 1 downTo tableIndex + 1) {
      val assignment = ASSIGNMENT.matchEntire(lines[index])
      if (assignment?.groupValues?.get(1) == key) lines.removeAt(index)
    }
    lines.add(tableIndex + 1, "$key = $value")
    return lines.joinToString("\n").trimEnd() + "\n"
  }

  private inline fun <T> List<T>.indexOfFirstFrom(
    startIndex: Int,
    predicate: (T) -> Boolean,
  ): Int {
    for (index in startIndex until size) if (predicate(this[index])) return index
    return -1
  }

  private fun bracketDelta(value: String): Int {
    var quoted = false
    var escaped = false
    var delta = 0
    for (character in value) {
      if (!quoted && character == '#') break
      when {
        escaped -> escaped = false
        quoted && character == '\\' -> escaped = true
        character == '"' -> quoted = !quoted
        !quoted && character == '[' -> delta += 1
        !quoted && character == ']' -> delta -= 1
      }
    }
    return delta
  }

  private fun tomlString(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

  private val TABLE_HEADER = Regex("""\s*\[.*]\s*(?:#.*)?""")
  private val ASSIGNMENT = Regex("""\s*([A-Za-z0-9_-]+)\s*=\s*(.*)""")
  private val SETTINGS = setOf(
    "vpn_mode",
    "exclusions",
    "exclusions_tcp_early_ack_enabled",
    "exclusions_preresolve_enabled",
    "exclusions_preresolve_max_queries",
  )
}

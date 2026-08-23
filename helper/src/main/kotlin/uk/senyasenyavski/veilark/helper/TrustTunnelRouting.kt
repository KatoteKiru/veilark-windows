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
)

/** Converts Veilark routing semantics to TrustTunnel 1.0.x TOML settings. */
internal object TrustTunnelRouting {
  fun plan(profile: Profile): TrustTunnelRoutingPlan = when (profile.appliedRouting.mode) {
    RoutingMode.All -> TrustTunnelRoutingPlan("general", emptyList())
    RoutingMode.RussiaDirect -> TrustTunnelRoutingPlan(
      "general",
      requiredGeoExclusions(profile),
      true,
    )
    RoutingMode.RussiaVpn -> TrustTunnelRoutingPlan(
      "selective",
      requiredGeoExclusions(profile),
      true,
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
      val vpnOverrides = (vpn.domains + vpn.networks).toSet()
      TrustTunnelRoutingPlan(
        "general",
        (
          direct.networks + direct.domains
            .filterNot(vpnOverrides::contains)
            .flatMap(::domainWithSubdomains)
          ).distinct(),
        direct.domains.any { it !in vpnOverrides },
      )
    }
  }

  fun apply(path: Path, plan: TrustTunnelRoutingPlan) {
    val source = Files.readString(path, Charsets.UTF_8)
    Files.writeString(path, apply(source, plan), Charsets.UTF_8)
  }

  internal fun apply(source: String, plan: TrustTunnelRoutingPlan): String {
    require(plan.vpnMode == "general" || plan.vpnMode == "selective")
    require(source.lineSequence().any { it.trim() == "[endpoint]" }) {
      "TrustTunnel не создал секцию endpoint"
    }
    require(source.lineSequence().any { it.trim() == "[listener.tun]" }) {
      "TrustTunnel не создал TUN-конфигурацию"
    }
    val lines = source.replace("\r\n", "\n").split('\n')
    val firstSection = lines.indexOfFirst { TABLE_HEADER.matches(it) }
    check(firstSection >= 0)
    val topLevel = removeAssignments(lines.take(firstSection), SETTINGS)
      .dropLastWhile(String::isBlank)
    val routingBlock = buildList {
      add("vpn_mode = \"${plan.vpnMode}\"")
      add("exclusions_tcp_early_ack_enabled = ${plan.exclusionsTcpEarlyAckEnabled}")
      add("exclusions = [")
      plan.exclusions.forEach { add("  \"${tomlString(it)}\",") }
      add("]")
      add("")
    }
    return (topLevel + routingBlock + lines.drop(firstSection)).joinToString("\n")
      .trimEnd() + "\n"
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
  private val SETTINGS = setOf("vpn_mode", "exclusions", "exclusions_tcp_early_ack_enabled")
}

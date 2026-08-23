package com.example.veilark.profile

import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN

object ProfileSelection {
  const val AUTOMATIC_TAG = "auto"
  const val ROUTING_ALL = "all"
  const val ROUTING_MANUAL = "manual"
  const val ROUTING_RU_DIRECT = "ru_direct"
  const val ROUTING_RU_VPN = "ru_vpn"
  const val APPS_ALL = "all"
  const val APPS_ONLY = "only"
  const val APPS_BYPASS = "bypass"
  const val DPI_OFF = "off"
  const val DPI_TLS_FRAGMENT = "tls_fragment"

  fun encodeNodes(nodes: List<ConnectionNode>): String =
    JSONArray().apply {
      nodes.forEach { node ->
        put(
          JSONObject()
            .put("tag", node.tag)
            .put("name", node.name)
            .put("protocol", node.protocol),
        )
      }
    }.toString()

  fun decodeNodes(value: String?): List<ConnectionNode> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
      val array = JSONArray(value)
      buildList {
        repeat(array.length()) { index ->
          val item = array.getJSONObject(index)
          add(
            ConnectionNode(
              tag = item.getString("tag"),
              name = item.getString("name"),
              protocol = item.getString("protocol"),
            ),
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  fun select(config: String, tag: String, nodes: List<ConnectionNode>): String {
    require(tag == AUTOMATIC_TAG || nodes.any { it.tag == tag }) {
      "Выбранный узел отсутствует в подписке"
    }
    val root = JSONObject(config)
    val route = root.getJSONObject("route")
    route.put("final", tag)
    val dnsServers = root.optJSONObject("dns")?.optJSONArray("servers")
    if (dnsServers != null) {
      repeat(dnsServers.length()) { index ->
        val server = dnsServers.optJSONObject(index) ?: return@repeat
        if (server.optString("tag") == "secure-dns") server.put("detour", tag)
      }
    }
    route.optJSONArray("rules")?.let { rules ->
      repeat(rules.length()) { index ->
        val rule = rules.optJSONObject(index) ?: return@repeat
        if (rule.has("outbound") && rule.optString("outbound") != "direct") {
          rule.put("outbound", tag)
        }
      }
    }
    return root.toString(2)
  }

  fun applyRouting(
    config: String,
    mode: String,
    directEntries: String = "",
    vpnEntries: String = "",
    geoIpRuPath: String? = null,
    geoSiteCategoryRuPath: String? = null,
  ): String {
    require(mode in setOf(ROUTING_ALL, ROUTING_MANUAL, ROUTING_RU_DIRECT, ROUTING_RU_VPN))
    val root = JSONObject(config)
    val route = root.getJSONObject("route")
    val selectedOutbound = route.getString("final")
    route.remove("rules")
    route.remove("rule_set")
    val dns = root.optJSONObject("dns")
    dns?.remove("rules")
    secureDns(root)?.put("detour", selectedOutbound)
    val rules = baseTunRules()
    when (mode) {
      ROUTING_ALL -> {
        route.put("rules", rules)
        dns?.put("final", SECURE_DNS_TAG)
      }
      ROUTING_MANUAL -> {
        val direct = parseRoutingEntries(directEntries)
        val vpn = parseRoutingEntries(vpnEntries)
        require(
          direct.domains.isNotEmpty() || direct.networks.isNotEmpty() ||
            vpn.domains.isNotEmpty() || vpn.networks.isNotEmpty(),
        ) {
          "Добавьте хотя бы один домен или IP-диапазон"
        }
        addRule(rules, vpn, route.getString("final"))
        addRule(rules, direct, "direct")
        route.put("rules", rules)
        dns?.put("final", SECURE_DNS_TAG)
        val dnsRules = JSONArray()
        addDnsRule(dnsRules, vpn.domains, SECURE_DNS_TAG)
        addDnsRule(dnsRules, direct.domains, LOCAL_DNS_TAG)
        if (dnsRules.length() > 0) dns?.put("rules", dnsRules)
      }
      ROUTING_RU_DIRECT, ROUTING_RU_VPN -> {
        require(!geoIpRuPath.isNullOrBlank() && !geoSiteCategoryRuPath.isNullOrBlank()) {
          "Геоданные маршрутизации не готовы. Обновите их и повторите подключение."
        }
        route.put(
          "rule_set",
          JSONArray()
            .put(localRuleSet(GEOIP_RU_TAG, geoIpRuPath))
            .put(localRuleSet(GEOSITE_CATEGORY_RU_TAG, geoSiteCategoryRuPath)),
        )
        val targetOutbound = if (mode == ROUTING_RU_DIRECT) "direct" else selectedOutbound
        route.put(
          "rules",
          rules.put(
            JSONObject()
              .put("rule_set", JSONArray(listOf(GEOIP_RU_TAG, GEOSITE_CATEGORY_RU_TAG)))
              .put("action", "route")
              .put("outbound", targetOutbound),
          ),
        )
        if (mode == ROUTING_RU_VPN) {
          route.put("final", "direct")
          dns?.put("final", LOCAL_DNS_TAG)
          dns?.put("rules", JSONArray().put(ruleSetDnsRule(GEOSITE_CATEGORY_RU_TAG, SECURE_DNS_TAG)))
        } else {
          dns?.put("final", SECURE_DNS_TAG)
          dns?.put("rules", JSONArray().put(ruleSetDnsRule(GEOSITE_CATEGORY_RU_TAG, LOCAL_DNS_TAG)))
        }
      }
    }
    return root.toString(2)
  }

  fun routingEntries(value: String): NormalizedRoutingEntries = parseRoutingEntries(value)

  fun applyApplications(
    config: String,
    mode: String,
    packages: Set<String>,
    vpnPackage: String? = null,
  ): String {
    require(mode in setOf(APPS_ALL, APPS_ONLY, APPS_BYPASS))
    if (mode != APPS_ALL) require(packages.isNotEmpty()) { "Не выбраны приложения" }
    val root = JSONObject(config)
    val tun = root.getJSONArray("inbounds").getJSONObject(0)
    tun.remove("include_package")
    tun.remove("exclude_package")
    when (mode) {
      APPS_ONLY -> tun.put(
        "include_package",
        JSONArray((packages + listOfNotNull(vpnPackage)).sorted()),
      )
      APPS_BYPASS -> tun.put(
        "exclude_package",
        JSONArray((packages - listOfNotNull(vpnPackage).toSet()).sorted()),
      )
    }
    return root.toString(2)
  }

  fun applyDpiProtection(config: String, mode: String): String {
    require(mode in setOf(DPI_OFF, DPI_TLS_FRAGMENT))
    val root = JSONObject(config)
    val outbounds = root.getJSONArray("outbounds")
    repeat(outbounds.length()) { index ->
      val outbound = outbounds.optJSONObject(index) ?: return@repeat
      val tls = outbound.optJSONObject("tls") ?: return@repeat
      tls.remove("fragment")
      tls.remove("fragment_fallback_delay")
      tls.remove("record_fragment")
      if (mode == DPI_TLS_FRAGMENT && tls.optBoolean("enabled", true)) {
        tls.put("fragment", true)
        tls.put("fragment_fallback_delay", "20ms")
      }
    }
    return root.toString(2)
  }

  private fun addRule(
    rules: JSONArray,
    entries: NormalizedRoutingEntries,
    outbound: String,
  ) {
    if (entries.domains.isNotEmpty()) {
      rules.put(
        JSONObject()
          .put("domain_suffix", JSONArray(entries.domains))
          .put("action", "route")
          .put("outbound", outbound),
      )
    }
    if (entries.networks.isNotEmpty()) {
      rules.put(
        JSONObject()
          .put("ip_cidr", JSONArray(entries.networks))
          .put("action", "route")
          .put("outbound", outbound),
      )
    }
  }

  private fun addDnsRule(rules: JSONArray, domains: List<String>, server: String) {
    if (domains.isEmpty()) return
    rules.put(
      JSONObject()
        .put("domain_suffix", JSONArray(domains))
        .put("action", "route")
        .put("server", server),
    )
  }

  private fun ruleSetDnsRule(ruleSet: String, server: String): JSONObject = JSONObject()
    .put("rule_set", JSONArray().put(ruleSet))
    .put("action", "route")
    .put("server", server)

  /**
   * TUN traffic must keep these rules in every routing mode. In particular,
   * Windows strict-route can otherwise capture the system DNS request without
   * handing it to sing-box DNS, which leaves the tunnel up while host names
   * never resolve.
   */
  private fun baseTunRules(): JSONArray = JSONArray()
    .put(
      JSONObject()
        .put("action", "sniff")
        // 300 ms is sing-box's default. It is too short for a fragmented TLS
        // ClientHello or the first QUIC flight on a loaded Windows host, and a
        // missed domain makes geo routing fall back to the destination IP.
        .put("timeout", "1s"),
    )
    .put(
      JSONObject()
        .put("protocol", "dns")
        .put("action", "hijack-dns"),
    )
    .put(
      JSONObject()
        .put("ip_is_private", true)
        .put("action", "route")
        .put("outbound", "direct"),
    )

  private fun localRuleSet(tag: String, path: String): JSONObject = JSONObject()
    .put("type", "local")
    .put("tag", tag)
    .put("format", "binary")
    .put("path", path)

  private fun secureDns(root: JSONObject): JSONObject? {
    val servers = root.optJSONObject("dns")?.optJSONArray("servers") ?: return null
    repeat(servers.length()) { index ->
      val server = servers.optJSONObject(index) ?: return@repeat
      if (server.optString("tag") == "secure-dns") return server
    }
    return null
  }

  private fun parseRoutingEntries(value: String): NormalizedRoutingEntries {
    val domains = linkedSetOf<String>()
    val networks = linkedSetOf<String>()
    value.split(Regex("""[\s,;]+"""))
      .map(String::trim)
      .filter(String::isNotEmpty)
      .forEach { raw ->
        val candidate = raw.trim()
        if (candidate.contains('/') && !candidate.startsWith("http", ignoreCase = true)) {
          networks += normalizeNetwork(candidate)
        } else {
          val entry = candidate
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .removePrefix("*.")
            .removePrefix(".")
            .trimEnd('.')
            .lowercase()
          require(entry.isNotBlank()) { "Пустое правило маршрутизации" }
          if (entry.matches(IPV4)) {
            validateIpv4(entry, raw)
            networks += "$entry/32"
          } else if (entry.contains(':') && entry.matches(IPV6)) {
            networks += "$entry/128"
          } else {
            val ascii = runCatching { IDN.toASCII(entry) }
              .getOrElse { throw IllegalArgumentException("Некорректный домен: $raw") }
            require(ascii.matches(DOMAIN)) { "Некорректный домен: $raw" }
            domains += ascii
          }
        }
      }
    return NormalizedRoutingEntries(domains.toList(), networks.toList())
  }

  private fun normalizeNetwork(value: String): String {
    val address = value.substringBefore('/')
    val prefix = value.substringAfter('/').toIntOrNull()
      ?: throw IllegalArgumentException("Некорректная сеть: $value")
    val ipv4 = address.matches(IPV4)
    if (ipv4) validateIpv4(address, value)
    val ipv6 = address.contains(':') && address.matches(IPV6)
    require((ipv4 && prefix in 0..32) || (ipv6 && prefix in 0..128)) {
      "Некорректная сеть: $value"
    }
    return "${address.lowercase()}/$prefix"
  }

  private fun validateIpv4(address: String, original: String) {
    require(address.split('.').all { it.toIntOrNull() in 0..255 }) {
      "Некорректный IPv4: $original"
    }
  }

  private val DOMAIN =
    Regex("""(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?""")
  private val IPV4 = Regex("""(?:\d{1,3}\.){3}\d{1,3}""")
  private val IPV6 = Regex("""[0-9a-fA-F:]+""")
  private const val GEOIP_RU_TAG = "geoip-ru"
  private const val GEOSITE_CATEGORY_RU_TAG = "geosite-category-ru"
  private const val LOCAL_DNS_TAG = "bootstrap-dns"
  private const val SECURE_DNS_TAG = "secure-dns"
}

data class NormalizedRoutingEntries(
  val domains: List<String>,
  val networks: List<String>,
)

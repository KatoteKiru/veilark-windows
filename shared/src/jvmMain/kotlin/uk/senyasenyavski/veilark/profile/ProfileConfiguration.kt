package uk.senyasenyavski.veilark.profile

import com.example.veilark.profile.ProfileSelection
import org.json.JSONObject
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.GeoRoutingAssets
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.VpnEngine

object ProfileConfiguration {
  /**
   * Windows connection name given to the sing-box tunnel. It is how the session
   * recognises its own adapter, so it must stay in sync with the value written
   * into the generated configuration.
   */
  const val WINDOWS_TUN_INTERFACE = "Veilark"

  fun apply(
    profile: Profile,
    routing: RoutingSettings,
    selectedNodeTag: String = ProfileSelection.AUTOMATIC_TAG,
    geoRoutingAssets: GeoRoutingAssets? = null,
  ): Profile {
    if (routing.mode == RoutingMode.RussiaDirect || routing.mode == RoutingMode.RussiaVpn) {
      require(geoRoutingAssets != null) {
        "Геоданные маршрутизации не готовы. Обновите их и повторите подключение."
      }
    }
    if (profile.engine == VpnEngine.TrustTunnel) {
      val selectedConfig = profile.endpointConfigs[selectedNodeTag]
        ?: profile.endpointConfigs.values.firstOrNull()
        ?: profile.config
      return profile.copy(
        config = selectedConfig,
        appliedRouting = routing,
        geoRoutingAssets = geoRoutingAssets,
      )
    }
    var config = ProfileSelection.select(
      config = profile.config,
      tag = selectedNodeTag,
      nodes = profile.nodes.map {
        com.example.veilark.profile.ConnectionNode(it.tag, it.name, it.protocol)
      },
    )
    config = ProfileSelection.applyRouting(
      config = config,
      mode = when (routing.mode) {
        RoutingMode.All -> ProfileSelection.ROUTING_ALL
        RoutingMode.RussiaDirect -> ProfileSelection.ROUTING_RU_DIRECT
        RoutingMode.RussiaVpn -> ProfileSelection.ROUTING_RU_VPN
        RoutingMode.Manual -> ProfileSelection.ROUTING_MANUAL
      },
      directEntries = routing.directEntries,
      vpnEntries = routing.vpnEntries,
      geoIpRuPath = geoRoutingAssets?.geoIpRuPath,
      geoSiteCategoryRuPath = geoRoutingAssets?.geoSiteCategoryRuPath,
    )
    config = ProfileSelection.applyDpiProtection(
      config,
      if (routing.tlsFragment) {
        ProfileSelection.DPI_TLS_FRAGMENT
      } else {
        ProfileSelection.DPI_OFF
      },
    )
    config = ensureWindowsTun(config)
    return profile.copy(
      config = config,
      appliedRouting = routing,
      geoRoutingAssets = geoRoutingAssets,
    )
  }

  private fun ensureWindowsTun(config: String): String {
    val root = JSONObject(config)
    val inbounds = root.getJSONArray("inbounds")
    repeat(inbounds.length()) { index ->
      val inbound = inbounds.optJSONObject(index) ?: return@repeat
      if (inbound.optString("type") != "tun") return@repeat
      inbound.put("interface_name", WINDOWS_TUN_INTERFACE)
      // Per-application rules are an Android-only capability. Leaving them in a
      // Windows configuration makes sing-box reject the whole file.
      inbound.remove("include_package")
      inbound.remove("exclude_package")
    }
    // Without an explicit interface the core can route its own connection to
    // the server back into the tunnel it is building.
    root.getJSONObject("route").put("auto_detect_interface", true)
    return root.toString(2)
  }
}

package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ProfileSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.profile.ProfileConfiguration
import kotlin.system.exitProcess
import java.nio.file.Path
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI

/**
 * Headless connect/disconnect check for the stored profile.
 *
 * It exercises the same [WindowsVpnSession] the UI uses, so an elevated run
 * verifies the whole engine path without a window. Creating a WinTUN adapter
 * requires administrator rights; without them the check reports why and stops.
 *
 * Usage: `LiveTunnelCheck [singbox|trusttunnel|fairway] [holdSeconds] [cycles]`
 */
object LiveTunnelCheck {
  @JvmStatic
  fun main(args: Array<String>) {
    val requestedEngine = args.getOrNull(0)?.lowercase()
    val holdSeconds = args.getOrNull(1)?.toIntOrNull() ?: 12
    val cycles = args.getOrNull(2)?.toIntOrNull() ?: 1
    require(holdSeconds in 1..120 && cycles in 1..3) { "Invalid diagnostic bounds" }

    if (!ElevationManager().isElevated()) {
      error("НЕ ХВАТАЕТ ПРАВ: запустите проверку в терминале от имени администратора")
    }

    val checkStore = System.getProperty("veilark.check.profileStore")?.let(Path::of)
    val stored = (checkStore?.let { ProfileStore(it) } ?: ProfileStore()).load()
    if (stored.profiles.isEmpty()) {
      error("НЕТ ПРОФИЛЕЙ: сначала импортируйте подписку в приложении")
    }
    val engine = when (requestedEngine) {
      "singbox", "sing-box" -> VpnEngine.SingBox
      "trusttunnel", "trust", "tt" -> VpnEngine.TrustTunnel
      "fairway", "core" -> VpnEngine.VeilarkCore
      else -> stored.selectedEngine
    }
    val profile = stored.profiles.firstOrNull { it.engine == engine } ?: run {
      error("НЕТ ПРОФИЛЯ для ядра $engine")
    }

    report("Ядро: $engine, узлов: ${profile.nodes.size}")
    check(WindowsNetwork.tunnels().none { it.operational }) { "An active VPN must be stopped before this diagnostic" }
    reportAdapters("До подключения")

    val configured = ProfileConfiguration.apply(
      profile,
      stored.routing,
      stored.selectedNodeTags[engine] ?: defaultTag(engine, profile.nodes.firstOrNull()?.tag),
    )

    repeat(cycles) { cycle ->
      report("--- Цикл ${cycle + 1} из $cycles ---")
      runCycle(configured, holdSeconds)
    }
    reportAdapters("После отключения")
    report("Проверка завершена")
    // Nothing else must keep this diagnostic process alive once it has reported.
    exitProcess(0)
  }

  private fun runCycle(
    profile: uk.senyasenyavski.veilark.model.Profile,
    holdSeconds: Int,
  ) = runBlocking {
    val session = WindowsVpnSession()
    val previousLuids = WindowsNetwork.tunnels().map { it.luid }.toSet()
    val watcher = launch {
      var previous: VpnPhase? = null
      while (true) {
        val state = session.state.value
        if (state.phase != previous) {
          previous = state.phase
          report("фаза: ${describe(state.phase)}")
        }
        state.traffic?.let {
          if (it.bytesIn > 0 || it.bytesOut > 0) {
            report(
              "трафик через «${it.adapter}»: " +
                "принято ${format(it.bytesIn)}, отправлено ${format(it.bytesOut)}",
            )
          }
        }
        delay(2_000)
      }
    }

    try {
      val startedAt = System.currentTimeMillis()
      session.connect(profile)
      val elapsed = System.currentTimeMillis() - startedAt
      report("подключение заняло ${elapsed} мс, итог: ${describe(session.state.value.phase)}")
      check(session.state.value.phase is VpnPhase.Connected) { "Tunnel did not reach Connected" }
      val before = requireNotNull(session.state.value.traffic) { "Tunnel counters unavailable" }
      val adapter = WindowsNetwork.adapters().single {
        it.alias == before.adapter && it.luid !in previousLuids && it.operational && it.isTunnel
      }
      if (profile.engine == VpnEngine.VeilarkCore) {
        check(FairwayAdapterIdentityStore().loadOrCreate().matchesWindowsGuid(adapter.interfaceGuid)) {
          "Tunnel does not match the installation adapter identity"
        }
      }
      val sourceIpv4 = requireNotNull(NetworkInterface.getByIndex(adapter.index)).inetAddresses.asSequence()
        .filterIsInstance<Inet4Address>().filterNot { it.isLinkLocalAddress }.single().hostAddress
      WindowsCurlInternetProbe.TARGETS.forEach { target ->
        val addresses = InetAddress.getAllByName(URI(target).host).filterIsInstance<Inet4Address>()
        check(addresses.isNotEmpty() && addresses.all { WindowsNetwork.bestIpv4InterfaceIndex(it.hostAddress) == adapter.index }) {
          "Probe destination route bypasses the owned tunnel"
        }
      }
      check(WindowsCurlInternetProbe.probe(sourceIpv4) == null) { "Bound Internet probe failed while connected" }

    delay(holdSeconds * 1_000L)
    val traffic = session.state.value.traffic
    if (traffic == null) {
      report("ВНИМАНИЕ: счётчики трафика недоступны")
    } else {
      report(
        "итог трафика: принято ${format(traffic.bytesIn)}, " +
          "отправлено ${format(traffic.bytesOut)}",
      )
    }

      check(traffic != null && traffic.bytesIn > before.bytesIn && traffic.bytesOut > before.bytesOut) {
        "Bidirectional tunnel traffic was not observed"
      }
      check(session.state.value.phase is VpnPhase.Connected) { "Connection was lost during the hold interval" }
      check(WindowsNetwork.refresh(adapter.luid)?.operational == true) { "Owned tunnel disappeared during the hold interval" }
    } finally {
      try { session.disconnect() } finally { watcher.cancel() }
    }
    check(session.state.value.phase is VpnPhase.Idle) { "Tunnel did not stop" }
    report("после отключения: ${describe(session.state.value.phase)}")
    delay(1_500)
    check(WindowsNetwork.tunnels().all { it.luid in previousLuids }) { "New tunnel adapter remains after Stop" }
    check(WindowsCurlInternetProbe.probe() == null) { "Internet did not recover after Stop" }
  }

  private fun defaultTag(engine: VpnEngine, firstNode: String?): String =
    if (engine == VpnEngine.SingBox) {
      ProfileSelection.AUTOMATIC_TAG
    } else {
      firstNode ?: ProfileSelection.AUTOMATIC_TAG
    }

  private fun reportAdapters(title: String) {
    val tunnels = WindowsNetwork.tunnels()
    if (tunnels.isEmpty()) {
      report("$title: туннельных адаптеров нет")
      return
    }
    report(
      "$title: " + tunnels.joinToString("; ") {
        "«${it.alias}» ${if (it.operational) "активен" else "осиротел"} " +
          "(индекс ${it.index})"
      },
    )
  }

  private fun describe(phase: VpnPhase): String = when (phase) {
    VpnPhase.Idle -> "отключено"
    VpnPhase.NeedsElevation -> "нужны права администратора"
    VpnPhase.Preparing -> "подготовка"
    VpnPhase.Connecting -> "подключение"
    is VpnPhase.Connected -> "ПОДКЛЮЧЕНО"
    is VpnPhase.Degraded -> "ПОДКЛЮЧЕНО С ОГОВОРКОЙ: ${phase.message}"
    VpnPhase.Stopping -> "остановка"
    is VpnPhase.Error -> "ОШИБКА [${phase.code}] ${phase.message}"
  }

  private fun format(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f МиБ".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f КиБ".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes Б"
  }

  private fun report(message: String) {
    println("[veilark-check] $message")
  }
}

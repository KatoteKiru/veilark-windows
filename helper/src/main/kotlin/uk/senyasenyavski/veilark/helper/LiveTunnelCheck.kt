package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ProfileSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.profile.ProfileConfiguration
import kotlin.system.exitProcess

/**
 * Headless connect/disconnect check for the stored profile.
 *
 * It exercises the same [WindowsVpnSession] the UI uses, so an elevated run
 * verifies the whole engine path without a window. Creating a WinTUN adapter
 * requires administrator rights; without them the check reports why and stops.
 *
 * Usage: `LiveTunnelCheck [singbox|trusttunnel] [holdSeconds] [cycles]`
 */
object LiveTunnelCheck {
  @JvmStatic
  fun main(args: Array<String>) {
    val requestedEngine = args.getOrNull(0)?.lowercase()
    val holdSeconds = args.getOrNull(1)?.toIntOrNull() ?: 12
    val cycles = args.getOrNull(2)?.toIntOrNull() ?: 1

    if (!ElevationManager().isElevated()) {
      report("НЕ ХВАТАЕТ ПРАВ: запустите проверку в терминале от имени администратора")
      return
    }

    val stored = ProfileStore().load()
    if (stored.profiles.isEmpty()) {
      report("НЕТ ПРОФИЛЕЙ: сначала импортируйте подписку в приложении")
      return
    }
    val engine = when (requestedEngine) {
      "singbox", "sing-box" -> VpnEngine.SingBox
      "trusttunnel", "trust", "tt" -> VpnEngine.TrustTunnel
      else -> stored.selectedEngine
    }
    val profile = stored.profiles.firstOrNull { it.engine == engine } ?: run {
      report("НЕТ ПРОФИЛЯ для ядра $engine. Доступны: ${stored.profiles.map { it.engine }}")
      return
    }

    report("Ядро: $engine, профиль «${profile.name}», узлов: ${profile.nodes.size}")
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

    val startedAt = System.currentTimeMillis()
    session.connect(profile)
    val elapsed = System.currentTimeMillis() - startedAt
    report("подключение заняло ${elapsed} мс, итог: ${describe(session.state.value.phase)}")

    if (session.state.value.phase is VpnPhase.Error) {
      watcher.cancel()
      return@runBlocking
    }

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

    session.disconnect()
    report("после отключения: ${describe(session.state.value.phase)}")
    watcher.cancel()
    delay(1_500)
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

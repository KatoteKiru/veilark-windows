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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom
import com.sun.jna.platform.win32.IPHlpAPI
import org.json.JSONObject

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
    execute(args)
    exitProcess(0)
  }

  internal fun execute(args: Array<String>, onCycleReceipt: (Int, JSONObject) -> Unit = { _, _ -> }) {
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
      runCycle(configured, holdSeconds, cycle + 1, onCycleReceipt)
    }
    reportAdapters("После отключения")
    report("Проверка завершена")
  }

  private fun runCycle(
    profile: uk.senyasenyavski.veilark.model.Profile,
    holdSeconds: Int,
    cycle: Int,
    onCycleReceipt: (Int, JSONObject) -> Unit,
  ) = runBlocking {
    val targets = if (System.getProperty("veilark.check.ci") == "true") {
      listOf("https://1.1.1.1/cdn-cgi/trace")
    } else WindowsCurlInternetProbe.TARGETS
    val session = WindowsVpnSession()
    val previousLuids = WindowsNetwork.tunnels().map { it.luid }.toSet()
    val receipt = JSONObject().put("cycle", cycle).put("passed", false)
    fun mark(stage: String) {
      receipt.put(stage, true).put("stage", stage)
      onCycleReceipt(cycle, JSONObject(receipt.toString()))
    }
    fun captureConnectionPhase() {
      val phase = session.state.value.phase
      val name = when (phase) {
        VpnPhase.Idle -> "Idle"
        VpnPhase.NeedsElevation -> "NeedsElevation"
        VpnPhase.Preparing -> "Preparing"
        VpnPhase.Connecting -> "Connecting"
        is VpnPhase.Connected -> "Connected"
        is VpnPhase.Degraded -> "Degraded"
        VpnPhase.Stopping -> "Stopping"
        is VpnPhase.Error -> "Error"
      }
      receipt.put("connectionPhase", name)
      if (phase is VpnPhase.Error) {
        if (phase.code.matches(Regex("[A-Z0-9_-]{1,80}"))) receipt.put("connectionCode", phase.code)
        val status = Regex("^Veilark Core stopped \\(phase=(-?[0-9]{1,10}), outcome=(-?[0-9]{1,10}), os=([0-9]{1,10})(?:, hostStage=([0-9]{1,10}), failedStage=([0-9]{1,10}))?\\)$")
          .matchEntire(phase.message)
        status?.let {
          listOf("nativePhase", "nativeOutcome", "nativeOs", "hostStage", "failedHostStage").forEachIndexed { index, key ->
            it.groupValues[index + 1].toLongOrNull()?.let { number -> receipt.put(key, number) }
          }
        }
      }
      onCycleReceipt(cycle, JSONObject(receipt.toString()))
    }
    var ownedLuid: Long? = null
    val fairwayIdentity = if (profile.engine == VpnEngine.VeilarkCore) {
      FairwayAdapterIdentityStore().loadOrCreate()
    } else null
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
      mark("started")
      val startedAt = System.currentTimeMillis()
      session.connect(profile)
      captureConnectionPhase()
      val elapsed = System.currentTimeMillis() - startedAt
      report("подключение заняло ${elapsed} мс, итог: ${describe(session.state.value.phase)}")
      check(session.state.value.phase is VpnPhase.Connected) { "Tunnel did not reach Connected" }
      mark("connected")
      val before = requireNotNull(session.state.value.traffic) { "Tunnel counters unavailable" }
      val adapter = WindowsNetwork.adapters().single {
        it.alias == before.adapter && it.luid !in previousLuids &&
          if (fairwayIdentity != null) it.isOwnedFairway(fairwayIdentity) else it.operational && it.isTunnel
      }
      ownedLuid = adapter.luid
      mark("ownedAdapter")
      if (profile.engine == VpnEngine.VeilarkCore) {
        check(FairwayAdapterIdentityStore().loadOrCreate().matchesWindowsGuid(adapter.interfaceGuid)) {
          "Tunnel does not match the installation adapter identity"
        }
      }
      val sourceIpv4 = requireNotNull(NetworkInterface.getByIndex(adapter.index)).inetAddresses.asSequence()
        .filterIsInstance<Inet4Address>().filterNot { it.isLinkLocalAddress }.single().hostAddress
      targets.forEach { target ->
        val addresses = InetAddress.getAllByName(URI(target).host).filterIsInstance<Inet4Address>()
        check(addresses.isNotEmpty() && addresses.all { WindowsNetwork.bestIpv4InterfaceIndex(it.hostAddress) == adapter.index }) {
          "Probe destination route bypasses the owned tunnel"
        }
      }
      check(WindowsCurlInternetProbe.probe(sourceIpv4, targets) == null) { "Bound Internet probe failed while connected" }
      mark("boundInternet")
      if (System.getProperty("veilark.check.ci") == "true") {
        probeBoundDns(sourceIpv4)
        mark("boundUdpDns")
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

      check(traffic != null && traffic.bytesIn > before.bytesIn && traffic.bytesOut > before.bytesOut) {
        "Bidirectional tunnel traffic was not observed"
      }
      check(session.state.value.phase is VpnPhase.Connected) { "Connection was lost during the hold interval" }
      check(WindowsNetwork.refresh(adapter.luid)?.operational == true) { "Owned tunnel disappeared during the hold interval" }
      mark("bidirectionalTraffic")
    } finally {
      // Cleanup and its receipt also run when authentication, probing or hold fails.
      try {
        if (ownedLuid == null && fairwayIdentity != null) {
          ownedLuid = WindowsNetwork.adapters().singleOrNull {
            fairwayIdentity.matchesWindowsGuid(it.interfaceGuid)
          }?.luid
        }
        try { session.disconnect() } finally { watcher.cancel() }
        check(session.state.value.phase is VpnPhase.Idle) { "Tunnel did not stop" }
        mark("stopped")
        ownedLuid?.let { requireAdapterAbsent(it) }
        check(WindowsNetwork.tunnels().all { it.luid in previousLuids }) { "New tunnel adapter remains after Stop" }
        mark("adapterAbsent")
        check(WindowsCurlInternetProbe.probe(targets = targets) == null) { "Internet did not recover after Stop" }
        mark("internetRecovered")
      } finally {
        watcher.cancel()
        onCycleReceipt(cycle, JSONObject(receipt.toString()))
      }
    }
    mark("passed")
  }

  /** One bounded real UDP transaction, bound to the owned TUN source address. */
  private fun probeBoundDns(sourceIpv4: String) {
    val id = SecureRandom().nextInt(65536)
    val question = byteArrayOf(
      (id ushr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
      3, 111, 110, 101, 3, 111, 110, 101, 3, 111, 110, 101, 3, 111, 110, 101,
      0, 0, 1, 0, 1,
    )
    DatagramSocket(null).use { socket ->
      socket.bind(InetSocketAddress(sourceIpv4, 0))
      socket.connect(InetSocketAddress("1.1.1.1", 53))
      socket.soTimeout = 5_000
      socket.send(DatagramPacket(question, question.size))
      val response = DatagramPacket(ByteArray(4096), 4096)
      socket.receive(response)
      val data = response.data
      fun word(offset: Int) = ((data[offset].toInt() and 255) shl 8) or (data[offset + 1].toInt() and 255)
      check(response.length >= 12 && word(0) == id &&
        word(2) and 0xFA0F == 0x8000 && word(4) == 1 && word(6) > 0) {
        "Bound UDP DNS query failed"
      }
    }
  }

  private suspend fun requireAdapterAbsent(luid: Long) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      val row = IPHlpAPI.MIB_IF_ROW2().apply { InterfaceLuid = luid }
      val result = IPHlpAPI.INSTANCE.GetIfEntry2(row)
      if (result == 2 || result == 1168) return
      check(result == 0) { "Cannot prove adapter absence: GetIfEntry2=$result" }
      check(System.nanoTime() < deadline) { "Owned adapter remains after Stop" }
      delay(100)
    }
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
    if (System.getProperty("veilark.check.ci") != "true") println("[veilark-check] $message")
  }
}

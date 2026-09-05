package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Removes only the device observed on this controller's live tunnel, after its core exits. */
internal object OwnedWinTunCleanup {
  internal val INSTANCE_ID = Regex("""(?i)SWD\\WINTUN\\\{[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}}""")
  internal val GUID = Regex("""(?i)\{[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}}""")

  /** Recovers an exact stopped Veilark adapter left by older versions without a saved PnP identity. */
  suspend fun recoverStoppedAdapters() = withContext(Dispatchers.IO) {
    for (adapter in WindowsNetwork.tunnels()) {
      val tunnel = ReadyTunnel(adapter.index, adapter.luid, adapter.alias, adapter.description)
      if (!eligible(tunnel) || adapter.operational) continue
      val guid = adapter.interfaceGuid?.takeIf(GUID::matches)
        ?: error("Нельзя безопасно определить идентификатор остановленного адаптера Veilark")
      val script = ghostLookupScript(guid)
      val instance = runScript(script, 6_000)?.lineSequence()?.map(String::trim)
        ?.filter(INSTANCE_ID::matches)?.singleOrNull()
        ?: error("Не удалось безопасно сопоставить остановленный адаптер Veilark с устройством Windows")
      check(remove(tunnel, instance)) { "Windows не удалила остановленный адаптер Veilark; подключение не запущено" }
    }
  }

  internal fun ghostLookupScript(guid: String): String {
    require(GUID.matches(guid))
    return "\$ErrorActionPreference = 'Stop'; " +
        "Get-PnpDevice -Class Net -ErrorAction Stop | Where-Object { " +
        "\$_.InstanceId -like 'SWD\\WINTUN\\*' -and \$_.Status -ne 'OK' } | ForEach-Object { " +
        "\$device = \$_; \$driver = (Get-PnpDeviceProperty -InstanceId \$device.InstanceId -KeyName DEVPKEY_Device_Driver -ErrorAction SilentlyContinue).Data; " +
        "if (\$driver -match '^\\{4d36e972-e325-11ce-bfc1-08002be10318\\}\\\\[0-9]{4}\$') { " +
        "\$config = (Get-ItemProperty -LiteralPath (\"HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Class\\\" + \$driver) -Name NetCfgInstanceId -ErrorAction SilentlyContinue).NetCfgInstanceId; " +
        "if (\$config -eq '$guid') { Write-Output \$device.InstanceId } } }"
  }

  suspend fun capture(tunnel: ReadyTunnel): String? = withContext(Dispatchers.IO) {
    if (!eligible(tunnel)) return@withContext null
    val current = WindowsNetwork.refresh(tunnel.luid) ?: return@withContext null
    if (!sameAdapter(tunnel, current)) return@withContext null
    val script = "@(Get-NetAdapter -IncludeHidden -ErrorAction Stop | Where-Object { " +
      "\$_.InterfaceIndex -eq ${tunnel.index} -and [uint64]\$_.NetLuid -eq ${tunnel.luid} -and " +
      "\$_.Name -eq 'Veilark' } | Select-Object -ExpandProperty PnPDeviceID) | ForEach-Object { Write-Output \$_ }"
    val result = runScript(script, 5_000) ?: return@withContext null
    result.lineSequence().map(String::trim).filter(INSTANCE_ID::matches).singleOrNull()
  }

  suspend fun remove(tunnel: ReadyTunnel, instanceId: String): Boolean = withContext(Dispatchers.IO) {
    if (!eligible(tunnel) || !INSTANCE_ID.matches(instanceId)) return@withContext false
    val current = WindowsNetwork.refresh(tunnel.luid) ?: return@withContext true
    if (!sameAdapter(tunnel, current) || current.operational) return@withContext false
    // The exact instance ID was obtained before stopping the owned process.
    // Unknown/non-present PnP entries are ghosts, not a running competing VPN.
    val script = "\$ErrorActionPreference = 'Stop'; " +
      "\$device = Get-PnpDevice -InstanceId '$instanceId' -ErrorAction SilentlyContinue; " +
      "if (-not \$device) { Write-Output 'ABSENT'; exit 0 }; " +
      "if (\$device.Status -eq 'OK') { throw 'Refusing removal of a live PnP device' }; " +
      "& \"\$env:SystemRoot\\System32\\pnputil.exe\" /remove-device '$instanceId'; " +
      "if (\$LASTEXITCODE -ne 0) { throw \"Owned device removal failed: \$LASTEXITCODE\" }"
    if (runScript(script, 6_000) == null) return@withContext false
    // PnP removal completes asynchronously; observe the same identity, never a fixed sleep.
    repeat(10) {
      if (WindowsNetwork.refresh(tunnel.luid) == null) return@withContext true
      delay(100)
    }
    false
  }

  internal fun eligible(tunnel: ReadyTunnel): Boolean =
    tunnel.index > 0 && tunnel.luid > 0 && tunnel.alias == "Veilark" &&
      (tunnel.description.contains("wintun", ignoreCase = true) ||
        tunnel.description.contains("sing-tun", ignoreCase = true))

  internal fun sameAdapter(tunnel: ReadyTunnel, adapter: NetworkAdapter): Boolean =
    eligible(tunnel) && adapter.index == tunnel.index && adapter.luid == tunnel.luid &&
      adapter.alias == tunnel.alias && adapter.description == tunnel.description

  /** Keep PowerShell quoting separate from Windows ProcessBuilder argument quoting. */
  internal fun encodedCommand(script: String): String =
    Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))

  internal fun commandArguments(script: String): List<String> = listOf(
    "-NoProfile", "-NonInteractive", "-OutputFormat", "Text", "-EncodedCommand",
    encodedCommand("\$ProgressPreference = 'SilentlyContinue'; $script"),
  )

  private suspend fun runScript(script: String, timeoutMillis: Long): String? = try {
    val powershell = Path.of(System.getenv("SystemRoot") ?: "C:\\Windows", "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
    if (!Files.isRegularFile(powershell)) return null
    val result = ProcessBuilder(listOf(powershell.toString()) + commandArguments(script))
      .redirectErrorStream(true).start().captureCancellable(timeoutMillis, maximumOutputChars = 2_000)
    if (result.succeeded) result.output else null
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    null
  }
}

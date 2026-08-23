package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Removes WinTUN device nodes left behind when a VPN core was terminated before
 * it could release its adapter.
 *
 * A leaked adapter keeps its connection name reserved, so sing-box refuses to
 * start with `configure tun interface: Cannot create a file when that file
 * already exists` until the ghost is gone. Only devices Windows no longer
 * reports as `OK` are removed: a ghost cannot be bound to a running tunnel, so
 * a competing VPN is never disturbed.
 */
internal object WinTunJanitor {
  private const val REMOVE_SCRIPT = """
${'$'}ErrorActionPreference = 'Stop'
${'$'}ghosts = @(Get-PnpDevice -Class Net -ErrorAction SilentlyContinue |
  Where-Object { ${'$'}_.InstanceId -like 'SWD\WINTUN\*' -and ${'$'}_.Status -ne 'OK' })
foreach (${'$'}ghost in ${'$'}ghosts) {
  try {
    Remove-PnpDevice -InstanceId ${'$'}ghost.InstanceId -Confirm:${'$'}false -ErrorAction Stop
    Write-Output "REMOVED ${'$'}(${'$'}ghost.InstanceId)"
  } catch {
    Write-Output "FAILED ${'$'}(${'$'}ghost.InstanceId)"
  }
}
Write-Output "GHOSTS ${'$'}(${'$'}ghosts.Count)"
"""

  /**
   * Drops stale tunnel adapters when at least one is present.
   *
   * The interface table is consulted first so that the usual case, a clean
   * machine, costs one native call instead of a PowerShell process.
   */
  suspend fun removeGhostAdapters(): Int = withContext(Dispatchers.IO) {
    val stale = WindowsNetwork.tunnels().filterNot(NetworkAdapter::operational)
    if (stale.isEmpty()) return@withContext 0
    SafeLog.write(
      "Найдены осиротевшие TUN-адаптеры: ${stale.joinToString { it.alias.ifBlank { "без имени" } }}",
    )
    val powershell = resolvePowerShell() ?: run {
      SafeLog.write("PowerShell не найден, очистка адаптеров пропущена")
      return@withContext 0
    }
    val result = runCatching {
      ProcessBuilder(
        powershell.toString(),
        "-NoLogo",
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        REMOVE_SCRIPT,
      )
        .redirectErrorStream(true)
        .start()
        .capture(30_000, maximumOutputChars = 4_000)
    }.getOrElse {
      SafeLog.write("Очистка TUN-адаптеров не запустилась: ${it.message}")
      return@withContext 0
    }
    val removed = result.output.lineSequence().count { it.startsWith("REMOVED") }
    val failed = result.output.lineSequence().count { it.startsWith("FAILED") }
    if (failed > 0) {
      SafeLog.write(
        "Не удалось удалить осиротевшие адаптеры: $failed. " +
          "Требуются права администратора.",
      )
    }
    if (removed > 0) SafeLog.write("Удалено осиротевших TUN-адаптеров: $removed")
    removed
  }

  private fun resolvePowerShell(): Path? {
    val systemRoot = System.getenv("SystemRoot").orEmpty()
    return buildList {
      if (systemRoot.isNotBlank()) {
        add(Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe"))
      }
      add(Path.of("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"))
    }.firstOrNull(Files::isRegularFile)
  }
}

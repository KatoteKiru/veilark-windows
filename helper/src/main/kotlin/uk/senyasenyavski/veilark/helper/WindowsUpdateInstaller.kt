package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.withTimeout
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateClient
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Base64

/** The durable result left by the detached installer for the next app launch. */
data class UpdateInstallOutcome(
  val phase: UpdateInstallPhase,
  val versionCode: Int,
  val versionName: String,
  val exitCode: Int?,
  val message: String,
)

enum class UpdateInstallPhase {
  Scheduled,
  WaitingForExit,
  Installing,
  InteractiveFallback,
  Succeeded,
  Cancelled,
  Failed,
}

/** Returned only after the verified updater helper was actually started. */
class ScheduledWindowsUpdate internal constructor(
  val update: AppUpdate,
  val installer: Path,
  val statusFile: Path,
)

class PreparedWindowsUpdate internal constructor(
  val update: AppUpdate,
  val installer: Path,
  internal val command: List<String>,
  internal val helperLog: Path,
)

/**
 * Prepares a jpackage EXE update and launches a detached, hidden PowerShell
 * helper. The helper waits for this JVM to exit before touching the installed
 * application, verifies size + SHA-256 one final time, then attempts a quiet
 * WiX/jpackage upgrade (`/quiet /norestart`).
 *
 * A system-wide jpackage installation still has to cross the Windows UAC
 * boundary. If Veilark is not already elevated, Windows may show the standard
 * UAC consent prompt; bypassing it would be unsafe. If the generated EXE does
 * not accept silent installation, the helper falls back to its normal UI and
 * records that fact for the next launch.
 */
class WindowsUpdateInstaller(
  private val updateClient: UpdateClient = UpdateClient(),
  private val updateDirectory: Path = VeilarkPaths.dataDirectory.resolve("updates"),
  private val processIdsProvider: (Path) -> List<Long> = ::veilarkProcessIds,
  private val appExecutableProvider: () -> Path? = {
    ProcessHandle.current().info().command().orElse(null)?.let(Path::of)
  },
  private val powershellProvider: () -> Path = ::systemPowerShell,
  private val detachedLauncher: (List<String>, Path, Path) -> Unit = ::launchDetached,
) {
  private val statusFile: Path get() = updateDirectory.resolve(LAST_STATUS_FILE)

  fun validateDownloaded(update: AppUpdate, installer: Path): Path =
    installer.toAbsolutePath().normalize().also { normalized ->
      updateClient.verifyDownloadedInstaller(normalized, update)
      require(normalized.fileName.toString().endsWith(".exe", ignoreCase = true)) {
        "Обновление Windows должно быть EXE-установщиком"
      }
    }

  fun prepare(update: AppUpdate, installer: Path): PreparedWindowsUpdate {
    val normalizedInstaller = validateDownloaded(update, installer)
    val appExecutable = appExecutableProvider()
      ?.toAbsolutePath()
      ?.normalize()
      ?: error("Не удалось определить путь установленного Veilark")
    require(
      Files.isRegularFile(appExecutable, LinkOption.NOFOLLOW_LINKS) &&
        appExecutable.fileName.toString().equals("Veilark.exe", ignoreCase = true),
    ) {
      "Бесшовное обновление доступно только для установленного Veilark.exe"
    }

    val powershell = powershellProvider().toAbsolutePath().normalize()
    require(Files.isRegularFile(powershell, LinkOption.NOFOLLOW_LINKS)) {
      "Не найден системный Windows PowerShell"
    }
    Files.createDirectories(updateDirectory)
    val helperLog = updateDirectory.resolve("update-helper-${update.versionCode}.log")
    val installerLog = updateDirectory.resolve("installer-${update.versionCode}.log")
    val content = helperScript(
      update = update,
      installer = normalizedInstaller,
      appExecutable = appExecutable,
      state = statusFile,
      installerLog = installerLog,
      processIds = processIdsProvider(appExecutable)
        .filter { it > 0L }
        .distinct()
        .also { require(it.isNotEmpty()) { "Не удалось определить процессы Veilark" } },
    )
    // The command is injected directly into the already-running child process.
    // An elevated Veilark must never execute a script from user-writable
    // LOCALAPPDATA: another unelevated process could replace such a file.
    val encodedCommand = Base64.getEncoder().encodeToString(
      content.toByteArray(Charsets.UTF_16LE),
    )
    val command = listOf(
      powershell.toString(),
      "-NoLogo",
      "-NoProfile",
      "-NonInteractive",
      "-WindowStyle",
      "Hidden",
      "-EncodedCommand",
      encodedCommand,
    )
    require(command.sumOf { it.length + 1 } < MAX_WINDOWS_COMMAND_LINE) {
      "Команда помощника обновления слишком длинная"
    }
    return PreparedWindowsUpdate(update, normalizedInstaller, command, helperLog)
  }

  internal fun launchPrepared(prepared: PreparedWindowsUpdate): ScheduledWindowsUpdate {
    validateDownloaded(prepared.update, prepared.installer)
    writeStatus(
      UpdateInstallOutcome(
        phase = UpdateInstallPhase.Scheduled,
        versionCode = prepared.update.versionCode,
        versionName = prepared.update.versionName,
        exitCode = null,
        message = "Обновление проверено и подготовлено",
      ),
    )
    try {
      detachedLauncher(prepared.command, updateDirectory, prepared.helperLog)
    } catch (error: Throwable) {
      writeStatus(
        UpdateInstallOutcome(
          phase = UpdateInstallPhase.Failed,
          versionCode = prepared.update.versionCode,
          versionName = prepared.update.versionName,
          exitCode = null,
          message = "Не удалось запустить помощник обновления",
        ),
      )
      throw IllegalStateException("Не удалось запустить установку обновления", error)
    }
    return ScheduledWindowsUpdate(prepared.update, prepared.installer, statusFile)
  }

  /**
   * Convenience API for an already-stopped VPN. Most UI code should use
   * [SeamlessUpdateCoordinator], which enforces preparation before shutdown.
   */
  fun scheduleAfterExit(update: AppUpdate, installer: Path): ScheduledWindowsUpdate =
    launchPrepared(prepare(update, installer))

  fun readLastOutcome(): UpdateInstallOutcome? = runCatching {
    if (!Files.isRegularFile(statusFile, LinkOption.NOFOLLOW_LINKS)) return null
    require(Files.size(statusFile) <= MAX_STATUS_BYTES) { "Статус обновления повреждён" }
    val values = Files.readAllLines(statusFile, Charsets.UTF_8)
      .mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
      }
      .toMap()
    UpdateInstallOutcome(
      phase = enumValueOf(values.getValue("phase")),
      versionCode = values.getValue("versionCode").toInt(),
      versionName = values.getValue("versionName"),
      exitCode = values["exitCode"]?.takeIf(String::isNotBlank)?.toInt(),
      message = values["message64"]
        ?.let { Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }
        .orEmpty()
        .take(MAX_STATUS_MESSAGE_LENGTH),
    )
  }.getOrNull()

  fun clearLastOutcome() {
    Files.deleteIfExists(statusFile)
    Files.deleteIfExists(statusFile.resolveSibling("${statusFile.fileName}.tmp"))
  }

  /** Removes abandoned helper scripts; active/recent helpers are preserved. */
  fun cleanupStaleHelpers(olderThanMillis: Long = System.currentTimeMillis() - STALE_HELPER_AGE): Int {
    if (!Files.isDirectory(updateDirectory, LinkOption.NOFOLLOW_LINKS)) return 0
    var removed = 0
    Files.list(updateDirectory).use { entries ->
      entries.forEach { candidate ->
        if (
          PENDING_SCRIPT.matches(candidate.fileName.toString()) &&
          Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) &&
          Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS).toMillis() < olderThanMillis &&
          Files.deleteIfExists(candidate)
        ) {
          removed += 1
        }
      }
    }
    return removed
  }

  @Synchronized
  private fun writeStatus(outcome: UpdateInstallOutcome) {
    Files.createDirectories(updateDirectory)
    val encodedMessage = Base64.getEncoder().encodeToString(
      outcome.message.take(MAX_STATUS_MESSAGE_LENGTH).toByteArray(Charsets.UTF_8),
    )
    val temporary = statusFile.resolveSibling("${statusFile.fileName}.tmp")
    try {
      Files.deleteIfExists(temporary)
      Files.writeString(
        temporary,
        buildString {
          appendLine("phase=${outcome.phase}")
          appendLine("versionCode=${outcome.versionCode}")
          appendLine("versionName=${outcome.versionName}")
          appendLine("exitCode=${outcome.exitCode ?: ""}")
          appendLine("message64=$encodedMessage")
        },
        Charsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
      )
      runCatching {
        Files.move(
          temporary,
          statusFile,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.getOrElse {
        Files.move(temporary, statusFile, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      runCatching { Files.deleteIfExists(temporary) }
    }
  }

  internal fun helperScript(
    update: AppUpdate,
    installer: Path,
    appExecutable: Path,
    state: Path,
    installerLog: Path,
    processIds: List<Long>,
  ): String {
    val installerValue = psLiteral(installer.toString())
    val appValue = psLiteral(appExecutable.toString())
    val stateValue = psLiteral(state.toString())
    val logValue = psLiteral(installerLog.toString())
    val versionName = psLiteral(update.versionName)
    val expectedHash = psLiteral(update.sha256.uppercase())
    val processIdValues = processIds.joinToString(", ") { "[Int64]$it" }
    return """
      ${'$'}ErrorActionPreference = 'Stop'
      ${'$'}installer = '$installerValue'
      ${'$'}appExecutable = '$appValue'
      ${'$'}statePath = '$stateValue'
      ${'$'}installerLog = '$logValue'
      [Int64[]]${'$'}processIds = @($processIdValues)
      ${'$'}expectedSize = [Int64]${update.size}
      ${'$'}expectedHash = '$expectedHash'
      ${'$'}versionCode = ${update.versionCode}
      ${'$'}versionName = '$versionName'
      ${'$'}installerStream = ${'$'}null
      ${'$'}removeInstaller = ${'$'}false

      function Write-UpdateState([string]${'$'}phase, [Nullable[int]]${'$'}exitCode, [string]${'$'}message) {
        ${'$'}encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(${'$'}message))
        ${'$'}code = if (${'$'}null -eq ${'$'}exitCode) { '' } else { ${'$'}exitCode.Value.ToString() }
        ${'$'}body = "phase=${'$'}phase`nversionCode=${'$'}versionCode`nversionName=${'$'}versionName`nexitCode=${'$'}code`nmessage64=${'$'}encoded`n"
        ${'$'}temporary = "${'$'}statePath.tmp"
        [IO.File]::WriteAllText(${'$'}temporary, ${'$'}body, [Text.UTF8Encoding]::new(${'$'}false))
        Move-Item -LiteralPath ${'$'}temporary -Destination ${'$'}statePath -Force
      }

      function Start-Veilark {
        if (Test-Path -LiteralPath ${'$'}appExecutable -PathType Leaf) {
          Start-Process -FilePath ${'$'}appExecutable -ArgumentList '--update-result' | Out-Null
        }
      }

      function Test-VeilarkRunning {
        foreach (${'$'}processId in ${'$'}processIds) {
          if (Get-Process -Id ${'$'}processId -ErrorAction SilentlyContinue) {
            return ${'$'}true
          }
        }
        return ${'$'}false
      }

      function Assert-InstalledVersion {
        if (-not (Test-Path -LiteralPath ${'$'}appExecutable -PathType Leaf)) {
          throw 'Установщик завершился без установленного Veilark.exe'
        }
        ${'$'}installRoot = Split-Path -LiteralPath ${'$'}appExecutable -Parent
        ${'$'}appConfig = Join-Path ${'$'}installRoot 'app\Veilark.cfg'
        if (-not (Test-Path -LiteralPath ${'$'}appConfig -PathType Leaf)) {
          throw 'Установщик завершился без Veilark.cfg'
        }
        ${'$'}expectedVersionLine = '-Djpackage.app-version=' + ${'$'}versionName
        ${'$'}installedConfig = [IO.File]::ReadAllText(${'$'}appConfig, [Text.Encoding]::UTF8)
        if (${'$'}installedConfig.IndexOf(${'$'}expectedVersionLine, [StringComparison]::Ordinal) -lt 0) {
          throw ('Установщик завершился, но активная версия Veilark не обновилась до ' + ${'$'}versionName)
        }
      }

      try {
        Write-UpdateState 'WaitingForExit' ${'$'}null 'Ожидание завершения Veilark'
        ${'$'}deadline = [DateTime]::UtcNow.AddMinutes(3)
        while (Test-VeilarkRunning) {
          if ([DateTime]::UtcNow -ge ${'$'}deadline) {
            throw 'Veilark не завершился за отведённое время; установка отменена'
          }
          Start-Sleep -Milliseconds 200
        }

        if (-not (Test-Path -LiteralPath ${'$'}installer -PathType Leaf)) {
          throw 'Установщик обновления не найден'
        }
        # Keep a non-shareable write/delete handle until installation finishes.
        # This closes the check-to-execute race: another process can read the
        # installer, but cannot replace or delete the verified bytes.
        ${'$'}installerStream = [IO.File]::Open(
          ${'$'}installer,
          [IO.FileMode]::Open,
          [IO.FileAccess]::Read,
          [IO.FileShare]::Read
        )
        if (${'$'}installerStream.Length -ne ${'$'}expectedSize) {
          ${'$'}removeInstaller = ${'$'}true
          throw 'Размер установщика изменился после загрузки'
        }
        ${'$'}sha = [Security.Cryptography.SHA256]::Create()
        try {
          ${'$'}actualHash = ([BitConverter]::ToString(${'$'}sha.ComputeHash(${'$'}installerStream))).Replace('-', '')
        } finally {
          ${'$'}sha.Dispose()
        }
        if (${'$'}actualHash -cne ${'$'}expectedHash) {
          ${'$'}removeInstaller = ${'$'}true
          throw 'SHA-256 установщика изменился после загрузки'
        }

        Write-UpdateState 'Installing' ${'$'}null 'Выполняется тихое обновление'
        ${'$'}silentArgs = "/quiet /norestart /log `"${'$'}installerLog`""
        ${'$'}silent = Start-Process -FilePath ${'$'}installer -ArgumentList ${'$'}silentArgs -Verb RunAs -Wait -PassThru
        ${'$'}exitCode = [int]${'$'}silent.ExitCode
        if (${'$'}exitCode -in @(0, 1641, 3010)) {
          Assert-InstalledVersion
          Write-UpdateState 'Succeeded' ${'$'}exitCode 'Обновление установлено'
          ${'$'}removeInstaller = ${'$'}true
        } elseif (${'$'}exitCode -in @(1602, 1223)) {
          Write-UpdateState 'Cancelled' ${'$'}exitCode 'Установка отменена пользователем'
        } else {
          Write-UpdateState 'InteractiveFallback' ${'$'}exitCode 'Тихая установка недоступна; открыт обычный установщик'
          ${'$'}fallback = Start-Process -FilePath ${'$'}installer -ArgumentList '/norestart' -Verb RunAs -Wait -PassThru
          ${'$'}fallbackCode = [int]${'$'}fallback.ExitCode
          if (${'$'}fallbackCode -in @(0, 1641, 3010)) {
            Assert-InstalledVersion
            Write-UpdateState 'Succeeded' ${'$'}fallbackCode 'Обновление установлено'
            ${'$'}removeInstaller = ${'$'}true
          } elseif (${'$'}fallbackCode -in @(1602, 1223)) {
            Write-UpdateState 'Cancelled' ${'$'}fallbackCode 'Установка отменена пользователем'
          } else {
            Write-UpdateState 'Failed' ${'$'}fallbackCode 'Установщик завершился с ошибкой'
          }
        }
      } catch {
        ${'$'}nativeCode = ${'$'}null
        if (${'$'}null -ne ${'$'}_.Exception.NativeErrorCode) {
          ${'$'}nativeCode = [int]${'$'}_.Exception.NativeErrorCode
        }
        if (${'$'}nativeCode -in @(1602, 1223)) {
          Write-UpdateState 'Cancelled' ${'$'}nativeCode 'Запрос Windows был отменён пользователем'
        } else {
          Write-UpdateState 'Failed' ${'$'}nativeCode ${'$'}_.Exception.Message
        }
      } finally {
        if (${'$'}null -ne ${'$'}installerStream) {
          ${'$'}installerStream.Dispose()
        }
        if (${'$'}removeInstaller) {
          Remove-Item -LiteralPath ${'$'}installer -Force -ErrorAction SilentlyContinue
        }
        Start-Veilark
      }
    """.trimIndent()
  }

  private fun psLiteral(value: String): String = value.replace("'", "''")

  private companion object {
    const val LAST_STATUS_FILE = "last-install.properties"
    const val MAX_STATUS_BYTES = 64L * 1024L
    const val MAX_STATUS_MESSAGE_LENGTH = 1_000
    const val MAX_WINDOWS_COMMAND_LINE = 32_000
    const val STALE_HELPER_AGE = 24L * 60L * 60L * 1_000L
    val PENDING_SCRIPT = Regex("pending-\\d+-[0-9A-Za-z-]{1,64}\\.ps1")

    fun systemPowerShell(): Path {
      val windows = System.getenv("SystemRoot")?.takeIf(String::isNotBlank)
        ?: error("Не найдена системная папка Windows")
      return Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
    }

    fun launchDetached(command: List<String>, workingDirectory: Path, log: Path) {
      Files.createDirectories(workingDirectory)
      ProcessBuilder(command)
        .directory(workingDirectory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
        .start()
    }

    fun veilarkProcessIds(appExecutable: Path): List<Long> {
      val expected = appExecutable.toAbsolutePath().normalize().toString()
      val current = ProcessHandle.current()
      val handles = buildList {
        add(current)
        var ancestor = current.parent().orElse(null)
        while (ancestor != null) {
          add(ancestor)
          ancestor = ancestor.parent().orElse(null)
        }
      }
      return handles
        .filter { handle ->
          handle.info().command().orElse(null)
            ?.let { command ->
              runCatching { Path.of(command).toAbsolutePath().normalize().toString() }
                .getOrNull()
                ?.equals(expected, ignoreCase = true)
            } == true
        }
        .map(ProcessHandle::pid)
        .distinct()
    }
  }
}

/**
 * Enforces the safe ordering required by the UI: verify -> stop VPN -> spawn
 * wait-for-exit helper. No helper is launched if disconnect fails or times out.
 */
class SeamlessUpdateCoordinator(
  private val installer: WindowsUpdateInstaller = WindowsUpdateInstaller(),
  private val disconnectTimeoutMillis: Long = 30_000L,
) {
  suspend fun prepareAndSchedule(
    update: AppUpdate,
    downloadedInstaller: Path,
    disconnectVpn: suspend () -> Unit,
  ): ScheduledWindowsUpdate {
    // Prepare every executable/path prerequisite before changing network
    // state, re-verify after disconnect, then verify a third time in the child.
    val prepared = installer.prepare(update, downloadedInstaller)
    withTimeout(disconnectTimeoutMillis) { disconnectVpn() }
    return installer.launchPrepared(prepared)
  }
}

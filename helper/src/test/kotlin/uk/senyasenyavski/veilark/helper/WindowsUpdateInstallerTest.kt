package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import uk.senyasenyavski.veilark.update.AppUpdate
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class WindowsUpdateInstallerTest {
  @Test
  fun `verified update schedules hidden quiet jpackage upgrade`() {
    val fixture = Fixture()
    try {
      val scheduled = fixture.installer.scheduleAfterExit(fixture.update, fixture.download)

      assertContains(fixture.launchedCommand, "-WindowStyle")
      assertContains(fixture.launchedCommand, "Hidden")
      assertContains(fixture.launchedCommand, "-EncodedCommand")
      assertFalse(fixture.launchedCommand.contains("-File"))
      val script = fixture.launchedScript()
      assertContains(script, "[IO.FileShare]::Read")
      assertContains(script, "ComputeHash(\$installerStream)")
      assertContains(script, "/quiet /norestart /log")
      assertContains(
        script,
        "Start-Process -FilePath \$installer -ArgumentList \$silentArgs -Verb RunAs -Wait -PassThru",
      )
      assertContains(
        script,
        "Start-Process -FilePath \$installer -ArgumentList '/norestart' -Verb RunAs -Wait -PassThru",
      )
      assertContains(script, "InteractiveFallback")
      assertContains(script, "[Int64[]]\$processIds = @([Int64]${fixture.processIds[0]}, [Int64]${fixture.processIds[1]})")
      assertContains(script, "foreach (\$processId in \$processIds)")
      assertContains(script, "while (Test-VeilarkRunning)")
      assertContains(script, "function Assert-InstalledVersion")
      assertContains(script, "-Djpackage.app-version=")
      assertContains(script, "IndexOf(\$expectedVersionLine, [StringComparison]::Ordinal)")
      assertTrue(
        script.indexOf("Assert-InstalledVersion") <
          script.indexOf("Write-UpdateState 'Succeeded' \$exitCode"),
      )
      assertContains(script, fixture.update.sha256)
      assertEquals(UpdateInstallPhase.Scheduled, fixture.installer.readLastOutcome()?.phase)
      assertEquals(fixture.download.toAbsolutePath(), scheduled.installer)
      assertPowerShellSyntax(script)
    } finally {
      fixture.close()
    }
  }

  @Test
  fun `app and installer paths are escaped as PowerShell literals`() {
    val fixture = Fixture(directoryPrefix = "veilark updater's test")
    try {
      val scheduled = fixture.installer.scheduleAfterExit(fixture.update, fixture.download)
      val script = fixture.launchedScript()

      assertContains(script, "updater''s")
      assertFalse(script.contains("updater's test\\Veilark.exe'\n"))
    } finally {
      fixture.close()
    }
  }

  @Test
  fun `development launcher cannot schedule an installed update`() {
    val fixture = Fixture(appName = "java.exe")
    try {
      assertFailsWith<IllegalArgumentException> {
        fixture.installer.scheduleAfterExit(fixture.update, fixture.download)
      }
      assertTrue(fixture.launchedCommand.isEmpty())
    } finally {
      fixture.close()
    }
  }

  @Test
  fun `launcher failure removes helper and leaves durable error`() {
    val fixture = Fixture(failLaunch = true)
    try {
      assertFailsWith<IllegalStateException> {
        fixture.installer.scheduleAfterExit(fixture.update, fixture.download)
      }

      assertEquals(UpdateInstallPhase.Failed, fixture.installer.readLastOutcome()?.phase)
      Files.list(fixture.updates).use { entries ->
        assertFalse(entries.anyMatch { it.fileName.toString().endsWith(".ps1") })
      }
    } finally {
      fixture.close()
    }
  }

  @Test
  fun `coordinator stops VPN before updater is launched`() = runBlocking {
    val fixture = Fixture()
    try {
      var disconnected = false
      fixture.beforeLaunch = {
        assertTrue(disconnected, "Updater was launched before VPN shutdown completed")
      }
      val coordinator = SeamlessUpdateCoordinator(fixture.installer)

      coordinator.prepareAndSchedule(fixture.update, fixture.download) {
        disconnected = true
      }

      assertTrue(disconnected)
      assertTrue(fixture.launchedCommand.isNotEmpty())
    } finally {
      fixture.close()
    }
  }

  @Test
  fun `disconnect timeout never launches updater`() {
    val fixture = Fixture()
    try {
      val coordinator = SeamlessUpdateCoordinator(fixture.installer, disconnectTimeoutMillis = 10)

      assertFailsWith<Exception> {
        runBlocking {
          coordinator.prepareAndSchedule(fixture.update, fixture.download) {
            delay(1_000)
          }
        }
      }
      assertTrue(fixture.launchedCommand.isEmpty())
    } finally {
      fixture.close()
    }
  }

  @Test
  fun `tampered installer is deleted before VPN is stopped`() {
    val fixture = Fixture()
    try {
      Files.writeString(fixture.download, "tampered")
      var disconnectCalled = false

      assertFailsWith<IllegalArgumentException> {
        runBlocking {
          SeamlessUpdateCoordinator(fixture.installer).prepareAndSchedule(
            fixture.update,
            fixture.download,
          ) {
            disconnectCalled = true
          }
        }
      }

      assertFalse(disconnectCalled)
      assertFalse(Files.exists(fixture.download))
      assertTrue(fixture.launchedCommand.isEmpty())
    } finally {
      fixture.close()
    }
  }

  private class Fixture(
    directoryPrefix: String = "veilark-update-test",
    appName: String = "Veilark.exe",
    private val failLaunch: Boolean = false,
  ) : AutoCloseable {
    val root: Path = Files.createTempDirectory(directoryPrefix)
    val updates: Path = root.resolve("updates")
    val download: Path = root.resolve("Veilark-9.9.9.exe")
    val app: Path = root.resolve(appName)
    val powershell: Path = root.resolve("powershell.exe")
    val processIds = listOf(4242L, 4243L)
    var launchedCommand: List<String> = emptyList()
    var beforeLaunch: () -> Unit = {}
    val update: AppUpdate
    val installer: WindowsUpdateInstaller

    init {
      val payload = "verified jpackage installer".toByteArray()
      Files.write(download, payload)
      Files.writeString(app, "app")
      Files.writeString(powershell, "powershell")
      update = AppUpdate(
        versionCode = 999,
        versionName = "9.9.9",
        installerUrl = "https://updates.example.test/Veilark-9.9.9.exe",
        sha256 = sha256(payload),
        size = payload.size.toLong(),
        notes = "test",
      )
      installer = WindowsUpdateInstaller(
        updateDirectory = updates,
        processIdsProvider = { processIds },
        appExecutableProvider = { app },
        powershellProvider = { powershell },
        detachedLauncher = { command, _, _ ->
          beforeLaunch()
          if (failLaunch) error("launch failed")
          launchedCommand = command
        },
      )
    }

    override fun close() {
      root.deleteRecursively()
    }

    fun launchedScript(): String {
      val encoded = launchedCommand.last()
      return Base64.getDecoder().decode(encoded).toString(Charsets.UTF_16LE)
    }

    private fun sha256(value: ByteArray): String =
      MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02X".format(it) }
  }

  private fun assertPowerShellSyntax(content: String) {
    val systemRoot = System.getenv("SystemRoot") ?: return
    val powershell = Path.of(
      systemRoot,
      "System32",
      "WindowsPowerShell",
      "v1.0",
      "powershell.exe",
    )
    if (!Files.isRegularFile(powershell)) return
    val script = Files.createTempFile("veilark-update-parser", ".ps1")
    Files.write(
      script,
      byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
        content.toByteArray(Charsets.UTF_8),
    )
    val literal = script.toString().replace("'", "''")
    val command = """
      ${'$'}tokens = ${'$'}null
      ${'$'}errors = ${'$'}null
      [Management.Automation.Language.Parser]::ParseFile('$literal', [ref]${'$'}tokens, [ref]${'$'}errors) | Out-Null
      if (${'$'}errors.Count -gt 0) {
        ${'$'}errors | ForEach-Object { [Console]::Error.WriteLine(${'$'}_.Message) }
        exit 1
      }
    """.trimIndent()
    try {
      val process = ProcessBuilder(
        powershell.toString(),
        "-NoLogo",
        "-NoProfile",
        "-NonInteractive",
        "-Command",
        command,
      ).redirectErrorStream(true).start()
      assertTrue(process.waitFor(15, TimeUnit.SECONDS), "PowerShell parser timed out")
      val output = process.inputStream.bufferedReader().use { it.readText() }
      assertEquals(0, process.exitValue(), output)
    } finally {
      Files.deleteIfExists(script)
    }
  }
}

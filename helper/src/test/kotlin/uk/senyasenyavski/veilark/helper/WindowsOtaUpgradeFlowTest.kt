package uk.senyasenyavski.veilark.helper

import org.json.JSONObject
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateClient
import java.net.URI
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class WindowsOtaUpgradeFlowTest {
  @Test
  fun `legacy 03x detects verifies downloads and schedules 0312 installer`() {
    val root = Files.createTempDirectory("veilark-ota-upgrade-flow")
    try {
      val updates = root.resolve("updates")
      val app = root.resolve("Veilark.exe")
      val powershell = root.resolve("powershell.exe")
      Files.writeString(app, "installed 0.3.x launcher")
      Files.writeString(powershell, "system powershell seam")
      val payload = "0.3.12 jpackage installer".toByteArray()
      val update = AppUpdate(
        versionCode = 312,
        versionName = "0.3.12",
        installerUrl = "https://updates.example.test/Veilark-0.3.12.exe",
        sha256 = sha256(payload),
        size = payload.size.toLong(),
        notes = "Signed 0.3.12 notes",
      )
      val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
      val client = UpdateClient(
        manifestUri = URI("https://updates.example.test/manifest.json"),
        publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
        currentVersionCode = 304,
        allowedHost = "updates.example.test",
        allowedPort = 443,
      )
      fun sign(value: String): String = Signature.getInstance("Ed25519").run {
        initSign(keys.private)
        update(value.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
      }
      val manifest = JSONObject()
        .put("versionCode", update.versionCode)
        .put("versionName", update.versionName)
        .put("installerUrl", update.installerUrl)
        .put("sha256", update.sha256)
        .put("size", update.size)
        .put("notes", update.notes)
        .put("signature", sign(client.canonicalPayload(update)))
        .put("notesSignature", sign(client.canonicalPayloadWithNotes(update)))
        .toString()

      val detected = assertNotNull(client.parseAvailableUpdate(manifest))
      val partial = updates.resolve(".Veilark-${detected.versionCode}.download")
      Files.createDirectories(updates)
      Files.write(partial, payload)
      val downloaded = client.download(detected, updates)
      assertFalse(Files.exists(partial))
      assertEquals(payload.toList(), Files.readAllBytes(downloaded).toList())

      var launchedCommand: List<String> = emptyList()
      val installer = WindowsUpdateInstaller(
        updateClient = client,
        updateDirectory = updates,
        processIdsProvider = { listOf(4242L) },
        appExecutableProvider = { app },
        powershellProvider = { powershell },
        detachedLauncher = { command, _, _ -> launchedCommand = command },
      )
      val scheduled = installer.scheduleAfterExit(detected, downloaded)

      assertEquals(UpdateInstallPhase.Scheduled, installer.readLastOutcome()?.phase)
      assertEquals(downloaded.toAbsolutePath(), scheduled.installer)
      assertContains(launchedCommand, "-EncodedCommand")
      assertTrue(launchedCommand.isNotEmpty())
    } finally {
      root.deleteRecursively()
    }
  }

  private fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value)
      .joinToString("") { "%02X".format(it) }
}

package uk.senyasenyavski.veilark.update

import org.json.JSONObject
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CancellationException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class UpdateClientTest {
  @Test
  fun `signed manifest is accepted and tampering is rejected`() {
    val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val client = UpdateClient(
      manifestUri = URI("https://updates.example.test/manifest.json"),
      publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
      currentVersionCode = 1,
      allowedHost = "updates.example.test",
      allowedPort = 443,
    )
    val update = AppUpdate(
      versionCode = 2,
      versionName = "0.2.0",
      installerUrl = "https://updates.example.test/Veilark.exe",
      sha256 = "A".repeat(64),
      size = 42,
      notes = "Stable",
    )
    val signer = Signature.getInstance("Ed25519").apply {
      initSign(keys.private)
      update(client.canonicalPayload(update).toByteArray())
    }
    val manifest = JSONObject()
      .put("versionCode", update.versionCode)
      .put("versionName", update.versionName)
      .put("installerUrl", update.installerUrl)
      .put("sha256", update.sha256)
      .put("size", update.size)
      .put("notes", update.notes)
      .put("signature", Base64.getEncoder().encodeToString(signer.sign()))
      .toString()

    assertEquals(update, client.parseAndVerify(manifest))
    assertFailsWith<IllegalArgumentException> {
      client.parseAndVerify(JSONObject(manifest).put("size", 43).toString())
    }
  }

  @Test
  fun `range validation is strict`() {
    assertTrue(UpdateClient.contentRangeMatches("bytes 10-99/100", 10, 100))
    assertEquals(false, UpdateClient.contentRangeMatches("bytes 0-99/100", 10, 100))
  }

  @Test
  fun `downloaded installer is revalidated and tampering is removed`() {
    val directory = Files.createTempDirectory("veilark-verify-update")
    val installer = directory.resolve("Veilark-9.9.9.exe")
    val valid = "signed installer bytes".toByteArray()
    val update = updateFor(valid)
    val client = UpdateClient()
    try {
      Files.write(installer, valid)
      client.verifyDownloadedInstaller(installer, update)

      Files.write(installer, ByteArray(valid.size) { 0x2A })
      assertFailsWith<IllegalArgumentException> {
        client.verifyDownloadedInstaller(installer, update)
      }
      assertFalse(Files.exists(installer))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `explicit discard and stale cleanup only touch managed update files`() {
    val directory = Files.createTempDirectory("veilark-clean-updates")
    val update = updateFor("installer".toByteArray())
    val partial = directory.resolve(".Veilark-${update.versionCode}.download")
    val final = directory.resolve("Veilark-${update.versionName}.exe")
    val unrelated = directory.resolve("keep.txt")
    try {
      Files.writeString(partial, "partial")
      Files.writeString(final, "installer")
      Files.writeString(unrelated, "keep")

      client().discardDownload(update, directory)
      assertFalse(Files.exists(partial))
      assertFalse(Files.exists(final))

      val stale = directory.resolve("Veilark-1.0.0.exe")
      Files.writeString(stale, "old")
      Files.setLastModifiedTime(stale, FileTime.fromMillis(1))
      assertEquals(1, client().cleanupDownloads(directory, FileTime.fromMillis(2)))
      assertTrue(Files.exists(unrelated))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `cancelled download stops before opening the network request`() {
    val directory = Files.createTempDirectory("veilark-cancel-update")
    val cancellation = UpdateDownloadCancellation().apply { cancel() }
    try {
      assertFailsWith<CancellationException> {
        client().download(
          updateFor("installer".toByteArray()),
          directory,
          cancellation,
        )
      }
      assertTrue(Files.list(directory).use { it.findAny().isEmpty })
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `published manifest validates when live test is enabled`() {
    if (System.getenv("VEILARK_LIVE_OTA_TEST") != "1") return
    val update = UpdateClient(currentVersionCode = 0).check()
    assertNotNull(update)
    assertTrue(update.versionCode in 1..UpdateClient.CURRENT_VERSION_CODE)
  }

  private fun client() = UpdateClient()

  private fun updateFor(payload: ByteArray) = AppUpdate(
    versionCode = 999,
    versionName = "9.9.9",
    installerUrl = "https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-9.9.9.exe",
    sha256 = MessageDigest.getInstance("SHA-256")
      .digest(payload)
      .joinToString("") { "%02X".format(it) },
    size = payload.size.toLong(),
    notes = "test",
  )
}

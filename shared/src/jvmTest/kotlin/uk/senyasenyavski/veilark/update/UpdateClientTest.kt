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
import kotlin.test.assertNull
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
  fun `312 manifest keeps legacy compatibility and signs release notes`() {
    val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val client = UpdateClient(
      manifestUri = URI("https://updates.example.test/manifest.json"),
      publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
      currentVersionCode = 311,
      allowedHost = "updates.example.test",
      allowedPort = 443,
    )
    val update = AppUpdate(
      versionCode = 312,
      versionName = "0.3.12",
      installerUrl = "https://updates.example.test/Veilark-0.3.12.exe",
      sha256 = "B".repeat(64),
      size = 84,
      notes = "Signed release notes",
    )
    val manifest = manifest(client, update, keys, signNotes = true)

    assertEquals(update, client.parseAvailableUpdate(manifest))
    assertNull(UpdateClient(
      manifestUri = URI("https://updates.example.test/manifest.json"),
      publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
      currentVersionCode = 312,
      allowedHost = "updates.example.test",
      allowedPort = 443,
    ).parseAvailableUpdate(manifest))
    assertFailsWith<IllegalArgumentException> {
      client.parseAndVerify(JSONObject(manifest).put("notes", "tampered").toString())
    }
    assertFailsWith<IllegalArgumentException> {
      client.parseAndVerify(
        JSONObject(manifest).apply { remove("notesSignature") }.toString(),
      )
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
  fun `complete resumable download is promoted without opening a request`() {
    val payload = "complete update".toByteArray()
    val update = updateFor(payload)
    val directory = Files.createTempDirectory("veilark-update-complete-partial")
    try {
      val partial = directory.resolve(".Veilark-${update.versionCode}.download")
      Files.write(partial, payload)

      val installer = client().download(update, directory)

      assertTrue(Files.isRegularFile(installer))
      assertFalse(Files.exists(partial))
      assertEquals(payload.size.toLong(), Files.size(installer))
    } finally {
      directory.toFile().deleteRecursively()
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
    val published = assertNotNull(UpdateClient(currentVersionCode = 0).check())
    assertTrue(published.versionCode in 1..UpdateClient.CURRENT_VERSION_CODE)
    listOf(304, 305, 306, 307, 308, 310, 311)
      .filter { it < published.versionCode }
      .forEach { oldVersion ->
        assertEquals(
          published.versionCode,
          UpdateClient(currentVersionCode = oldVersion).check()?.versionCode,
          "Published update was not selected for $oldVersion",
        )
      }
    assertNull(UpdateClient(currentVersionCode = published.versionCode).check())
  }

  @Test
  fun `published installer downloads and verifies when live test is enabled`() {
    if (System.getenv("VEILARK_LIVE_OTA_DOWNLOAD_TEST") != "1") return
    val client = UpdateClient(currentVersionCode = 0)
    val update = assertNotNull(client.check())
    val directory = Files.createTempDirectory("veilark-live-ota-download")
    try {
      val installer = client.download(update, directory)
      assertEquals(update.size, Files.size(installer))
      client.verifyDownloadedInstaller(installer, update)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `notes limit is measured in UTF-16 units as the publisher enforces`() {
    val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val client = UpdateClient(
      manifestUri = URI("https://updates.example.test/manifest.json"),
      publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
      currentVersionCode = 321,
      allowedHost = "updates.example.test",
      allowedPort = 443,
    )
    fun update(notes: String) = AppUpdate(
      versionCode = 322,
      versionName = "0.3.22",
      installerUrl = "https://updates.example.test/Veilark-0.3.22.exe",
      sha256 = "C".repeat(64),
      size = 84,
      notes = notes,
    )
    val rocket = "\uD83D\uDE80"
    // 2 000 astral code points are exactly 4 000 UTF-16 units: accepted intact.
    val atLimit = update(rocket.repeat(2_000))
    assertEquals(atLimit, client.parseAndVerify(manifest(client, atLimit, keys, signNotes = true)))
    // 2 001 code points (4 002 units) would pass a code-point check in Python,
    // but the client truncates before verifying, so the signature must fail.
    // scripts/publish_ota.py rejects such notes before signing.
    val overLimit = update(rocket.repeat(2_001))
    assertFailsWith<IllegalArgumentException> {
      client.parseAndVerify(manifest(client, overLimit, keys, signNotes = true))
    }
  }

  @Test
  fun `failures carry stable codes for the localized UI`() {
    val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val client = UpdateClient(
      manifestUri = URI("https://updates.example.test/manifest.json"),
      publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded),
      currentVersionCode = 321,
      allowedHost = "updates.example.test",
      allowedPort = 443,
    )
    val update = AppUpdate(322, "0.3.22", "https://updates.example.test/Veilark-0.3.22.exe", "D".repeat(64), 84, "n")
    val signed = JSONObject(manifest(client, update, keys, signNotes = true))
    val tampered = assertFailsWith<UpdateException> {
      client.parseAndVerify(JSONObject(signed.toString()).put("notes", "changed").toString())
    }
    assertEquals(UpdateErrorCode.SignatureInvalid, tampered.error.code)
    val foreign = assertFailsWith<UpdateException> {
      client.parseAndVerify(
        JSONObject(signed.toString()).put("installerUrl", "https://evil.example.test/Veilark.exe").toString(),
      )
    }
    assertEquals(UpdateErrorCode.UntrustedAddress, foreign.error.code)
    val malformed = runCatching { client.parseAndVerify("{") }.exceptionOrNull()!!
    assertEquals(UpdateErrorCode.ManifestInvalid, malformed.toUpdateError(UpdateErrorCode.CheckFailed).code)
  }

  private fun manifest(
    client: UpdateClient,
    update: AppUpdate,
    keys: java.security.KeyPair,
    signNotes: Boolean,
  ): String {
    fun sign(payload: String): String = Signature.getInstance("Ed25519").run {
      initSign(keys.private)
      update(payload.toByteArray(Charsets.UTF_8))
      Base64.getEncoder().encodeToString(sign())
    }
    return JSONObject()
      .put("versionCode", update.versionCode)
      .put("versionName", update.versionName)
      .put("installerUrl", update.installerUrl)
      .put("sha256", update.sha256)
      .put("size", update.size)
      .put("notes", update.notes)
      .put("signature", sign(client.canonicalPayload(update)))
      .apply {
        if (signNotes) put("notesSignature", sign(client.canonicalPayloadWithNotes(update)))
      }
      .toString()
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

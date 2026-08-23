package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.VpnEngine

class ProfileStoreTest {
  @Test
  fun `profiles survive an encrypted round trip`() {
    val directory = Files.createTempDirectory("veilark-profile-store")
    val path = directory.resolve("profiles.dat")
    val store = ProfileStore(path, XorProtector) {}
    val base = StoredProfiles(
      selectedEngine = VpnEngine.TrustTunnel,
      routing = RoutingSettings(
        mode = RoutingMode.Manual,
        directEntries = "example.ru",
        vpnEntries = "youtube.com",
        tlsFragment = true,
      ),
      selectedNodeTags = mapOf(
        VpnEngine.SingBox to "node",
        VpnEngine.TrustTunnel to "node",
      ),
    )
    val expected = SubscriptionCatalog.put(
      base,
      SubscriptionRecord.user(
        listOf(profile(VpnEngine.SingBox), profile(VpnEngine.TrustTunnel)),
      ),
    )

    try {
      store.save(expected)

      assertEquals(expected, store.load())
      assertFalse(path.readBytes().toString(Charsets.UTF_8).contains("private-secret"))
    } finally {
      path.deleteIfExists()
      directory.deleteIfExists()
    }
  }

  @Test
  fun `two subscriptions for the same engine survive a round trip`() {
    val directory = Files.createTempDirectory("veilark-multiple-subscriptions")
    val path = directory.resolve("profiles.dat")
    val store = ProfileStore(path, XorProtector) {}
    val first = SubscriptionRecord.user(
      listOf(profile(VpnEngine.SingBox).copy(id = "first", sourceUrl = "https://example.test/first")),
    )
    val second = SubscriptionRecord.user(
      listOf(profile(VpnEngine.SingBox).copy(id = "second", sourceUrl = "https://example.test/second")),
    )
    val expected = SubscriptionCatalog.put(
      SubscriptionCatalog.put(StoredProfiles(), first),
      second,
    )

    try {
      store.save(expected)

      val restored = store.load()
      assertEquals(expected, restored)
      assertEquals(2, restored.subscriptions.size)
      assertEquals(second.id, restored.selectedSubscriptionIds[VpnEngine.SingBox])
    } finally {
      path.deleteIfExists()
      directory.deleteIfExists()
    }
  }

  @Test
  fun `version five flat profiles migrate to subscription records`() {
    val directory = Files.createTempDirectory("veilark-profile-migration")
    val path = directory.resolve("profiles.dat")
    val legacyProfile = profile(VpnEngine.TrustTunnel)
    try {
      Files.write(path, XorProtector.protect(encodeVersionFive(legacyProfile)))

      val restored = ProfileStore(path, XorProtector) {}.load()

      assertEquals(1, restored.subscriptions.size)
      assertEquals(SubscriptionOrigin.User, restored.subscriptions.single().origin)
      assertEquals(legacyProfile, restored.profiles.single())
      assertEquals(
        restored.subscriptions.single().id,
        restored.selectedSubscriptionIds[VpnEngine.TrustTunnel],
      )
    } finally {
      path.deleteIfExists()
      directory.deleteIfExists()
    }
  }

  @Test
  fun `version five dual core source migrates as one subscription`() {
    val directory = Files.createTempDirectory("veilark-dual-core-migration")
    val path = directory.resolve("profiles.dat")
    val backup = directory.resolve("profiles.dat.bak")
    val singBox = profile(VpnEngine.SingBox)
    val trustTunnel = profile(VpnEngine.TrustTunnel)
    try {
      Files.write(
        path,
        XorProtector.protect(encodeVersionFive(singBox, trustTunnel)),
      )

      val store = ProfileStore(path, XorProtector) {}
      val restored = assertIs<ProfileLoadResult.Loaded>(store.loadResult()).stored

      assertEquals(1, restored.subscriptions.size)
      assertEquals(
        setOf(VpnEngine.SingBox, VpnEngine.TrustTunnel),
        restored.subscriptions.single().profiles.map(Profile::engine).toSet(),
      )
      assertEquals(2, restored.profiles.size)
      assertEquals(
        restored.subscriptions.single().id,
        restored.selectedSubscriptionIds[VpnEngine.SingBox],
      )
      assertEquals(
        restored.subscriptions.single().id,
        restored.selectedSubscriptionIds[VpnEngine.TrustTunnel],
      )

      store.save(restored)
      assertEquals(restored, store.load())
      assertTrue(Files.isRegularFile(backup))
    } finally {
      backup.deleteIfExists()
      path.deleteIfExists()
      directory.deleteIfExists()
    }
  }

  @Test
  fun `valid backup recovers a corrupt primary without being overwritten`() {
    val directory = Files.createTempDirectory("veilark-profile-backup")
    val path = directory.resolve("profiles.dat")
    val backup = directory.resolve("profiles.dat.bak")
    val store = ProfileStore(path, XorProtector) {}
    val first = SubscriptionCatalog.put(
      StoredProfiles(),
      SubscriptionRecord.user(listOf(profile(VpnEngine.SingBox))),
    )
    val second = SubscriptionCatalog.put(
      first,
      SubscriptionRecord.user(
        listOf(
          profile(VpnEngine.TrustTunnel).copy(
            sourceUrl = "https://subscription.example.test/second",
          ),
        ),
      ),
    )
    try {
      store.save(first)
      store.save(second)
      assertTrue(Files.isRegularFile(backup))
      Files.write(path, byteArrayOf(1, 2, 3))

      val restored = assertIs<ProfileLoadResult.Loaded>(store.loadResult()).stored

      assertEquals(first, restored)
      assertTrue(Files.size(backup) > 3)
    } finally {
      backup.deleteIfExists()
      path.deleteIfExists()
      directory.deleteIfExists()
    }
  }

  @Test
  fun `DPAPI protects and restores bytes for the current Windows user`() {
    if (!Platform.isWindows()) return
    val protector = DpapiSecretProtector()
    val plain = "veilark-dpapi-check".toByteArray()

    val encrypted = protector.protect(plain)

    assertFalse(encrypted.contentEquals(plain))
    assertContentEquals(plain, protector.unprotect(encrypted))
  }

  @Test
  fun `corrupt storage fails closed`() {
    val directory = Files.createTempDirectory("veilark-profile-store")
    val path = directory.resolve("profiles.dat")
    try {
      Files.write(path, byteArrayOf(1, 2, 3))

      val store = ProfileStore(path, XorProtector) {}
      assertIs<ProfileLoadResult.Failed>(store.loadResult())
      assertEquals(StoredProfiles(), store.load())
      assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(path))
    } finally {
      path.deleteIfExists()
      directory.deleteIfExists()
    }
  }

  private fun profile(engine: VpnEngine) = Profile(
    id = engine.name,
    name = engine.name,
    engine = engine,
    config = "private-secret-${engine.name}",
    nodes = listOf(Node("node", "Node", engine.name)),
    sourceLabel = "test",
    endpointConfigs = if (engine == VpnEngine.TrustTunnel) {
      mapOf("node" to "private-secret-endpoint")
    } else {
      emptyMap()
    },
    sourceUrl = "https://subscription.example.test/id",
  )

  private fun encodeVersionFive(vararg profiles: Profile): ByteArray =
    ByteArrayOutputStream().use { bytes ->
      DataOutputStream(bytes).use { output ->
        output.writeInt(0x56454C4B)
        output.writeInt(5)
        output.writeUTF(profiles.first().engine.name)
        output.writeUTF(RoutingMode.All.name)
        output.writeLegacyString("")
        output.writeLegacyString("")
        output.writeBoolean(false)
        output.writeInt(profiles.size)
        profiles.forEach { profile ->
          output.writeUTF(profile.engine.name)
          output.writeLegacyString(profile.nodes.single().tag)
        }
        output.writeInt(profiles.size)
        profiles.forEach { profile ->
          output.writeLegacyString(profile.id)
          output.writeLegacyString(profile.name)
          output.writeUTF(profile.engine.name)
          output.writeLegacyString(profile.config)
          output.writeLegacyString(profile.sourceLabel)
          output.writeInt(profile.nodes.size)
          profile.nodes.forEach { node ->
            output.writeLegacyString(node.tag)
            output.writeLegacyString(node.name)
            output.writeLegacyString(node.protocol)
          }
          output.writeInt(profile.endpointConfigs.size)
          profile.endpointConfigs.forEach { (tag, config) ->
            output.writeLegacyString(tag)
            output.writeLegacyString(config)
          }
          output.writeBoolean(profile.sourceUrl != null)
          profile.sourceUrl?.let { output.writeLegacyString(it) }
        }
      }
      bytes.toByteArray()
    }

  private fun DataOutputStream.writeLegacyString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeInt(bytes.size)
    write(bytes)
  }

  private object XorProtector : SecretProtector {
    override fun protect(value: ByteArray): ByteArray =
      value.map { (it.toInt() xor MASK).toByte() }.toByteArray()

    override fun unprotect(value: ByteArray): ByteArray = protect(value)

    private const val MASK = 0x5A
  }
}

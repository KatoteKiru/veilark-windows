package uk.senyasenyavski.veilark.helper

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import uk.senyasenyavski.veilark.model.*

class FairwayRollbackStoreTest {
  private val protector = object : SecretProtector {
    override fun protect(value: ByteArray) = value.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    override fun unprotect(value: ByteArray) = protect(value)
  }
  private fun legacy() = Profile("legacy", "Legacy", VpnEngine.SingBox, "{}", emptyList(), "test")
  private fun core() = Profile("core", "Fairway", VpnEngine.VeilarkCore, "synthetic-Core-private-value",
    listOf(Node("core", "Fairway", "Fairway")), "test")

  @Test fun `first Core save freezes exact legacy ciphertext and later saves never replace it`() = directory { path ->
    val store = ProfileStore(path, protector) {}
    val original = SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(legacy()))
    store.save(original)
    val before = Files.readAllBytes(path)
    assertFalse(Files.exists(store.legacyRollbackPath))
    val added = SubscriptionCatalog.fromImportedProfiles(original, listOf(core()))
    store.save(added)
    assertContentEquals(before, Files.readAllBytes(store.legacyRollbackPath))
    store.save(added.copy(routing = RoutingSettings(RoutingMode.RussiaDirect)))
    assertContentEquals(before, Files.readAllBytes(store.legacyRollbackPath))
    assertEquals(2, store.load().subscriptions.size)
  }

  @Test fun `fresh mixed source gets a legacy projection without Core secrets or engine tags`() = directory { path ->
    val record = SubscriptionRecord.user(listOf(legacy(), core()))
    val store = ProfileStore(path, protector) {}
    store.save(StoredProfiles(subscriptions = listOf(record)))
    val rollback = ProfileStore(store.legacyRollbackPath, protector) {}.load()
    assertEquals(listOf(VpnEngine.SingBox), rollback.subscriptions.single().profiles.map { it.engine })
    val clear = protector.unprotect(Files.readAllBytes(store.legacyRollbackPath)).toString(Charsets.UTF_8)
    assertFalse(clear.contains("synthetic-Core-private-value"))
    assertFalse(clear.contains("VeilarkCore"))
    assertEquals(2, store.load().subscriptions.single().profiles.size)
  }

  @Test fun `corrupt checkpoint refuses Core save and preserves both existing files`() = directory { path ->
    val store = ProfileStore(path, protector) {}
    val original = SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(legacy()))
    store.save(original)
    val before = Files.readAllBytes(path)
    Files.write(store.legacyRollbackPath, byteArrayOf(1, 2, 3))
    assertFails { store.save(SubscriptionCatalog.fromImportedProfiles(original, listOf(core()))) }
    assertContentEquals(before, Files.readAllBytes(path))
    assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(store.legacyRollbackPath))
  }

  @Test fun `raw Core selection cannot hide behind catalog normalization in rollback validation`() = directory { path ->
    val store = ProfileStore(path, protector) {}
    val original = SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(legacy()))
    store.save(original)
    val before = Files.readAllBytes(path)
    Files.write(store.legacyRollbackPath, protector.protect(rawCoreSelection()))
    assertFailsWith<IllegalArgumentException> {
      store.save(SubscriptionCatalog.fromImportedProfiles(original, listOf(core())))
    }
    assertContentEquals(before, Files.readAllBytes(path))
  }

  @Test fun `raw Core selection in primary is projected instead of copied into legacy checkpoint`() = directory { path ->
    val store = ProfileStore(path, protector) {}
    Files.write(path, protector.protect(rawCoreSelection()))
    store.save(SubscriptionCatalog.fromImportedProfiles(StoredProfiles(), listOf(core())))
    val clear = protector.unprotect(Files.readAllBytes(store.legacyRollbackPath)).toString(Charsets.UTF_8)
    assertFalse(clear.contains("VeilarkCore"))
    assertTrue(ProfileStore(store.legacyRollbackPath, protector) {}.loadResult() is ProfileLoadResult.Loaded)
    assertEquals(VpnEngine.VeilarkCore, store.load().subscriptions.single().profiles.single().engine)
  }

  private fun rawCoreSelection(): ByteArray = ByteArrayOutputStream().use { bytes ->
      DataOutputStream(bytes).use { output ->
        output.writeInt(0x56454c4b); output.writeInt(6)
        output.writeUTF("VeilarkCore"); output.writeUTF("All")
        output.writeInt(0); output.writeInt(0); output.writeBoolean(false)
        output.writeInt(0); output.writeInt(0); output.writeInt(0)
      }
      bytes.toByteArray()
  }

  private fun directory(test: (Path) -> Unit) {
    val directory = Files.createTempDirectory("veilark-legacy-rollback-")
    try { test(directory.resolve("profiles.dat")) }
    finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } } }
  }
}

package uk.senyasenyavski.veilark.helper

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlProtocolRegistrationTest {
  private class MemoryStore(override val available: Boolean = true) : UrlProtocolRegistration.RegistryStore {
    val values = linkedMapOf<Pair<String, String>, String>()
    var writes = 0

    override fun read(key: String, name: String): String? = values[key to name]

    override fun write(key: String, name: String, value: String) {
      writes += 1
      values[key to name] = value
    }
  }

  private val launcher: Path = Path.of("C:\\Program Files\\Veilark\\Veilark.exe")

  @Test
  fun `builds the HKCU protocol handler keys for the packaged launcher`() {
    val values = UrlProtocolRegistration.values(launcher)

    assertEquals(
      listOf(
        UrlProtocolRegistration.RegistryValue("Software\\Classes\\veilark", "", "URL:Veilark Protocol"),
        UrlProtocolRegistration.RegistryValue("Software\\Classes\\veilark", "URL Protocol", ""),
        UrlProtocolRegistration.RegistryValue(
          "Software\\Classes\\veilark\\shell\\open\\command",
          "",
          "\"C:\\Program Files\\Veilark\\Veilark.exe\" \"%1\"",
        ),
      ),
      values,
    )
  }

  @Test
  fun `registration is idempotent and repairs a stale command`() {
    val store = MemoryStore()
    val registration = UrlProtocolRegistration(store) { launcher }

    assertEquals(3, registration.ensureRegistered())
    assertEquals(0, registration.ensureRegistered())
    assertEquals(3, store.writes)

    val oldLauncher = Path.of("D:\\Old\\Veilark.exe")
    store.values[UrlProtocolRegistration.COMMAND_KEY to ""] = UrlProtocolRegistration.commandLine(oldLauncher)
    assertEquals(1, registration.ensureRegistered())
    assertEquals(
      UrlProtocolRegistration.commandLine(launcher),
      store.values[UrlProtocolRegistration.COMMAND_KEY to ""],
    )
  }

  @Test
  fun `development runs and non-windows hosts are skipped`() {
    val javaStore = MemoryStore()
    assertNull(UrlProtocolRegistration(javaStore) { Path.of("C:\\jdk\\bin\\java.exe") }.ensureRegistered())
    assertTrue(javaStore.values.isEmpty())

    val unavailable = MemoryStore(available = false)
    assertNull(UrlProtocolRegistration(unavailable) { launcher }.ensureRegistered())
    assertTrue(unavailable.values.isEmpty())

    assertNull(UrlProtocolRegistration(MemoryStore()) { null }.ensureRegistered())
    assertFalse(UrlProtocolRegistration.isPackagedLauncher(Path.of("veilark-helper.exe")))
    assertTrue(UrlProtocolRegistration.isPackagedLauncher(Path.of("c:\\apps\\VEILARK.EXE")))
  }
}
